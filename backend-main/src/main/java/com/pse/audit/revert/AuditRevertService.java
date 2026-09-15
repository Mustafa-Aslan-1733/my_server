package com.pse.audit.revert;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.audit.service.AuditRevertContext;
import com.pse.shared.dto.BasicResponse;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reverses a single administrative field edit.
 *
 * <p>Only field edits can be reversed, and that is not a limitation of this class but of the
 * log: an entry records {@code before}/{@code after} per field, which inverts mechanically,
 * whereas a deletion records only that the row stopped existing. {@code USER_DELETED}
 * anonymises the account and {@code LECTURE_DELETED} takes its comments, answers and ratings
 * with it, and nothing in the log carries the removed data. Those refuse rather than
 * half-apply.
 *
 * <p>Whether an entry can be reverted is decided here and reported on the entry itself, so
 * the panel never has to recompute the window or the staleness rule. Two copies of that rule
 * would drift.
 */
@Service
public class AuditRevertService {

    /**
     * The actions whose {@code changes} map is a pure field diff. Everything else — the
     * creations, the deletions, the session events and the refusals — is not invertible, and
     * is refused with {@link AuditRevertRefusal#ACTION_NOT_REVERTIBLE}.
     */
    private static final Set<AuditAction> REVERTIBLE_ACTIONS = EnumSet.of(
            AuditAction.USER_UPDATED,
            AuditAction.USER_BLOCKED,
            AuditAction.USER_UNBLOCKED,
            AuditAction.USER_WARNING_UPDATED,
            AuditAction.COMMENT_UPDATED,
            AuditAction.ANSWER_UPDATED,
            AuditAction.LECTURE_UPDATED,
            AuditAction.PROFESSOR_UPDATED,
            AuditAction.BUG_REPORT_UPDATED,
            AuditAction.COMMENT_REPORT_STATUS_CHANGED,
            AuditAction.ANSWER_REPORT_STATUS_CHANGED
    );

    /**
     * Change keys that mark a lifecycle event rather than a field. Their presence means the
     * entry is a creation or a removal however its action is classified.
     */
    private static final Set<String> LIFECYCLE_KEYS = Set.of("exists", "anonymized");

    private final AuditLogRepository repository;
    private final Map<AuditTargetType, AuditRevertHandler> handlers;
    private final Duration window;
    private final Clock clock;

    /**
     * Creates AuditRevertService.
     *
     * @param repository the repository
     * @param handlers the handlers
     * @param window the window
     * @param clock the clock
     */
    public AuditRevertService(
            AuditLogRepository repository,
            List<AuditRevertHandler> handlers,
            @Value("${app.audit.revert-window:P7D}") Duration window,
            Clock clock
    ) {
        this.repository = repository;
        this.handlers = handlers.stream().collect(Collectors.toMap(
                AuditRevertHandler::targetType,
                Function.identity()
        ));
        this.window = window;
        this.clock = clock;
    }

    /**
     * Whether one entry can be reverted, and if not, why.
     *
     * @param revertedByAuditId the entry that already reversed this one, or null
     * @param revertible the revertible
     * @param reason the reason
     */
    public record Revertability(
            boolean revertible,
            AuditRevertRefusal reason,
            UUID revertedByAuditId
    ) {
        static final Revertability YES = new Revertability(true, null, null);

        static Revertability no(AuditRevertRefusal reason) {
            return new Revertability(false, reason, null);
        }
    }

    /**
     * Decides revertibility for a whole page in one pass.
     *
     * <p>Batched deliberately. The staleness rule needs the target's current values, and
     * doing that per entry would put a query per row onto the panel's heaviest read; grouped
     * by target type it is a handful of queries for the page however long the page is.
     *
     * @param page the page
     * @return the result
     */
    @Transactional(readOnly = true)
    public Map<UUID, Revertability> describe(List<AuditLog> page) {
        Map<UUID, Revertability> result = new HashMap<>();
        if (page.isEmpty()) {
            return result;
        }

        // Candidates are the entries that could be reverted on their own terms. Only those
        // need their target loaded, so an audit page full of logins costs no extra queries.
        List<AuditLog> candidates = page.stream().filter(AuditRevertService::isFieldDiff).toList();

        Map<UUID, UUID> reverted = candidates.isEmpty()
                ? Map.of()
                : reversalsOf(candidates.stream().map(AuditLog::getId).toList());

        Map<AuditTargetType, Map<UUID, Map<String, Object>>> currentByType =
                loadCurrentValues(candidates);

        Instant cutoff = Instant.now(clock).minus(window);
        for (AuditLog log : page) {
            Map<UUID, Map<String, Object>> current =
                    currentByType.getOrDefault(log.getTargetType(), Map.of());
            // A refusal -- LOGIN_REFUSED, ACCESS_REFUSED -- records something that did not
            // happen and so carries no target id. Asking an immutable empty map for a null
            // key throws, which turned a page holding one refusal into a 500.
            Map<String, Object> currentValues =
                    log.getTargetId() == null ? null : current.get(log.getTargetId());
            result.put(log.getId(), evaluate(log, currentValues, reverted, cutoff));
        }
        return result;
    }

    /**
     * Reverses one entry, or refuses it with a reason the panel can act on.
     *
     * @param principal the principal
     * @param auditId the auditId
     * @return the result
     */
    @Transactional
    public BasicResponse revert(AuthenticatedUser principal, UUID auditId) {
        AuditLog log = repository.findById(auditId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Audit entry not found"));

        AuditRevertHandler handler = isFieldDiff(log) ? handlers.get(log.getTargetType()) : null;
        Map<UUID, Map<String, Object>> current = handler == null
                ? Map.of()
                : handler.currentValues(List.of(log.getTargetId()));
        Map<UUID, UUID> reverted = reversalsOf(List.of(auditId));

        // The same guard describe() carries, and for the same reason: a refusal --
        // LOGIN_REFUSED, ACCESS_REFUSED -- records something that did not happen and so has
        // no target id, and asking an immutable empty map for a null key throws. describe()
        // learned this and revert() did not, though the panel offers the operator both from
        // one list, so clicking revert on a refusal answered 500 instead of the 409 that
        // names the reason.
        Map<String, Object> currentValues =
                log.getTargetId() == null ? null : current.get(log.getTargetId());

        Revertability verdict = evaluate(
                log,
                currentValues,
                reverted,
                Instant.now(clock).minus(window)
        );
        if (!verdict.revertible()) {
            throw refusal(log, verdict.reason());
        }

        // `handler` cannot be null here, and the reason is two methods apart: it is null only
        // when !isFieldDiff(log) or when no handler is registered for the target type, and
        // evaluate() refuses both on its first line -- so the throw above has already run.
        // Static analysis flags this as a possible NPE because it cannot see that coupling;
        // it is written down rather than guarded so that nobody "fixes" it with a null check
        // that would silently swallow a genuinely unrevertible entry instead of refusing it.
        //
        // The inverse goes through the same service method the panel calls, so it inherits
        // every guard that path enforces and writes its own audit event. The context is what
        // links that new event back to this one; the entry being reverted is not touched.
        AuditRevertContext.set(auditId);
        try {
            handler.applyInverse(principal, log.getTargetId(), beforeValues(log));
        } finally {
            AuditRevertContext.clear();
        }
        return new BasicResponse("Reverted " + log.getAction().name(), true);
    }

    private Map<AuditTargetType, Map<UUID, Map<String, Object>>> loadCurrentValues(
            List<AuditLog> candidates
    ) {
        Map<AuditTargetType, List<UUID>> idsByType = new LinkedHashMap<>();
        for (AuditLog log : candidates) {
            idsByType.computeIfAbsent(log.getTargetType(), type -> new ArrayList<>())
                    .add(log.getTargetId());
        }
        Map<AuditTargetType, Map<UUID, Map<String, Object>>> current = new HashMap<>();
        idsByType.forEach((type, ids) -> {
            AuditRevertHandler handler = handlers.get(type);
            if (handler != null) {
                current.put(type, handler.currentValues(ids));
            }
        });
        return current;
    }

    private Revertability evaluate(
            AuditLog log,
            Map<String, Object> current,
            Map<UUID, UUID> reverted,
            Instant cutoff
    ) {
        if (!isFieldDiff(log) || !handlers.containsKey(log.getTargetType())) {
            return Revertability.no(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
        }
        if (reverted.containsKey(log.getId())) {
            return new Revertability(
                    false, AuditRevertRefusal.ALREADY_REVERTED, reverted.get(log.getId()));
        }
        if (log.getCreatedAt().isBefore(cutoff)) {
            return Revertability.no(AuditRevertRefusal.WINDOW_EXPIRED);
        }
        if (current == null) {
            return Revertability.no(AuditRevertRefusal.TARGET_MISSING);
        }
        for (Map.Entry<String, Object> entry : log.getChanges().entrySet()) {
            // A field the handler does not know about cannot be checked or restored, so the
            // entry as a whole is not something this can reverse.
            if (!current.containsKey(entry.getKey())) {
                return Revertability.no(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
            }
            Object recordedAfter = half(entry.getValue(), "after");
            if (!RevertValues.sameValue(recordedAfter, current.get(entry.getKey()))) {
                return Revertability.no(AuditRevertRefusal.VALUE_CHANGED);
            }
        }
        return Revertability.YES;
    }

    private ApiException refusal(AuditLog log, AuditRevertRefusal reason) {
        String message = switch (reason) {
            case ACTION_NOT_REVERTIBLE ->
                    log.getAction().name() + " cannot be reversed";
            case WINDOW_EXPIRED ->
                    "This entry is older than the " + window + " revert window";
            case VALUE_CHANGED ->
                    "The value changed after this entry, so reverting would discard that change";
            case ALREADY_REVERTED -> "This entry has already been reverted";
            case TARGET_MISSING -> "The record this entry is about no longer exists";
        };
        // 409 rather than 400: the request is well formed, the state of the world is what
        // makes it inapplicable.
        return new ApiException(HttpStatus.CONFLICT, message, reason.name());
    }

    /**
     * True when the entry records per-field before/after values for a target this can
     * address — the shape a reversal needs. False for creations, deletions, session events
     * and refusals.
     */
    private static boolean isFieldDiff(AuditLog log) {
        if (!REVERTIBLE_ACTIONS.contains(log.getAction())
                || log.getTargetType() == null
                || log.getTargetId() == null
                || log.getChanges().isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : log.getChanges().entrySet()) {
            if (LIFECYCLE_KEYS.contains(entry.getKey())) {
                return false;
            }
            if (!(entry.getValue() instanceof Map<?, ?> value)
                    || !value.containsKey("before")
                    || !value.containsKey("after")) {
                return false;
            }
        }
        return true;
    }

    /** The {@code before} half of every recorded field, which is what gets applied back. */
    private static Map<String, Object> beforeValues(AuditLog log) {
        Map<String, Object> before = new LinkedHashMap<>();
        log.getChanges().forEach((field, value) -> before.put(field, half(value, "before")));
        return before;
    }

    private static Object half(Object change, String key) {
        return change instanceof Map<?, ?> value ? value.get(key) : null;
    }

    /** Reverted entry id to the id of the entry that reversed it. */
    private Map<UUID, UUID> reversalsOf(Collection<UUID> auditIds) {
        Map<UUID, UUID> reversals = new HashMap<>();
        for (Object[] row : repository.findReversalsOf(auditIds)) {
            reversals.put((UUID) row[0], (UUID) row[1]);
        }
        return reversals;
    }
}

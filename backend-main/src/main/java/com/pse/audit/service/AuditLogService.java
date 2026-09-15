package com.pse.audit.service;

import com.pse.shared.util.Uuids;
import com.pse.audit.dto.AuditActorMetaResponse;
import com.pse.audit.dto.AuditActorResponse;
import com.pse.audit.dto.AuditLogPageResponse;
import com.pse.audit.dto.AuditLogResponse;
import com.pse.audit.dto.AuditMetaResponse;
import com.pse.audit.dto.AuditTargetResponse;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActionScope;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.audit.revert.AuditRevertService;
import com.pse.shared.error.ApiException;
import com.pse.shared.util.KeysetCursorCodec;
import com.pse.shared.util.SqlLike;
import com.pse.shared.util.UtcDates;
import jakarta.persistence.criteria.Predicate;
import com.pse.shared.page.KeysetPage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Provides AuditLogService.
 */
@Service
public class AuditLogService {

    /** What {@code GET /audit-logs} returns when the caller does not ask for a size. */
    private static final int DEFAULT_LIMIT = 50;

    /**
     * Upper bound on the actor filter list. It feeds a dropdown, and every account that
     * ever signed in is an actor in the activity log; an unbounded list would be neither
     * usable nor cheap. Beyond this the {@code q} search is the way to find an actor.
     */
    private static final int ACTOR_LIMIT = 200;

    private final AuditLogRepository repository;
    private final AuditCursorCodec cursorCodec;
    private final AuditRevertService revertService;

    /**
     * Creates AuditLogService.
     *
     * @param repository the repository
     * @param cursorCodec the cursorCodec
     * @param revertService the revertService
     */
    public AuditLogService(
            AuditLogRepository repository,
            AuditCursorCodec cursorCodec,
            AuditRevertService revertService
    ) {
        this.repository = repository;
        this.cursorCodec = cursorCodec;
        this.revertService = revertService;
    }

    /**
     * Returns getLogs.
     *
     * @param scope the scope
     * @param rawLimit the rawLimit
     * @param rawCursor the rawCursor
     * @param q the q
     * @param rawActorId the rawActorId
     * @param rawActorType the rawActorType
     * @param rawAction the rawAction
     * @param rawTargetType the rawTargetType
     * @param rawFrom the rawFrom
     * @param rawTo the rawTo
     * @throws ApiException for Invalid audit action
     * @return the result
     */
    @Transactional(readOnly = true)
    public AuditLogPageResponse getLogs(
            AuditActionScope scope,
            String rawLimit,
            String rawCursor,
            String q,
            String rawActorId,
            String rawActorType,
            String rawAction,
            String rawTargetType,
            String rawFrom,
            String rawTo
    ) {
        int limit = KeysetPage.limitOr(rawLimit, DEFAULT_LIMIT);
        UUID actorId = parseUuid(rawActorId, "Invalid actor id");
        AuditActorType actorType =
                parseEnum(rawActorType, AuditActorType.class, "Invalid audit actor type");
        List<AuditAction> scopeActions = AuditAction.of(scope);
        AuditAction action = parseEnum(rawAction, AuditAction.class, "Invalid audit action");
        // An action from the other page is not a valid filter here. Silently returning
        // nothing would look like "no such events" instead of "wrong endpoint".
        if (action != null && action.getScope() != scope) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid audit action");
        }
        AuditTargetType targetType =
                parseEnum(rawTargetType, AuditTargetType.class, "Invalid audit target type");
        Instant from = parseDate(rawFrom);
        Instant to = parseDate(rawTo);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid audit date range");
        }
        KeysetCursorCodec.Cursor cursor =
                rawCursor == null || rawCursor.isBlank() ? null : cursorCodec.decode(rawCursor);
        String search = q == null ? "" : q.trim();
        if (search.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Audit search is too long");
        }

        Specification<AuditLog> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("action").in(scopeActions));
            if (actorId != null) {
                predicates.add(builder.equal(root.get("actorId"), actorId));
            }
            if (actorType != null) {
                predicates.add(builder.equal(root.get("actorType"), actorType));
            }
            if (action != null) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (targetType != null) {
                predicates.add(builder.equal(root.get("targetType"), targetType));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (!search.isEmpty()) {
                String pattern = "%" + SqlLike.escape(search.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("actorName")), pattern, '\\'),
                        builder.like(builder.lower(root.get("actorEmail")), pattern, '\\'),
                        builder.like(builder.lower(root.get("targetLabel")), pattern, '\\')
                ));
            }
            if (cursor != null) {
                predicates.add(KeysetPage.after(
                        builder, root, cursor.createdAt(), cursor.id(), Sort.Direction.DESC));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        List<AuditLog> fetched = repository.findAll(
                specification,
                PageRequest.of(0, limit + 1, KeysetPage.byCreatedAtThenId(Sort.Direction.DESC))
        ).getContent();
        KeysetPage.Slice<AuditLog> slice = KeysetPage.of(fetched, limit,
                last -> cursorCodec.encode(last.getCreatedAt(), last.getId()));
        List<AuditLog> page = slice.items();
        String nextCursor = slice.nextCursor();
        // One pass for the whole page rather than a check per row: the staleness rule has to
        // read each entry's target, and that is only affordable batched.
        Map<UUID, AuditRevertService.Revertability> verdicts = revertService.describe(page);
        return new AuditLogPageResponse(
                "Success",
                true,
                page.stream().map(log -> toResponse(log, verdicts.get(log.getId()))).toList(),
                nextCursor
        );
    }

    /**
     * Returns getMeta.
     *
     * @param scope the scope
     * @return the result
     */
    @Transactional(readOnly = true)
    public AuditMetaResponse getMeta(AuditActionScope scope) {
        List<AuditAction> scopeActions = AuditAction.of(scope);

        // One row per actor snapshot, newest activity first, so keeping the first row
        // per id keeps the snapshot the log itself shows.
        Map<UUID, AuditActorMetaResponse> actors = new LinkedHashMap<>();
        repository.findActorSnapshots(scopeActions, PageRequest.of(0, ACTOR_LIMIT))
                .forEach(row -> actors.putIfAbsent(
                        (UUID) row[0],
                        new AuditActorMetaResponse(
                                (UUID) row[0],
                                (String) row[1],
                                (String) row[2],
                                (String) row[3]
                        )
                ));
        return new AuditMetaResponse(
                "Success",
                true,
                List.copyOf(actors.values()),
                scopeActions,
                Arrays.asList(AuditTargetType.values()),
                Arrays.asList(AuditActorType.values())
        );
    }

    private AuditLogResponse toResponse(AuditLog log, AuditRevertService.Revertability verdict) {
        AuditTargetResponse target = log.getTargetType() == null
                ? null
                : new AuditTargetResponse(
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getTargetLabel()
                );
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                UtcDates.format(log.getCreatedAt()),
                new AuditActorResponse(
                        log.getActorType().name(),
                        log.getActorId(),
                        log.getActorName(),
                        log.getActorEmail(),
                        log.getActorRole()
                ),
                target,
                log.getChanges(),
                log.getMetadata(),
                verdict.revertible(),
                verdict.reason() == null ? null : verdict.reason().name(),
                verdict.revertedByAuditId()
        );
    }


    private UUID parseUuid(String value, String message) {
        return Uuids.parseOrNull(value, message);
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid audit date range");
        }
    }

    private <T extends Enum<T>> T parseEnum(
            String value,
            Class<T> type,
            String errorMessage
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }
}

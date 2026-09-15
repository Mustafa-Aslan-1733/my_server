package com.pse.audit.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.moderation.model.Admin;
import com.pse.user.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provides AuditWriter.
 */
@Service
public class AuditWriter {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String STUDENT_ROLE = "STUDENT";

    private final AuditLogRepository repository;

    /**
     * Creates AuditWriter.
     *
     * @param repository the repository
     */
    public AuditWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an administrative action. Joins the caller's transaction on purpose, so a
     * failing audit insert rolls the domain mutation back with it.
     *
     * @param actor the actor
     * @param action the action
     * @param targetType the targetType
     * @param targetId the targetId
     * @param targetLabel the targetLabel
     * @param changes the changes
     * @param metadata the metadata
     * @return the result
     */
    public AuditLog write(
            Admin actor,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            Map<String, Object> changes,
            Map<String, Object> metadata
    ) {
        return persist(
                AuditActorType.ADMIN,
                actor.getStudent(),
                ADMIN_ROLE,
                action,
                targetType,
                targetId,
                targetLabel,
                changes,
                metadata
        );
    }

    /**
     * Records something a user did in the student app. Joins the caller's transaction
     * for the same reason {@link #write} does.
     *
     * @param actor the actor
     * @param action the action
     * @param targetType the targetType
     * @param targetId the targetId
     * @param targetLabel the targetLabel
     * @param metadata the metadata
     * @return the result
     */
    public AuditLog writeStudentAction(
            Student actor,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            Map<String, Object> metadata
    ) {
        return persist(
                AuditActorType.USER,
                actor,
                STUDENT_ROLE,
                action,
                targetType,
                targetId,
                targetLabel,
                Map.of(),
                metadata
        );
    }

    /**
     * Records a refused request in its own transaction. The caller is about to answer
     * with an error and roll its own work back, which would take a record written in
     * the caller's transaction with it.
     *
     * @param actor the actor
     * @param admin the admin
     * @param action the action
     * @param targetType the targetType
     * @param targetId the targetId
     * @param targetLabel the targetLabel
     * @param metadata the metadata
     * @return the result
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog writeRefusal(
            Student actor,
            boolean admin,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            Map<String, Object> metadata
    ) {
        return persist(
                admin ? AuditActorType.ADMIN : AuditActorType.USER,
                actor,
                admin ? ADMIN_ROLE : STUDENT_ROLE,
                action,
                targetType,
                targetId,
                targetLabel,
                Map.of(),
                metadata
        );
    }

    private AuditLog persist(
            AuditActorType actorType,
            Student actor,
            String actorRole,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            Map<String, Object> changes,
            Map<String, Object> metadata
    ) {
        AuditLog log = new AuditLog();
        log.setActorType(actorType);
        log.setActorId(actor.getId());
        log.setActorName(actor.getUsername());
        log.setActorEmail(actor.getKitEmail());
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetLabel(targetLabel);
        log.setChanges(changes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(changes));
        log.setMetadata(metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata));
        // Stamped when this write is the reversal of an earlier entry. The reversal is
        // recorded as its own event; the entry it undoes is never modified.
        log.setRevertsAuditId(AuditRevertContext.get());
        return repository.saveAndFlush(log);
    }

    /**
     * Returns change.
     *
     * @param field the field
     * @param before the before
     * @param after the after
     * @return the result
     */
    public static Map<String, Object> change(String field, Object before, Object after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(field, Map.of("before", before, "after", after));
        return changes;
    }

    /**
     * Returns nullableChange.
     *
     * @param field the field
     * @param before the before
     * @param after the after
     * @return the result
     */
    public static Map<String, Object> nullableChange(String field, Object before, Object after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(field, value(before, after));
        return changes;
    }

    /**
     * One field's {@code {before, after}} pair, for the callers that record several fields in
     * a single entry and so cannot use {@link #change} -- it returns the whole changes map.
     *
     * <p>Null-tolerant, unlike {@link #change}: {@code Map.of} rejects a null value and a
     * cleared biography or a removed display name is a legitimate {@code after}.
     *
     * @param before the before
     * @param after the after
     * @return the result
     */
    public static Map<String, Object> value(Object before, Object after) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("before", before);
        value.put("after", after);
        return value;
    }

    /**
     * A creation or a removal, rather than a field edit.
     *
     * <p>The key is {@code exists} and that is the whole point: {@code AuditRevertService}
     * keeps it in {@code LIFECYCLE_KEYS} and refuses to revert any entry carrying one, however
     * the action is classified. The inverse of a creation is a deletion, which is its own
     * action with its own guards. Spelling the key by hand at eleven call sites made that
     * contract a string literal; a typo would have quietly made the entry revertible.
     *
     * @param before the before
     * @param after the after
     * @return the result
     */
    public static Map<String, Object> lifecycle(boolean before, boolean after) {
        return change("exists", before, after);
    }
}

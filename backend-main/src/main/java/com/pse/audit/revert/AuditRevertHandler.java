package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.security.AuthenticatedUser;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Knows how to read and un-do the field edits of one kind of record.
 *
 * <p>Implementations deliberately do not write fields themselves. They call the same service
 * method the panel calls, so a revert inherits every guard that path already enforces — and
 * writes its own audit event, rather than the log being rewritten to hide the original.
 */
public interface AuditRevertHandler {

    /**
     * Simple Getter.
     *
     * @return AuditTargetType
     */
    AuditTargetType targetType();

    /**
     * Current values of every field this handler knows about, keyed by target id. Ids with no
     * surviving row are absent from the result, which is how a missing target is detected.
     *
     * <p>Batched because a page of audit entries is checked in one pass; doing it per entry
     * would put a query per row on the panel's heaviest read.
     *
     * @param targetIds the targetIds
     * @return the result
     */
    Map<UUID, Map<String, Object>> currentValues(Collection<UUID> targetIds);

    /**
     * Applies the recorded {@code before} values back onto the target.
     *
     * @param before the {@code before} half of the entry's {@code changes} map, keyed by field
     * @param principal the principal
     * @param targetId the targetId
     */
    void applyInverse(AuthenticatedUser principal, UUID targetId, Map<String, Object> before);
}

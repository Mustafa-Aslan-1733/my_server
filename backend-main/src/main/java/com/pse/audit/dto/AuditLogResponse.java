package com.pse.audit.dto;

import com.pse.audit.model.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

/**
 * One recorded event.
 *
 * @param revertible whether this entry can be reversed right now. Computed by the server on
 *                   every read, so a client never has to reimplement the window or the
 *                   staleness rule — two copies of that rule would drift apart.
 *
 * @param revertBlockedReason why not, when {@code revertible} is false. Same vocabulary the
 *                   refusal uses, so one explanation covers both the hidden button and the
 *                   button that was clicked after the entry went stale.
 *
 * @param revertedByAuditId the entry that already reversed this one, when there is one. The
 *                   reversal is its own event; this record is never rewritten.
 *
 * @param id the id
 * @param action the action
 * @param createdAt the createdAt
 * @param actor the actor
 * @param target the target
 * @param changes the changes
 * @param metadata the metadata
 */
public record AuditLogResponse(
        UUID id,
        AuditAction action,
        String createdAt,
        AuditActorResponse actor,
        AuditTargetResponse target,
        Map<String, Object> changes,
        Map<String, Object> metadata,
        boolean revertible,
        @Schema(nullable = true) String revertBlockedReason,
        @Schema(nullable = true) UUID revertedByAuditId
) {
}

package com.pse.system.dto;

/**
 * The newest persisted audit event. This is what answers "did the action I just took
 * actually reach the database": if an admin action succeeded, it shows up here.
 *
 * @param at         UTC ISO 8601 timestamp of the event
 * @param ageSeconds how long ago that was, relative to {@code checkedAt}
 * @param action     the recorded {@code AuditAction}
 * @param actorName  snapshotted display name of the actor
 */
public record SystemLastWriteResponse(
        String at,
        long ageSeconds,
        String action,
        String actorName
) {
}

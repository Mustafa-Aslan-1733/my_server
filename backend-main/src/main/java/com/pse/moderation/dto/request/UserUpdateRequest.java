package com.pse.moderation.dto.request;

import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;

/**
 * Partial update of a user. An omitted (null) field is left alone; at least one has to
 * be supplied. {@code biography} accepts {@code ""} to clear it.
 *
 * <p>{@code status} accepts {@code ACTIVE} and {@code BLOCKED} only — deleting a user
 * anonymizes it, which is {@code DELETE /users/{id}} and not a field edit.
 *
 * @param username the username
 * @param kitEmail the kitEmail
 * @param biography the biography
 * @param status the status
 * @param role the role
 * @param credibilityScore the credibilityScore
 */
public record UserUpdateRequest(
        String username,
        String kitEmail,
        String biography,
        UserStatus status,
        UserRole role,
        Integer credibilityScore
) {
}

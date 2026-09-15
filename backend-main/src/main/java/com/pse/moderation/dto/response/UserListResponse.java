package com.pse.moderation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The user listing.
 *
 * @param nextCursor opaque cursor for the next page, or {@code null} when this is the last
 *                   one. Only ever non-null when the caller asked for a page by passing
 *                   {@code limit}; an unparameterised read returns every user at once and so
 *                   has no next page.
 *
 * @param message the message
 * @param success the success
 * @param users the users
 */
public record UserListResponse(
        String message,
        boolean success,
        List<UserResponse> users,
        @Schema(nullable = true) String nextCursor
) {
}

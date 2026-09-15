package com.pse.moderation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * @param nextCursor opaque cursor for the next page, or {@code null} on the last one. Only
 *                   non-null when the caller asked for a page by passing {@code limit}.
 *
 * @param message the message
 * @param success the success
 * @param ratings the ratings
 */
public record ManagedRatingsResponse(
        String message,
        boolean success,
        List<ManagedRatingResponse> ratings,
        @Schema(nullable = true) String nextCursor
) {
}

package com.pse.moderation.dto.response;

import java.util.List;

/**
 * Represents ManagedCommentsResponse.
 *
 * @param message the message
 * @param success the success
 * @param comments the comments
 */
public record ManagedCommentsResponse(
        String message,
        boolean success,
        List<ManagedCommentResponse> comments
) {
}

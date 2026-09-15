package com.pse.social.dto.response;

import java.util.List;

/**
 * Represents CommentsResponse.
 *
 * @param message the message
 * @param success the success
 * @param comments the comments
 */
public record CommentsResponse(
        String message,
        boolean success,
        List<CommentResponse> comments
) {
}

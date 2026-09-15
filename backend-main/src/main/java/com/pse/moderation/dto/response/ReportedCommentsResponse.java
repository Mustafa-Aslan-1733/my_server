package com.pse.moderation.dto.response;

import java.util.List;

/**
 * Represents ReportedCommentsResponse.
 *
 * @param message the message
 * @param success the success
 * @param comments the comments
 */
public record ReportedCommentsResponse(
        String message,
        boolean success,
        List<ReportedCommentResponse> comments
) {
}

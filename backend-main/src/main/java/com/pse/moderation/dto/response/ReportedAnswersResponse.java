package com.pse.moderation.dto.response;

import java.util.List;

/**
 * Represents ReportedAnswersResponse.
 *
 * @param message the message
 * @param success the success
 * @param answers the answers
 */
public record ReportedAnswersResponse(
        String message,
        boolean success,
        List<ReportedAnswerResponse> answers
) {
}

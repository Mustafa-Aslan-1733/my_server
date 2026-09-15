package com.pse.moderation.dto.response;

import java.util.List;

/**
 * Represents ManagedAnswersResponse.
 *
 * @param message the message
 * @param success the success
 * @param answers the answers
 */
public record ManagedAnswersResponse(
        String message,
        boolean success,
        List<ManagedAnswerResponse> answers
) {
}

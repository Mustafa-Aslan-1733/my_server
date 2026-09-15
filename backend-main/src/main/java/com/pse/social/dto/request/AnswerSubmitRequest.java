package com.pse.social.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents AnswerSubmitRequest.
 *
 * @param commentID the commentID
 * @param content the content
 */
public record AnswerSubmitRequest(
        @NotBlank String commentID,
        @NotBlank String content
) {
}

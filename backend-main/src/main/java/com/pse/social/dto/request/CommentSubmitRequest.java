package com.pse.social.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Implements the CommentSubmitRequest.
 *
 * @param lectureID the lecture the comment belongs to. A {@code String} rather than a
 *                  {@code UUID} because that is what the app sends; {@code Uuids.parse}
 *                  converts it, so a malformed one is a 400 rather than the 500 an
 *                  unguarded {@code UUID.fromString} produced.
 * @param content the content
 */
public record CommentSubmitRequest(
        @NotBlank String lectureID,
        @NotBlank String content
) { }

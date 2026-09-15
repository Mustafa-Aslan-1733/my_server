package com.pse.auth.dto.response;

/**
 * Represents AuthMeResponse.
 *
 * @param message the message
 * @param success the success
 * @param user the user
 */
public record AuthMeResponse(
        String message,
        boolean success,
        AuthUserResponse user
) {
}

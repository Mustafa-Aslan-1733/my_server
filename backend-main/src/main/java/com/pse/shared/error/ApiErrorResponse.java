package com.pse.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error body, shaped exactly like {@code BasicResponse} plus an optional {@code reason}.
 *
 * <p>{@code reason} is omitted when null rather than serialised as {@code null}, so every
 * error that does not carry one is byte-identical to what this API returned before.
 *
 * @param message the message
 * @param success the success
 * @param reason the reason
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String message,
        boolean success,
        String reason
) {
    /**
     * Returns of.
     *
     * @param message the message
     * @return the result
     */
    public static ApiErrorResponse of(String message) {
        return new ApiErrorResponse(message, false, null);
    }

    /**
     * Returns of.
     *
     * @param message the message
     * @param reason the reason
     * @return the result
     */
    public static ApiErrorResponse of(String message, String reason) {
        return new ApiErrorResponse(message, false, reason);
    }
}

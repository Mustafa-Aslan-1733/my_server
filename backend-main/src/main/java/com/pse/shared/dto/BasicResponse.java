package com.pse.shared.dto;

/**
 * Represents BasicResponse.
 *
 * @param message the message
 * @param success the success
 */
public record BasicResponse(
        String message,
        boolean success
) { }

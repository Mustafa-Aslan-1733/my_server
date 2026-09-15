package com.pse.user.dto;

import java.util.List;

/**
 * Represents UserRatingResponse.
 *
 * @param message the message
 * @param success the success
 * @param ratings the ratings
 */
public record UserRatingResponse(
        String message,
        boolean success,
        List<UserRatingDto> ratings
) { }

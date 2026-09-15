package com.pse.rating.dto.response;

import java.util.List;

/**
 * Represents RatingsAverageResponse.
 *
 * @param message the message
 * @param success the success
 * @param ratings the ratings
 */
public record RatingsAverageResponse(
        String message,
        boolean success,
        List<TopicAverageResponse> ratings
) {
}

package com.pse.rating.dto.response;


import com.pse.rating.model.RatingCategory;

/**
 * Represents TopicAverageResponse.
 *
 * @param category the category
 * @param value the value
 */
public record TopicAverageResponse(
        RatingCategory category,
        double value
) { }

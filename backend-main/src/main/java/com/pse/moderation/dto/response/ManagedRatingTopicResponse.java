package com.pse.moderation.dto.response;

import com.pse.rating.model.RatingCategory;

/**
 * Represents ManagedRatingTopicResponse.
 *
 * @param category the category
 * @param value the value
 */
public record ManagedRatingTopicResponse(
        RatingCategory category,
        double value
) {
}

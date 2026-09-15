package com.pse.rating.dto.response;

import com.pse.rating.model.RatingCategory;

/**
 * Represents RatingCategoryResponse.
 *
 * @param category the category
 * @param displayName the displayName
 * @param defaultWeight the defaultWeight
 */
public record RatingCategoryResponse(

        RatingCategory category,
        String displayName,
        double defaultWeight

) {
}

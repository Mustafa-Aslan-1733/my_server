package com.pse.rating.dto.response;

import java.util.List;

/**
 * Represents RatingCategoriesResponse.
 *
 * @param message the message
 * @param success the success
 * @param categories the categories
 */
public record RatingCategoriesResponse(

        String message,
        boolean success,
        List<RatingCategoryResponse> categories

) {
}

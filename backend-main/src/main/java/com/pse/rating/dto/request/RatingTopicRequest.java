package com.pse.rating.dto.request;

import com.pse.rating.model.RatingCategory;

import jakarta.validation.constraints.NotNull;

/**
 * Implements the RatingTopicRequest.
 * @param value deliberately unconstrained. The column takes any double and no rule anywhere in
 *              this repository says what the scale is, so a range here would be this file
 *              inventing one. It belongs with whoever owns the scale.
 *
 * @param category the category
 */
public record RatingTopicRequest(
        @NotNull RatingCategory category,
        double value
) { }

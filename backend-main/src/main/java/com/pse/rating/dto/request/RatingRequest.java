package com.pse.rating.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Implements the RatingRequest.
 * @param topics {@code @NotNull} rather than {@code @NotEmpty}: an empty list is answered the
 *               way it always has been, and only an absent one changes. Both used to reach
 *               {@code submitRating} unchallenged, where a null id went to
 *               {@code findById(null)} and a null list to a for-each -- 500 either way.
 *
 * @param lectureId the lectureId
 */
public record RatingRequest(
        @NotNull UUID lectureId,
        @NotNull List<@Valid RatingTopicRequest> topics
) { }

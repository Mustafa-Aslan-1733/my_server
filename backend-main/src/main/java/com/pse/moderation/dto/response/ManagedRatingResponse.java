package com.pse.moderation.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * A rating as the panel manages it — every rating, not aggregated into an average. This is
 * what a rating gets deleted by id from.
 *
 * @param lectureLabel the lecture it was submitted for, formatted {@code "CODE — Name"}
 * @param id the id
 * @param student the student
 * @param lectureId the lectureId
 * @param topics the topics
 * @param createdAt the createdAt
 */
public record ManagedRatingResponse(
        UUID id,
        UserReferenceResponse student,
        String lectureLabel,
        UUID lectureId,
        List<ManagedRatingTopicResponse> topics,
        String createdAt
) {
}

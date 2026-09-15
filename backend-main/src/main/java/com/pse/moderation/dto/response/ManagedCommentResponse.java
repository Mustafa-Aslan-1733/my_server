package com.pse.moderation.dto.response;

import com.pse.shared.enums.ContentStatus;

import java.util.UUID;

/**
 * A comment as the panel manages it — every comment, not only the reported ones.
 *
 * @param postContext the lecture it was posted under, formatted {@code "CODE — Name"}
 * @param id the id
 * @param content the content
 * @param status the status
 * @param author the author
 * @param lectureId the lectureId
 * @param answers the answers
 * @param reports the reports
 * @param createdAt the createdAt
 */
public record ManagedCommentResponse(
        UUID id,
        String content,
        ContentStatus status,
        UserReferenceResponse author,
        String postContext,
        UUID lectureId,
        int answers,
        int reports,
        String createdAt
) {
}

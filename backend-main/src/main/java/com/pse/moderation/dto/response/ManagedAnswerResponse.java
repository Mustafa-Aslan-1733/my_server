package com.pse.moderation.dto.response;

import com.pse.shared.enums.ContentStatus;

import java.util.UUID;

/**
 * An answer as the panel manages it.
 *
 * @param commentId      the comment it answers
 * @param commentPreview enough of that comment to recognise the thread
 * @param postContext    the lecture the thread belongs to, formatted {@code "CODE — Name"}
 * @param id the id
 * @param content the content
 * @param status the status
 * @param author the author
 * @param reports the reports
 * @param createdAt the createdAt
 */
public record ManagedAnswerResponse(
        UUID id,
        String content,
        ContentStatus status,
        UserReferenceResponse author,
        UUID commentId,
        String commentPreview,
        String postContext,
        int reports,
        String createdAt
) {
}

package com.pse.moderation.dto.request;

import com.pse.shared.enums.ContentStatus;

/**
 * Partial update of a comment or an answer. An omitted (null) field is left alone; at
 * least one has to be supplied.
 *
 * <p>{@code status} is the visibility switch the student app reads: {@code HIDDEN}
 * content is filtered out of the read path. Setting it here is independent of any report,
 * so content can be taken down — or put back — without one.
 *
 * @param content the content
 * @param status the status
 */
public record ContentUpdateRequest(
        String content,
        ContentStatus status
) {
}

package com.pse.social.dto.request;

import com.pse.shared.enums.ReportReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Implements the CommentReportRequest.
 *
 * @param explanation deliberately unconstrained: a reason code is required and the free text
 *                    beside it is not, because a reporter who has nothing to add should not be
 *                    made to invent something.
 *
 * @param commentID the commentID
 * @param reportReason the reportReason
 */
public record CommentReportRequest(
        @NotBlank String commentID,
        @NotNull ReportReason reportReason,
        String explanation
) {
}

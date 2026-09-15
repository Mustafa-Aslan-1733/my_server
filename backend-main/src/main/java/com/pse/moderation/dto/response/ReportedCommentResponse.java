package com.pse.moderation.dto.response;

import com.pse.shared.enums.ReportReason;
import com.pse.shared.enums.ReportStatus;

import java.util.UUID;

/**
 * A report filed against a comment.
 *
 * @param id the <em>report's</em> id — what the status and withdrawal routes address
 * @param commentId the reported comment's own id, which is what opening, hiding, editing or
 *                  deleting the content it is about addresses
 *
 * @param commentContent the commentContent
 * @param postContext the postContext
 * @param reportText the reportText
 * @param reportedUser the reportedUser
 * @param reporter the reporter
 * @param reason the reason
 * @param status the status
 * @param date the date
 */
public record ReportedCommentResponse(

        UUID id,
        UUID commentId,
        String commentContent,
        String postContext,
        String reportText,
        UserReferenceResponse reportedUser,
        UserReferenceResponse reporter,
        ReportReason reason,
        ReportStatus status,
        String date
) {
}

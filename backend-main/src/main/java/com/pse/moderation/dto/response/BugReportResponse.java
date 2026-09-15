package com.pse.moderation.dto.response;

import com.pse.shared.enums.BugSeverity;
import com.pse.shared.enums.IssueState;
import com.pse.shared.enums.ReportStatus;


import java.util.UUID;

/**
 * @param issueUrl the tracker issue opened for this report, or null if none has been
 * @param issueState whether an issue exists, was never created, or failed to be created —
 *                   which is what decides whether a client shows a link, an action, or a
 *                   retry
 *
 * @param id the id
 * @param title the title
 * @param description the description
 * @param severity the severity
 * @param status the status
 * @param reporterName the reporterName
 * @param reportedAt the reportedAt
 */
public record BugReportResponse(

        UUID id,
        String title,
        String description,
        BugSeverity severity,
        ReportStatus status,
        String reporterName,
        String reportedAt,
        String issueUrl,
        IssueState issueState
) {
}

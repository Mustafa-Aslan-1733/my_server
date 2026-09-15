package com.pse.moderation.dto.request;

import com.pse.shared.enums.BugSeverity;

/**
 * Represents BugReportRequest.
 *
 * @param title the title
 * @param description the description
 * @param severity the severity
 */
public record BugReportRequest(

        String title,
        String description,
        BugSeverity severity
) {
}

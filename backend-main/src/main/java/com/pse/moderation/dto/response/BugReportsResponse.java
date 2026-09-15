package com.pse.moderation.dto.response;

import java.util.List;

/**
 * Represents BugReportsResponse.
 *
 * @param message the message
 * @param success the success
 * @param bugReports the bugReports
 */
public record BugReportsResponse(
        String message,
        boolean success,
        List<BugReportResponse> bugReports
) {
}

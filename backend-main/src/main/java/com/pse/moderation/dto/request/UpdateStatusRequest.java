package com.pse.moderation.dto.request;

import com.pse.shared.enums.ReportStatus;

/**
 * Represents UpdateStatusRequest.
 *
 * @param status the status
 */
public record UpdateStatusRequest(
        ReportStatus status
) {
}

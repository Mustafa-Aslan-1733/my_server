package com.pse.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A page of the activity log. Same entry shape and same cursor contract as
 * {@link AuditLogPageResponse}; the array is named after this endpoint.
 *
 * @param message the message
 * @param success the success
 * @param activityLogs the activityLogs
 * @param nextCursor the nextCursor
 */
public record ActivityLogPageResponse(
        String message,
        boolean success,
        List<AuditLogResponse> activityLogs,
        @Schema(nullable = true) String nextCursor
) {
}

package com.pse.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Represents AuditLogPageResponse.
 *
 * @param message the message
 * @param success the success
 * @param auditLogs the auditLogs
 * @param nextCursor the nextCursor
 */
public record AuditLogPageResponse(
        String message,
        boolean success,
        List<AuditLogResponse> auditLogs,
        @Schema(nullable = true) String nextCursor
) {
}

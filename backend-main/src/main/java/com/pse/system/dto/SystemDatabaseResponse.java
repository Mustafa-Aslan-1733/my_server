package com.pse.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of the readiness probe against the configured database.
 *
 * @param reachable whether {@code SELECT 1} answered
 * @param latencyMs how long that round trip took, or {@code null} when it failed
 * @param error     the failure class name when unreachable, otherwise {@code null}
 */
public record SystemDatabaseResponse(
        boolean reachable,
        @Schema(nullable = true) Long latencyMs,
        @Schema(nullable = true) String error
) {
}

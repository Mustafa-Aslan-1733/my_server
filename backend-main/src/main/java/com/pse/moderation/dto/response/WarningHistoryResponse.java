package com.pse.moderation.dto.response;

import java.util.List;

/**
 * Represents WarningHistoryResponse.
 *
 * @param message the message
 * @param success the success
 * @param warnings the warnings
 */
public record WarningHistoryResponse(
        String message,
        boolean success,
        List<WarningResponse> warnings
) {
}

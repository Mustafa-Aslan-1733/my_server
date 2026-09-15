package com.pse.moderation.dto.response;

import java.util.UUID;

/**
 * Represents WarningResponse.
 *
 * @param id the id
 * @param userID the userID
 * @param message the message
 * @param createdAt the createdAt
 * @param createdFrom the createdFrom
 */
public record WarningResponse(
        UUID id,
        UUID userID,
        String message,
        String createdAt,
        WarningIssuerResponse createdFrom
) {
}

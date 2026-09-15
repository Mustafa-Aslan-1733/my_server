package com.pse.audit.dto;

import java.util.UUID;

/**
 * Represents AuditActorMetaResponse.
 *
 * @param id the id
 * @param name the name
 * @param email the email
 * @param role the role
 */
public record AuditActorMetaResponse(
        UUID id,
        String name,
        String email,
        String role
) {
}

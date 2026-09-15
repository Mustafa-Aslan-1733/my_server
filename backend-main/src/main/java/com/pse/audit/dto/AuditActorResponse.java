package com.pse.audit.dto;

import java.util.UUID;

/**
 * Represents AuditActorResponse.
 *
 * @param type the type
 * @param id the id
 * @param name the name
 * @param email the email
 * @param role the role
 */
public record AuditActorResponse(
        String type,
        UUID id,
        String name,
        String email,
        String role
) {
}

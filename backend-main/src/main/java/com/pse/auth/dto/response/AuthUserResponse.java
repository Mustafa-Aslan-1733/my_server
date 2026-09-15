package com.pse.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;

import java.util.UUID;

/**
 * Represents AuthUserResponse.
 *
 * @param id the id
 * @param username the username
 * @param kitEmail the kitEmail
 * @param role the role
 * @param status the status
 * @param isSuperAdmin the isSuperAdmin
 */
public record AuthUserResponse(
        UUID id,
        String username,
        String kitEmail,
        UserRole role,
        UserStatus status,
        /*
         * Whether this administrator may also moderate other administrator accounts.
         * A separate field and not a role value: the admin panel's role union is
         * STUDENT | ADMIN, so an elevated operator still reports role ADMIN above.
         *
         * Named explicitly because Jackson drops the "is" prefix from a boolean
         * accessor, which would put this on the wire as "superAdmin".
         */
        @JsonProperty("isSuperAdmin") boolean isSuperAdmin
) {
}

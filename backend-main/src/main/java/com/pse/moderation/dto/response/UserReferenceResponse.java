package com.pse.moderation.dto.response;

import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;

import java.util.UUID;

/**
 * Represents UserReferenceResponse.
 *
 * @param id the id
 * @param name the name
 * @param role the role
 * @param warnings the warnings
 * @param status the status
 */
public record UserReferenceResponse(

        UUID id,
        String name,
        UserRole role,
        int warnings,
        UserStatus status
) {
}

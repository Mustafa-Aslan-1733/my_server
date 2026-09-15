package com.pse.professor.dto.response;

import java.util.UUID;

/**
 * Represents ProfessorShortResponse.
 *
 * @param id the id
 * @param firstName the firstName
 * @param lastName the lastName
 * @param active the active
 */
public record ProfessorShortResponse(
        UUID id,
        String firstName,
        String lastName,
        boolean active
) {
}

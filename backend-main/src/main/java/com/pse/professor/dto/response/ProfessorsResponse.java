package com.pse.professor.dto.response;

import java.util.List;

/**
 * Represents ProfessorsResponse.
 *
 * @param message the message
 * @param success the success
 * @param professors the professors
 */
public record ProfessorsResponse(
        String message,
        boolean success,
        List<ProfessorResponse> professors
) {
}

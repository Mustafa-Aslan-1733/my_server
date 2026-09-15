package com.pse.professor.dto.request;


import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/**
 * Represents ProfessorAddRequest.
 *
 * @param firstName the firstName
 * @param lastName the lastName
 * @param lectureIDs the lectureIDs
 */
public record ProfessorAddRequest(
        @NotBlank
        String firstName,
        @NotBlank
        String lastName,
        List<UUID> lectureIDs
) {
}

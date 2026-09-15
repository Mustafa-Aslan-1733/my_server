package com.pse.professor.dto.response;


/**
 * Represents ProfessorDetailResponse.
 *
 * @param message the message
 * @param success the success
 * @param professor the professor
 */
public record ProfessorDetailResponse(
        String message,
        boolean success,
        ProfessorResponse professor
) { }

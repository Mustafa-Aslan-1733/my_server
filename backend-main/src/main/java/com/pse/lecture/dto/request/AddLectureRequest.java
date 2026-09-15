package com.pse.lecture.dto.request;

import com.pse.shared.enums.SemesterSeason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Represents AddLectureRequest.
 *
 * @param name the name
 * @param code the code
 * @param semesterYear the semesterYear
 * @param semesterSeason the semesterSeason
 * @param active the active
 * @param professorIds the professorIds
 */
public record AddLectureRequest(
        @NotBlank String name,
        @NotBlank String code,
        @Positive int semesterYear,
        @NotNull SemesterSeason semesterSeason,
        boolean active,
        List<UUID> professorIds
) {
}

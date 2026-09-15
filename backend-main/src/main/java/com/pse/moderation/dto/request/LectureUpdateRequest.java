package com.pse.moderation.dto.request;

import com.pse.rating.model.LectureType;
import com.pse.shared.enums.SemesterSeason;

import java.util.List;
import java.util.UUID;

/**
 * Partial update of a lecture. An omitted (null) field is left alone; at least one has to
 * be supplied. {@code professorIds} replaces the assignment wholesale — pass {@code []}
 * to clear it.
 *
 * <p>{@code lectureType} decides which rating categories the lecture offers, so changing
 * it changes what future ratings can score. Ratings already submitted are left as they
 * are; they keep the categories they were given.
 *
 * @param name the name
 * @param code the code
 * @param semesterYear the semesterYear
 * @param semesterSeason the semesterSeason
 * @param active the active
 * @param lectureType the lectureType
 * @param professorIds the professorIds
 */
public record LectureUpdateRequest(
        String name,
        String code,
        Integer semesterYear,
        SemesterSeason semesterSeason,
        Boolean active,
        LectureType lectureType,
        List<UUID> professorIds
) {
}

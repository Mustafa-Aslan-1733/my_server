package com.pse.lecture.dto.response;

import java.util.List;
import java.util.UUID;

import com.pse.professor.dto.response.ProfessorShortResponse;
import com.pse.rating.model.LectureType;
import com.pse.shared.enums.SemesterSeason;


/**
 * A lecture as every read returns it.
 *
 * <p>{@code semesterLabel} and {@code title} are derived, not stored: they are
 * {@code semesterSeason} + {@code semesterYear} + {@code name} + {@code professors} written
 * the way the university writes them. They exist so the {@code WS25/26} rule lives in one
 * place instead of in each client -- see {@link SemesterSeason#label(int)}.
 *
 * <p>{@code professors} is ordered by last name, then first name, then id. It comes out of a
 * {@code Set} with no order of its own, so without that sort the same lecture answers with
 * the names in a different order between two requests, and any title built from them
 * reshuffles under the reader. That is the read side of BUG-3.
 *
 * @param id the id
 * @param name the name
 * @param code the code
 * @param semesterYear the semesterYear
 * @param semesterSeason the semesterSeason
 * @param semesterLabel the semesterLabel
 * @param title the title
 * @param active the active
 * @param lectureType the lectureType
 * @param professors the professors
 * @param commentCount the commentCount
 * @param ratingCount the ratingCount
 * @param averageRating the averageRating
 */
public record LectureResponse(
        UUID id,
        String name,
        String code,
        int semesterYear,
        SemesterSeason semesterSeason,
        String semesterLabel,
        String title,
        boolean active,
        LectureType lectureType,
        List<ProfessorShortResponse> professors,
        int commentCount,
        int ratingCount,
        double averageRating
) { }

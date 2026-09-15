package com.pse.professor.mapper;

import java.math.BigDecimal;
import java.util.Comparator;

import com.pse.lecture.model.Lecture;
import com.pse.professor.dto.response.ProfessorResponse;
import com.pse.professor.dto.response.ProfessorShortResponse;
import com.pse.professor.model.Professor;
import com.pse.professor.service.ProfessorRatings;

/**
 * A professor as the API returns them.
 *
 * <p>Lifted out of {@code ProfessorService}, where it sat as two {@code public static}
 * methods that {@code LectureService} called across the package boundary -- so a class named
 * for a service was really the project's professor formatter, and reading a lecture went
 * through the professor service to find that out.
 */
public final class ProfessorResponseMapper {



    /**
     * Sorted for the same reason a lecture sorts its professors: {@code getLectures()} is a
     * {@code Set} and {@code Lecture} overrides neither {@code equals} nor {@code hashCode},
     * so the ids come out in a different order between two requests otherwise. By name and
     * not by id, because an id order is meaningless to whoever reads the response.
     */
    private static final Comparator<Lecture> BY_NAME =
            Comparator.comparing(Lecture::getName).thenComparing(Lecture::getId);




    private ProfessorResponseMapper() {
    }


    /**
     * Returns toResponse.
     *
     * @param professor the professor
     * @return the result
     */
    public static ProfessorResponse toResponse(Professor professor) {
        // Recomputed on read rather than taken from the stored column, so a rating an
        // administrator removed is reflected without anything having to refresh it.
        //
        // The recomputed figure used to be written back onto the entity here, in a method
        // every caller reaches from a @Transactional(readOnly = true) read. Spring leaves a
        // read-only transaction in FlushMode.MANUAL, so the write was never flushed and the
        // column was never actually updated by a read -- it only fed the line below. Passing
        // the value straight into the response says that, and leaves the entity alone.
        BigDecimal averageRating = BigDecimal.valueOf(ProfessorRatings.average(professor));

        return new ProfessorResponse(
                professor.getId(),
                professor.getFirstName(),
                professor.getLastName(),
                professor.isActive(),
                averageRating,
                // Counted from the same walk, and for the same reason. The stored
                // rating_count column was never written by anything -- no setter call in
                // src/main, no default, no trigger -- so this field answered 0 for every
                // professor who had ever been rated while the average beside it was right.
                ProfessorRatings.of(professor).size(),
                professor.getLectures().stream()
                        .sorted(BY_NAME)
                        .map(Lecture::getId)
                        .toList()
        );
    }

    /**
     * Returns toShortResponse.
     *
     * @param professor the professor
     * @return the result
     */
    public static ProfessorShortResponse toShortResponse(Professor professor) {
        return new ProfessorShortResponse(
                professor.getId(),
                professor.getFirstName(),
                professor.getLastName(),
                professor.isActive()
        );
    }
}

package com.pse.professor.mapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.pse.lecture.model.Lecture;
import com.pse.professor.dto.response.ProfessorResponse;
import com.pse.professor.model.Professor;
import com.pse.rating.model.Rating;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moved here with the code from {@code ProfessorServiceTests}, where it tested a
 * {@code public static} method on a service.
 */
class ProfessorResponseMapperTests {

    /**
     * {@code lectureIds} comes out of {@code Professor.lectures}, a {@code Set}, so it has the
     * same defect the professor list of a lecture had: no order of its own, and therefore a
     * different one between two requests. Fed a {@code LinkedHashSet} in the wrong order for
     * the same reason as {@code LectureServiceTests} -- a real {@code HashSet} would make the
     * test depend on identity-hash order and be flaky about a defect that is not.
     */
    @Test
    void toResponse_lecturesInAnyOrder_answersTheirIdsByLectureName() {

        Professor professor = new Professor();
        professor.setFirstName("Peter");
        professor.setLastName("Sanders");

        Lecture algorithms = lectureNamed("Algorithmen 1");
        Lecture theory = lectureNamed("Theoretische Grundlagen");
        Lecture programming = lectureNamed("Programmieren");

        professor.setLectures(new LinkedHashSet<>(List.of(theory, programming, algorithms)));


        ProfessorResponse response = ProfessorResponseMapper.toResponse(professor);


        assertThat(response.lectureIds())
                .containsExactly(algorithms.getId(), programming.getId(), theory.getId());
    }

    /**
     * {@code ratingCount} was read straight off the entity column, and nothing in the
     * application ever wrote that column -- no setter call anywhere in src/main, no default,
     * no trigger. So the field was 0 for every professor who had ever been rated, while
     * {@code averageRating} beside it was recomputed on read and correct. Counted the same way
     * the average is now.
     */
    @Test
    void toResponse_professorWithRatings_countsThemRatherThanReadingTheStoredColumn() {

        Professor professor = new Professor();
        professor.setFirstName("Peter");
        professor.setLastName("Sanders");

        Lecture algorithms = lectureNamed("Algorithmen 1");
        algorithms.setRatings(new ArrayList<>(List.of(new Rating(), new Rating())));
        Lecture theory = lectureNamed("Theoretische Grundlagen");
        theory.setRatings(new ArrayList<>(List.of(new Rating())));

        professor.setLectures(new LinkedHashSet<>(List.of(algorithms, theory)));

        // The stored column says something else on purpose: before this fix the response
        // handed that number straight out, so 99 here is what a reader used to get.
        professor.setRatingCount(99);


        ProfessorResponse response = ProfessorResponseMapper.toResponse(professor);


        assertThat(response.ratingCount()).isEqualTo(3);
    }

    @Test
    void toResponse_professorWithNoRatings_countsZero() {

        Professor professor = new Professor();
        professor.setLectures(new LinkedHashSet<>(List.of(lectureNamed("Empty"))));

        assertThat(ProfessorResponseMapper.toResponse(professor).ratingCount()).isZero();
    }

    private static Lecture lectureNamed(String name) {
        Lecture lecture = new Lecture();
        lecture.setId(UUID.randomUUID());
        lecture.setName(name);
        lecture.setRatings(new ArrayList<>());
        return lecture;
    }
}

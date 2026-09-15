package com.pse.lecture.mapper;

import java.util.Arrays;
import java.util.LinkedHashSet;

import com.pse.lecture.dto.response.LectureResponse;
import com.pse.lecture.model.Lecture;
import com.pse.professor.model.Professor;
import com.pse.shared.enums.SemesterSeason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ordering, the semester label and the title. Moved here with the code from
 * {@code LectureServiceTests}, where it tested a {@code public static} method on a service.
 */
class LectureResponseMapperTests {


    /**
     * A lecture with its teaching staff in a {@code LinkedHashSet} in the wrong order.
     *
     * <p>{@code LinkedHashSet} on purpose, and this is the point of the whole fixture: the
     * defect is that {@code Lecture.professors} is a {@code Set} whose order is undefined, and
     * a test that fed it a real {@code HashSet} and asserted on what came out would be
     * asserting on identity-hash order -- flaky, and it would put the flakiness in the test
     * rather than in the defect. A {@code LinkedHashSet} states the input order exactly, so a
     * response that comes back in it has not been sorted, and one that comes back in name
     * order has. This is how BUG-3's tests were built for the same reason.
     */
    private static Lecture lectureTaughtBy(Professor... professors) {
        Lecture lecture = new Lecture();
        lecture.setName("Algorithmen 1");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setProfessors(new LinkedHashSet<>(Arrays.asList(professors)));
        return lecture;
    }

    private static Professor professor(String firstName, String lastName) {
        Professor professor = new Professor();
        professor.setFirstName(firstName);
        professor.setLastName(lastName);
        return professor;
    }

    @Test
    void toResponse_professorsInAnyOrder_answersThemByName() {

        Lecture lecture = lectureTaughtBy(
                professor("Peter", "Sanders"),
                professor("Anna", "Bauer"),
                professor("Chris", "Abt")
        );

        LectureResponse response = LectureResponseMapper.toResponse(lecture);


        assertThat(response.professors())
                .extracting(professor -> professor.firstName() + " " + professor.lastName())
                .containsExactly("Chris Abt", "Anna Bauer", "Peter Sanders");
    }

    /** Two people with the same surname are still ordered, and always the same way. */
    @Test
    void toResponse_sameLastName_ordersByFirstName() {

        Lecture lecture = lectureTaughtBy(
                professor("Peter", "Sanders"),
                professor("Anna", "Sanders")
        );

        LectureResponse response = LectureResponseMapper.toResponse(lecture);


        assertThat(response.professors())
                .extracting(professor -> professor.firstName() + " " + professor.lastName())
                .containsExactly("Anna Sanders", "Peter Sanders");
    }

    @Test
    void toResponse_carriesTheSemesterLabelAndTheTitle() {

        Lecture lecture = lectureTaughtBy(
                professor("Uebungsleiter", "2"),
                professor("Peter", "Sanders"),
                professor("Uebungsleiter", "1")
        );

        LectureResponse response = LectureResponseMapper.toResponse(lecture);


        assertThat(response.semesterLabel()).isEqualTo("SS26");
        assertThat(response.title())
                .isEqualTo("SS26 Algorithmen 1 \u2014 Uebungsleiter 1, Uebungsleiter 2, Peter Sanders");
    }

    /**
     * Characterization of F-47, third of three. <b>This pins current behaviour, not intended
     * behaviour</b>, and of the three it is the one most likely to be deliberate: who taught
     * a lecture is a fact about the past, and hiding it would rewrite it. The open product
     * question is item 38 in {@code docs/TODO.md}, the defect item 37.
     *
     * <p>{@code toResponse} sorts {@code lecture.getProfessors()} without filtering, so a
     * professor who has left {@code GET /data/professor} is still nested in every active
     * lecture they teach -- carrying {@code active: false}, which a client can act on -- and
     * still inside the generated {@code title}, which is prose and cannot carry a flag. The
     * title is the half worth pinning: it is what a client renders when it renders one string
     * instead of three.
     *
     * <p>Invert, do not delete, if the answer is that the staff list follows the catalogue.
     */
    @Test
    void toResponse_deactivatedProfessor_staysInTheListAndInTheTitle() {

        Professor retired = professor("Gerda", "Abt");
        retired.setActive(false);

        Lecture lecture = lectureTaughtBy(retired, professor("Peter", "Sanders"));

        LectureResponse response = LectureResponseMapper.toResponse(lecture);

        assertThat(response.professors())
                .extracting(professor -> professor.lastName() + "=" + professor.active())
                .containsExactly("Abt=false", "Sanders=true");

        assertThat(response.title())
                .isEqualTo("SS26 Algorithmen 1 \u2014 Gerda Abt, Peter Sanders");
    }


    /** A winter lecture is the case a client formatting this itself gets wrong. */
    @Test
    void toResponse_winterSemester_labelsBothYears() {

        Lecture lecture = lectureTaughtBy();
        lecture.setSemesterSeason(SemesterSeason.WS);
        lecture.setSemesterYear(2025);

        LectureResponse response = LectureResponseMapper.toResponse(lecture);


        assertThat(response.semesterLabel()).isEqualTo("WS25/26");
        assertThat(response.title()).isEqualTo("WS25/26 Algorithmen 1");
    }

    /** No staff, no separator: a title ending in a dash reads as a bug to whoever sees it. */
    @Test
    void toResponse_lectureWithNobodyAssigned_endsAfterTheName() {

        LectureResponse response =
                LectureResponseMapper.toResponse(lectureTaughtBy());


        assertThat(response.professors()).isEmpty();
        assertThat(response.title()).isEqualTo("SS26 Algorithmen 1");
    }
}

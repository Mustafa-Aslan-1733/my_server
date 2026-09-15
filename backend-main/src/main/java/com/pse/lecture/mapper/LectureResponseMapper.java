package com.pse.lecture.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.pse.lecture.dto.response.LectureResponse;
import com.pse.lecture.model.Lecture;
import com.pse.professor.dto.response.ProfessorShortResponse;
import com.pse.professor.mapper.ProfessorResponseMapper;
import com.pse.professor.model.Professor;
import com.pse.rating.model.Rating;
import com.pse.rating.service.RatingAverages;
import com.pse.shared.enums.UserStatus;

/**
 * A lecture as the API returns it.
 *
 * <p>Lifted out of {@code LectureService}, where it was a {@code public static} method that
 * {@code SocialService} and {@code StudentService} called directly. Formatting a lecture is
 * not something the lecture service does for them, and routing it through that class made
 * two feature modules depend on a third one's service to render a payload.
 */
public final class LectureResponseMapper {



    /**
     * Orders the teaching staff of a lecture: last name, then first name, then id.
     *
     * <p>The same order {@code ProfessorRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc}
     * already uses for the professor listings, so the two surfaces agree. The id is the
     * tie-breaker and is only there to make the sort total -- two people can share a name.
     *
     * <p><b>It is alphabetical, not by role.</b> {@code lecture_professors} records that a
     * person teaches a lecture and nothing else, so the schema cannot say who lectures and
     * who runs an exercise group; that would need a column on the join table. Alphabetical
     * is what the data supports, and it is stable, which is the defect being fixed.
     */
    private static final Comparator<Professor> BY_NAME =
            Comparator.comparing(Professor::getLastName)
                    .thenComparing(Professor::getFirstName)
                    .thenComparing(Professor::getId);



    private LectureResponseMapper() {
    }


    /**
     * Returns toResponse.
     *
     * @param lecture the lecture
     * @return the result
     */
    public static LectureResponse toResponse(Lecture lecture) {

        // Sorted, not iterated as it comes. lecture.getProfessors() is a Set and Professor
        // overrides neither equals nor hashCode, so the iteration order is identity-hash
        // order and can differ on every run -- the read side of BUG-3. An unordered list is
        // invisible in a JSON payload and obvious in a title built from it.
        List<ProfessorShortResponse> professors = lecture.getProfessors().stream()
                .sorted(BY_NAME)
                .map(ProfessorResponseMapper::toShortResponse)
                .toList();

        String semesterLabel = lecture.getSemesterSeason().label(lecture.getSemesterYear());

        // Ignore ratings from deleted users
        var activeRatings = lecture.getRatings().stream()
                .filter(rating -> rating.getStudent() == null
                        || rating.getStudent().getStatus() != UserStatus.DELETED)
                .toList();

        return new LectureResponse(
                lecture.getId(),
                lecture.getName(),
                lecture.getCode(),
                lecture.getSemesterYear(),
                lecture.getSemesterSeason(),
                semesterLabel,
                title(semesterLabel, lecture.getName(), professors),
                lecture.isActive(),
                lecture.getLectureType(),
                professors,
                lecture.getComments().size(),
                activeRatings.size(),
                RatingAverages.overallAcross(activeRatings)
        );
    }

    /**
     * {@code "SS26 Algorithmen 1 -- Peter Sanders, Uebungsleiter 1"}, with an em dash.
     *
     * <p>Built here so that every client shows one string rather than three near-identical
     * ones. A lecture nobody is assigned to is just {@code "SS26 Algorithmen 1"}: the
     * separator belongs to the names, and a title ending in a dash reads as a bug.
     */
    private static String title(String semesterLabel,
                                String name,
                                List<ProfessorShortResponse> professors) {

        String heading = semesterLabel + " " + name;

        if (professors.isEmpty()) {
            return heading;
        }

        return heading + " — " + professors.stream()
                .map(professor -> professor.firstName() + " " + professor.lastName())
                .collect(Collectors.joining(", "));
    }
}

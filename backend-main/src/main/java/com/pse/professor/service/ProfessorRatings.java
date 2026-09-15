package com.pse.professor.service;

import java.util.List;

import com.pse.lecture.model.Lecture;
import com.pse.professor.model.Professor;
import com.pse.rating.model.Rating;
import com.pse.rating.service.RatingAverages;

/**
 * How a professor reaches the ratings they are judged by: every rating on every lecture they
 * teach.
 *
 * <p>Named because two unrelated places need the same walk and must not disagree about it.
 * Both recompute on read, so a rating an administrator removes is reflected without anyone
 * having to remember to refresh a stored copy.
 *
 * <p>This paragraph used to say that {@code RatingService} recomputes the figure on submission
 * and stores it on the row. It does not, and never did: {@code professors.average_rating} and
 * {@code professors.rating_count} are written by nothing but the entity's field initialisers
 * and read by nothing at all -- which is how {@code ratingCount} came to answer 0 for every
 * professor (F-26). Both columns are dead and want dropping; see docs/worklog.md.
 */
public final class ProfessorRatings {

    private ProfessorRatings() {
    }

    /**
     * Every rating on every lecture this professor teaches.
     *
     * @param professor the professor
     * @return the result
     */
    public static List<Rating> of(Professor professor) {
        return professor.getLectures().stream()
                .map(Lecture::getRatings)
                .flatMap(List::stream)
                .toList();
    }

    /**
     * The professor's overall rating: {@code 0.0} when nobody has rated them.
     *
     * @param professor the professor
     * @return the result
     */
    public static double average(Professor professor) {
        return RatingAverages.overallAcross(of(professor));
    }
}

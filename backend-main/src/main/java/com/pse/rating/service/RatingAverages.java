package com.pse.rating.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.model.RatingTopic;
import com.pse.shared.enums.UserStatus;

/**
 * The three averages this application computes over ratings, in one place.
 *
 * <p>They are genuinely three, and telling them apart is the reason this class exists rather
 * than one method: {@link #weightedScore} derives a single rating's headline number from its
 * category topics, {@link #overallAcross} averages the {@code OVERALL} topic already stored
 * on each rating, and {@link #scoreAcross} re-derives that number per rating and averages
 * the results. The last two agree on data written by {@code RatingService.submitRating},
 * which stores {@code weightedScore} as the {@code OVERALL} topic -- they are two routes to
 * the same figure, and only one of them reads what is on the row.
 *
 * <p>Before this, the walk down {@code Rating -> RatingTopic -> category == OVERALL} was
 * written out in {@code LectureService}, {@code ProfessorService}, {@code RatingService} and
 * {@code StudentService}. The lecture copy stopped at the first {@code OVERALL} topic of each
 * rating and the professor copy did not, which looks like a disagreement about what to count
 * and is not one: {@code uk_rating_topics_rating_category} makes {@code (rating_id, category)}
 * unique, so a rating has at most one {@code OVERALL} topic and the two loops always counted
 * the same rows.
 *
 * <p>Callers navigate to the ratings themselves. Taking a {@code Collection<Rating>} is what
 * keeps this class free of {@code Lecture}, {@code Professor} and {@code Student}, which each
 * reach their ratings by a different path.
 */
public final class RatingAverages {

    private RatingAverages() {
    }

    /**
     * A single rating's headline number: the weighted mean of its topics, {@code OVERALL}
     * itself excluded, rounded to two decimals. This is the value
     * {@code RatingService.submitRating} stores as the rating's {@code OVERALL} topic.
     *
     * @return {@code 0} for a rating carrying no weighted topic at all
     * @param rating the rating
     */
    public static double weightedScore(Rating rating) {
        double weightedSum = 0;
        double totalWeight = 0;

        for (RatingTopic topic : rating.getTopics()) {
            if (topic.getCategory() == RatingCategory.OVERALL) {
                continue;
            }
            double weight = topic.getCategory().getDefaultWeight();
            weightedSum += topic.getValue() * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0) {
            return 0;
        }

        return BigDecimal.valueOf(weightedSum / totalWeight)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * How a lecture or a professor is rated: the mean of the {@code OVERALL} topic stored on
     * each of these ratings. Ratings without one do not count towards the divisor.
     *
     * @return {@code 0.0} when none of the ratings carries an {@code OVERALL} topic
     * @param ratings the ratings
     */
    public static double overallAcross(Collection<Rating> ratings) {
        double sum = 0.0;
        int count = 0;

        for (Rating rating : ratings) {

            // Ignore ratings from deleted users, but allow test ratings without a student.
            if (rating.getStudent() != null
                    && rating.getStudent().getStatus() == UserStatus.DELETED) {
                continue;
            }

            for (RatingTopic topic : rating.getTopics()) {
                if (topic.getCategory() == RatingCategory.OVERALL) {
                    sum += topic.getValue();
                    count++;
                }
            }
        }

        return count == 0 ? 0.0 : sum / count;
    }

    /**
     * The mean of {@link #weightedScore} over these ratings -- how an author's own ratings
     * average out, re-derived rather than read off the {@code OVERALL} topic.
     *
     * @return {@code 0} when there are no ratings
     * @param ratings the ratings
     */
    public static double scoreAcross(Collection<Rating> ratings) {
        if (ratings.isEmpty()) {
            return 0;
        }

        double sum = 0;
        for (Rating rating : ratings) {
            sum += weightedScore(rating);
        }

        return sum / ratings.size();
    }
}

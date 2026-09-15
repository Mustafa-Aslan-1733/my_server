package com.pse.rating.service;

import java.util.List;

import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.model.RatingTopic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The three averages, tested apart from the services that used to hold them. Moved here with
 * the code: {@code weightedScore} was {@code RatingService.calculateAverageScore} and its two
 * cases are the ones {@code RatingServiceTests} carried.
 */
class RatingAveragesTests {

    // ------------------------------------------------------------------ weightedScore

    @Test
    void weightedScore_noTopics_isZero() {
        assertThat(RatingAverages.weightedScore(rating())).isCloseTo(0.0, within(0.001));
    }

    @Test
    void weightedScore_topicsOfOneCategory_averagesThemEvenly() {
        // Same category => same weight, so (4 + 2) / 2 = 3.
        Rating rating = rating(
                topic(RatingCategory.ORGANIZATION, 4.0),
                topic(RatingCategory.ORGANIZATION, 2.0));

        assertThat(RatingAverages.weightedScore(rating)).isCloseTo(3.0, within(0.001));
    }

    @Test
    void weightedScore_overallTopic_isExcludedFromItsOwnDerivation() {
        // The OVERALL topic is what this method produces, so counting it would feed the
        // result back into itself. Only the ORGANIZATION topic may contribute.
        Rating rating = rating(
                topic(RatingCategory.OVERALL, 1.0),
                topic(RatingCategory.ORGANIZATION, 5.0));

        assertThat(RatingAverages.weightedScore(rating)).isCloseTo(5.0, within(0.001));
    }

    // ------------------------------------------------------------------ overallAcross

    @Test
    void overallAcross_noRatings_isZero() {
        assertThat(RatingAverages.overallAcross(List.of())).isEqualTo(0.0);
    }

    @Test
    void overallAcross_ratingsWithoutAnOverallTopic_isZero() {
        // Not a division by zero: a rating carrying no OVERALL topic must not count towards
        // the divisor either.
        Rating rating = rating(topic(RatingCategory.ORGANIZATION, 4.0));

        assertThat(RatingAverages.overallAcross(List.of(rating))).isEqualTo(0.0);
    }

    @Test
    void overallAcross_readsTheStoredOverallTopicAndIgnoresTheRest() {
        Rating first = rating(
                topic(RatingCategory.OVERALL, 4.0),
                topic(RatingCategory.ORGANIZATION, 1.0));
        Rating second = rating(topic(RatingCategory.OVERALL, 2.0));

        assertThat(RatingAverages.overallAcross(List.of(first, second))).isEqualTo(3.0);
    }

    // ------------------------------------------------------------------ scoreAcross

    @Test
    void scoreAcross_noRatings_isZero() {
        assertThat(RatingAverages.scoreAcross(List.of())).isEqualTo(0.0);
    }

    @Test
    void scoreAcross_averagesEachRatingsDerivedScoreRatherThanItsStoredOverall() {
        // The stored OVERALL says 99; scoreAcross must ignore it and re-derive 4 and 2.
        Rating first = rating(
                topic(RatingCategory.OVERALL, 99.0),
                topic(RatingCategory.ORGANIZATION, 4.0));
        Rating second = rating(topic(RatingCategory.ORGANIZATION, 2.0));

        assertThat(RatingAverages.scoreAcross(List.of(first, second)))
                .isCloseTo(3.0, within(0.001));
    }

    private static Rating rating(RatingTopic... topics) {
        Rating rating = new Rating();
        rating.setTopics(List.of(topics));
        return rating;
    }

    private static RatingTopic topic(RatingCategory category, double value) {
        RatingTopic topic = new RatingTopic();
        topic.setCategory(category);
        topic.setValue(value);
        return topic;
    }
}

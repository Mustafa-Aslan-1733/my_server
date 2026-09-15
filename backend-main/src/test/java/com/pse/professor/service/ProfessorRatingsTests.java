package com.pse.professor.service;

import java.util.List;
import java.util.Set;

import com.pse.lecture.model.Lecture;
import com.pse.professor.model.Professor;
import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.model.RatingTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The walk from a professor to the ratings they are judged by. Both cases moved here with the
 * code from {@code ProfessorServiceTests.calculateAverageRating*}.
 */
@ExtendWith(MockitoExtension.class)
class ProfessorRatingsTests {

    @Test
    void average_professorTeachesNothing_isZero() {
        Professor professor = mock(Professor.class);
        when(professor.getLectures()).thenReturn(Set.of());

        assertThat(ProfessorRatings.average(professor)).isEqualTo(0.0);
    }

    @Test
    void average_usesTheOverallTopicOfEveryRatingOnEveryLectureTaught() {
        Professor professor = mock(Professor.class);
        Lecture lecture = mock(Lecture.class);

        Rating first = mock(Rating.class);
        Rating second = mock(Rating.class);

        RatingTopic overallOfFirst = mock(RatingTopic.class);
        RatingTopic overallOfSecond = mock(RatingTopic.class);
        RatingTopic organization = mock(RatingTopic.class);

        when(professor.getLectures()).thenReturn(Set.of(lecture));
        when(lecture.getRatings()).thenReturn(List.of(first, second));
        when(first.getTopics()).thenReturn(List.of(overallOfFirst, organization));
        when(second.getTopics()).thenReturn(List.of(overallOfSecond));

        when(overallOfFirst.getCategory()).thenReturn(RatingCategory.OVERALL);
        when(overallOfFirst.getValue()).thenReturn(4.0);
        when(overallOfSecond.getCategory()).thenReturn(RatingCategory.OVERALL);
        when(overallOfSecond.getValue()).thenReturn(2.0);
        when(organization.getCategory()).thenReturn(RatingCategory.ORGANIZATION);

        assertThat(ProfessorRatings.average(professor)).isEqualTo(3.0);
    }

    @Test
    void of_gathersTheRatingsOfEveryLectureTaught() {
        Professor professor = mock(Professor.class);
        Lecture first = mock(Lecture.class);
        Lecture second = mock(Lecture.class);
        Rating one = mock(Rating.class);
        Rating two = mock(Rating.class);

        when(professor.getLectures()).thenReturn(Set.of(first, second));
        when(first.getRatings()).thenReturn(List.of(one));
        when(second.getRatings()).thenReturn(List.of(two));

        assertThat(ProfessorRatings.of(professor)).containsExactlyInAnyOrder(one, two);
    }
}

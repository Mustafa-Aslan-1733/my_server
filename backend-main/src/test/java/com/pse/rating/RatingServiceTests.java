package com.pse.rating;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.service.LectureService;
import com.pse.rating.dto.request.RatingRequest;
import com.pse.rating.dto.request.RatingTopicRequest;
import com.pse.rating.dto.response.RatingCategoriesResponse;
import com.pse.rating.dto.response.RatingsAverageResponse;
import com.pse.rating.dto.response.TopicAverageResponse;
import com.pse.rating.model.LectureType;
import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.model.RatingTopic;
import com.pse.rating.repository.RatingRepository;
import com.pse.rating.service.RatingService;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTests {

    @Mock
    private LectureService lectureService;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private AuditWriter auditWriter;

    @InjectMocks
    private RatingService ratingService;


    /**
     * Inverted from {@code submitRatingReturnsErrorWhenLectureDoesNotExist}, which pinned the
     * old {@code 200 {"success": false}}. The two reads either side of this write --
     * {@code getOwnRating} and {@code getRatingCategories} -- have answered 404 for the same
     * unknown id since F-15, so this was the last route in the module disagreeing.
     */
    @Test
    void submitRatingUnknownLectureIsNotFoundRatherThanASuccessfulFailureBody() {

        Student student = mock(Student.class);
        RatingRequest request = mock(RatingRequest.class);

        UUID lectureId = UUID.randomUUID();

        when(request.lectureId())
                .thenReturn(lectureId);

        when(lectureService.getById(lectureId))
                .thenReturn(null);


        ApiException thrown = catchThrowableOfType(
                ApiException.class,
                () -> ratingService.submitRating(student, request));


        assertThat(thrown).as("nothing was thrown").isNotNull();

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(thrown.getMessage()).isEqualTo("Lecture not found");

        verify(ratingRepository, never()).save(any());

        verifyNoInteractions(auditWriter);
    }

    /**
     * Characterization of F-47, second of three. <b>This pins current behaviour, not intended
     * behaviour</b>: whether {@code active = false} should close a lecture to new content is
     * the open product question recorded as item 38 in {@code docs/TODO.md}, the defect as
     * item 37.
     *
     * <p>{@code submitRating} resolves its target through {@code LectureService.getById} and
     * checks only that it exists. Deactivating a lecture therefore removes it from the
     * catalogue while leaving it open to new ratings from anyone who still holds the id --
     * this is the consequence of the three that produces <em>new</em> data on a row somebody
     * has retired, and the one that reads as an oversight rather than as a decision.
     *
     * <p>The {@code lenient()} below is the finding stated as a stub: the service never asks
     * whether the lecture is active, so a strict stub would fail as an unnecessary one. When
     * the rule arrives, that {@code lenient()} becomes a real stub and the assertions below
     * become an {@code ApiException}. Invert it; do not delete it.
     */
    @Test
    void submitRatingIsAcceptedForADeactivatedLecture() {

        Student student = mock(Student.class);
        Lecture deactivated = mock(Lecture.class);
        RatingRequest request = mock(RatingRequest.class);
        RatingTopicRequest organization = mock(RatingTopicRequest.class);

        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();

        lenient().when(deactivated.isActive()).thenReturn(false);

        when(student.getId()).thenReturn(studentId);
        when(request.lectureId()).thenReturn(lectureId);
        when(request.topics()).thenReturn(List.of(organization));
        when(organization.category()).thenReturn(RatingCategory.ORGANIZATION);
        when(organization.value()).thenReturn(4.0);
        when(lectureService.getById(lectureId)).thenReturn(deactivated);
        when(ratingRepository.findByStudentIdAndLectureId(studentId, lectureId))
                .thenReturn(Optional.empty());
        when(deactivated.getCode()).thenReturn("ALT");
        when(deactivated.getName()).thenReturn("Alte Vorlesung");
        when(deactivated.getId()).thenReturn(lectureId);

        BasicResponse response = ratingService.submitRating(student, request);

        assertThat(response.success()).isTrue();

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLecture()).isSameAs(deactivated);

        // Said directly, because it is the defect rather than a side effect of it.
        verify(deactivated, never()).isActive();
    }



    @Test
    void submitRatingCreatesNewRatingAndAddsOverallTopic() {

        Student student = mock(Student.class);
        Lecture lecture = mock(Lecture.class);

        RatingRequest request = mock(RatingRequest.class);

        RatingTopicRequest organization = mock(RatingTopicRequest.class);

        UUID studentId = UUID.randomUUID();

        UUID lectureId = UUID.randomUUID();


        when(student.getId())
                .thenReturn(studentId);

        when(request.lectureId())
                .thenReturn(lectureId);

        when(request.topics())
                .thenReturn(List.of(organization));

        when(organization.category())
                .thenReturn(
                        RatingCategory.ORGANIZATION
                );

        when(organization.value())
                .thenReturn(4.0);

        when(lectureService.getById(lectureId))
                .thenReturn(lecture);

        when(ratingRepository
                .findByStudentIdAndLectureId(
                        studentId,
                        lectureId
                ))
                .thenReturn(Optional.empty());

        when(lecture.getCode())
                .thenReturn("CS101");

        when(lecture.getName())
                .thenReturn("Algorithms");

        when(lecture.getId())
                .thenReturn(lectureId);


        BasicResponse response =
                ratingService.submitRating(
                        student,
                        request
                );


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Rating submitted successfully");


        ArgumentCaptor<Rating> captor =
                ArgumentCaptor.forClass(
                        Rating.class
                );

        verify(ratingRepository).saveAndFlush(captor.capture());

        Rating saved = captor.getValue();

        assertThat(saved.getStudent()).isSameAs(student);
        assertThat(saved.getLecture()).isSameAs(lecture);

        assertThat(saved.getTopics().size()).isEqualTo(2);


        RatingTopic normalTopic =
                saved.getTopics()
                        .stream()
                        .filter(topic ->
                                topic.getCategory()
                                        == RatingCategory.ORGANIZATION)
                        .findFirst()
                        .orElseThrow();

        assertThat(normalTopic.getValue()).isCloseTo(4.0, within(0.001));


        RatingTopic overall =
                saved.getTopics()
                        .stream()
                        .filter(topic ->
                                topic.getCategory()
                                        == RatingCategory.OVERALL)
                        .findFirst()
                        .orElseThrow();


        assertThat(overall.getValue()).isCloseTo(4.0, within(0.001));


        verify(auditWriter)
                .writeStudentAction(
                        eq(student),
                        eq(AuditAction.RATING_SUBMITTED),
                        eq(AuditTargetType.RATING),
                        nullable(UUID.class),
                        eq("CS101 — Algorithms"),
                        anyMap()
                );
    }

    @Test
    void submitRatingIgnoresOverallSentByClient() {

        Student student = mock(Student.class);
        Lecture lecture = mock(Lecture.class);

        RatingRequest request = mock(RatingRequest.class);

        RatingTopicRequest organization = mock(RatingTopicRequest.class);

        RatingTopicRequest maliciousOverall = mock(RatingTopicRequest.class);

        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();


        when(student.getId())
                .thenReturn(studentId);

        when(request.lectureId())
                .thenReturn(lectureId);

        when(request.topics())
                .thenReturn(
                        List.of(
                                organization,
                                maliciousOverall
                        )
                );

        when(organization.category())
                .thenReturn(
                        RatingCategory.ORGANIZATION
                );

        when(organization.value())
                .thenReturn(4.0);

        when(maliciousOverall.category())
                .thenReturn(
                        RatingCategory.OVERALL
                );

        when(lectureService.getById(lectureId))
                .thenReturn(lecture);

        when(lecture.getId())
                .thenReturn(lectureId);

        when(ratingRepository
                .findByStudentIdAndLectureId(
                        studentId,
                        lectureId
                ))
                .thenReturn(Optional.empty());


        ratingService.submitRating(
                student,
                request
        );


        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);

        verify(ratingRepository).saveAndFlush(captor.capture());

        Rating saved = captor.getValue();


        long overallTopics =
                saved.getTopics()
                        .stream()
                        .filter(topic ->
                                topic.getCategory()
                                        == RatingCategory.OVERALL)
                        .count();


        assertThat(overallTopics).isEqualTo(1);
    }



    @Test
    void submitRatingOverwritesExistingRatingInsteadOfCreatingSecondRating() {

        Student student = mock(Student.class);
        Lecture lecture = mock(Lecture.class);

        RatingRequest request = mock(RatingRequest.class);

        RatingTopicRequest newTopic = mock(RatingTopicRequest.class);

        Rating existing = mock(Rating.class);

        List<RatingTopic> existingTopics = new ArrayList<>();

        RatingTopic oldTopic = new RatingTopic();

        oldTopic.setCategory(RatingCategory.ORGANIZATION);

        oldTopic.setValue(1.0);

        existingTopics.add(oldTopic);


        UUID studentId = UUID.randomUUID();

        UUID lectureId = UUID.randomUUID();


        when(student.getId())
                .thenReturn(studentId);

        when(request.lectureId())
                .thenReturn(lectureId);

        when(request.topics())
                .thenReturn(List.of(newTopic));

        when(newTopic.category())
                .thenReturn(
                        RatingCategory.ORGANIZATION
                );

        when(newTopic.value())
                .thenReturn(5.0);

        when(lectureService.getById(lectureId))
                .thenReturn(lecture);

        when(lecture.getId())
                .thenReturn(lectureId);

        when(ratingRepository
                .findByStudentIdAndLectureId(
                        studentId,
                        lectureId
                ))
                .thenReturn(
                        Optional.of(existing)
                );

        when(existing.getTopics())
                .thenReturn(existingTopics);


        BasicResponse response =
                ratingService.submitRating(
                        student,
                        request
                );


        assertThat(response.success()).isTrue();

        assertThat(existingTopics.size()).isEqualTo(2);

        assertThat(existingTopics.contains(oldTopic)).isFalse();


        verify(ratingRepository).flush();

        verify(ratingRepository).saveAndFlush(existing);
    }


    /**
     * The averages came out of a {@code HashMap} keyed by the enum, and {@code Enum.hashCode}
     * is the identity hash -- so the bucket order is decided by where the JVM happened to put
     * the constants. Two runs of the same process disagree, and so do two instances behind a
     * load balancer, on a public read. F-21's shape, one package over.
     *
     * <p>Whether this assertion fails against a {@code HashMap} is itself up to the JVM, which
     * is the defect stated precisely. What it pins is the guarantee after the fix: declaration
     * order, every run, whatever order the rows arrive in. The topics below are fed in
     * deliberately scrambled order so that passing means something.
     */
    @Test
    void getRatingsAnswersItsCategoriesInADeterministicOrder() {

        UUID lectureId = UUID.randomUUID();

        Rating rating = mock(Rating.class);
        when(rating.getTopics()).thenReturn(List.of(
                topic(RatingCategory.TUTOR_FEEDBACK, 3.0),
                topic(RatingCategory.ROOM, 4.0),
                topic(RatingCategory.PROFESSOR_ENGAGEMENT, 5.0),
                topic(RatingCategory.ORGANIZATION, 2.0),
                topic(RatingCategory.EXERCISE_PACE, 1.0)));

        when(ratingRepository.findByLectureIdAndStudentStatus(
                lectureId,
                UserStatus.ACTIVE
        )).thenReturn(List.of(rating));

        RatingsAverageResponse response = ratingService.getRatings(lectureId);

        assertThat(response.ratings())
                .extracting(TopicAverageResponse::category)
                .containsExactly(
                        RatingCategory.ROOM,
                        RatingCategory.ORGANIZATION,
                        RatingCategory.PROFESSOR_ENGAGEMENT,
                        RatingCategory.EXERCISE_PACE,
                        RatingCategory.TUTOR_FEEDBACK);
    }

    @Test
    void getRatingsCalculatesAveragePerCategory() {

        UUID lectureId = UUID.randomUUID();

        Rating rating1 = mock(Rating.class);

        Rating rating2 = mock(Rating.class);


        RatingTopic first =
                topic(
                        RatingCategory.ORGANIZATION,
                        4.0
                );

        RatingTopic second =
                topic(
                        RatingCategory.ORGANIZATION,
                        2.0
                );


        when(rating1.getTopics())
                .thenReturn(List.of(first));

        when(rating2.getTopics())
                .thenReturn(List.of(second));

        when(ratingRepository.findByLectureIdAndStudentStatus(
                lectureId,
                UserStatus.ACTIVE
        )).thenReturn(List.of(
                rating1,
                rating2
        ));


        RatingsAverageResponse response = ratingService.getRatings(lectureId);


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Success, found 1 ratings");

        assertThat(response.ratings().size()).isEqualTo(1);

        assertThat(response.ratings()
                        .getFirst()
                        .category()).isEqualTo(RatingCategory.ORGANIZATION);

        assertThat(response.ratings().getFirst().value()).isCloseTo(3.0, within(0.001));
    }


    @Test
    void getRatingsReturnsEmptyListWhenNoRatingsExist() {

        UUID lectureId = UUID.randomUUID();

        when(ratingRepository.findByLectureIdAndStudentStatus(
                lectureId,
                UserStatus.ACTIVE
        )).thenReturn(List.of());

        RatingsAverageResponse response = ratingService.getRatings(lectureId);


        assertThat(response.success()).isTrue();

        assertThat(response.ratings().size()).isEqualTo(0);

        assertThat(response.message()).isEqualTo("Success, found 0 ratings");
    }



    @Test
    void getOwnRatingUnknownLectureIsNotFound() {

        Student student = mock(Student.class);

        UUID lectureId = UUID.randomUUID();

        when(lectureService.getById(lectureId))
                .thenReturn(null);


        ApiException thrown = catchThrowableOfType(ApiException.class, () -> ratingService.getOwnRating(
                        student,
                        lectureId
                ));
        assertThat(thrown).as("nothing was thrown").isNotNull();


        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(thrown.getMessage()).isEqualTo("Lecture not found");
    }


    /**
     * F-27's surface, on the route beside the one that was fixed. {@code getRatings} was given
     * an {@code EnumMap} so the public read returns categories in declaration order; this
     * method, two below it, builds the same {@code RatingsAverageResponse.ratings} field by
     * walking {@code rating.getTopics()} -- a JPA bag with no {@code @OrderBy} -- so the same
     * field came back in whatever order the database happened to return.
     *
     * <p>Declaration order rather than alphabetical, because that is what the CHANGELOG
     * already promised for the sibling route and one field cannot have two orders.
     */
    @Test
    void getOwnRatingReturnsTheCategoriesInDeclarationOrder() {

        Student student = mock(Student.class);
        Lecture lecture = mock(Lecture.class);

        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();

        Rating rating = new Rating();
        // Stored in reverse declaration order, which an unordered bag is free to return.
        rating.setTopics(new ArrayList<>(List.of(
                topic(rating, RatingCategory.WORKLOAD, 4.0),
                topic(rating, RatingCategory.ORGANIZATION, 3.0),
                topic(rating, RatingCategory.ROOM, 2.0))));

        when(student.getId()).thenReturn(studentId);
        when(lectureService.getById(lectureId)).thenReturn(lecture);
        when(ratingRepository.findByStudentIdAndLectureId(studentId, lectureId))
                .thenReturn(Optional.of(rating));

        RatingsAverageResponse response = ratingService.getOwnRating(student, lectureId);

        assertThat(response.ratings())
                .extracting(TopicAverageResponse::category)
                .containsExactly(
                        RatingCategory.ROOM,
                        RatingCategory.ORGANIZATION,
                        RatingCategory.WORKLOAD);
    }

    private static RatingTopic topic(Rating rating, RatingCategory category, double value) {
        RatingTopic topic = new RatingTopic();
        topic.setRating(rating);
        topic.setCategory(category);
        topic.setValue(value);
        return topic;
    }

    @Test
    void getOwnRatingWithNoRatingYetReturnsAnEmptyListRatherThanNull() {

        Student student = mock(Student.class);

        Lecture lecture = mock(Lecture.class);

        UUID studentId = UUID.randomUUID();

        UUID lectureId = UUID.randomUUID();


        when(student.getId())
                .thenReturn(studentId);

        when(lectureService.getById(lectureId))
                .thenReturn(lecture);

        when(ratingRepository
                .findByStudentIdAndLectureId(
                        studentId,
                        lectureId
                ))
                .thenReturn(Optional.empty());


        RatingsAverageResponse response =
                ratingService.getOwnRating(
                        student,
                        lectureId
                );


        assertThat(response.success()).isFalse();

        assertThat(response.message()).isEqualTo("Error, couldn't find rating for Lecture: "
                        + lectureId);

        // Empty, never null. This assertion read assertNull until the crash report -- a
        // lecture nobody has rated is exactly the case that reaches here, so the null list
        // went out on the ordinary path rather than an exotic one.
        assertThat(response.ratings()).isNotNull();

        assertThat(response.ratings().isEmpty()).isTrue();
    }


    @Test
    void getOwnRatingReturnsStudentTopics() {

        Student student = mock(Student.class);

        Lecture lecture = mock(Lecture.class);

        Rating rating = mock(Rating.class);

        UUID studentId = UUID.randomUUID();

        UUID lectureId = UUID.randomUUID();


        RatingTopic organization =
                topic(
                        RatingCategory.ORGANIZATION,
                        4.0
                );

        RatingTopic overall =
                topic(
                        RatingCategory.OVERALL,
                        4.0
                );


        when(student.getId())
                .thenReturn(studentId);

        when(lectureService.getById(lectureId))
                .thenReturn(lecture);

        when(ratingRepository
                .findByStudentIdAndLectureId(
                        studentId,
                        lectureId
                ))
                .thenReturn(
                        Optional.of(rating)
                );

        when(rating.getTopics())
                .thenReturn(
                        List.of(
                                organization,
                                overall
                        )
                );


        RatingsAverageResponse response =
                ratingService.getOwnRating(
                        student,
                        lectureId
                );


        assertThat(response.success()).isTrue();

        assertThat(response.ratings().size()).isEqualTo(2);

        assertThat(response.message()).isEqualTo("Success, found 2 ratings");
    }



    /**
     * F-15. {@code lectureService.getById} answers null rather than throwing, and the loop
     * that follows dereferences it -- so an unknown id was an NPE, and the catch-all turned
     * that into a 500 on a route an anonymous caller can reach with any random UUID.
     */
    @Test
    void getRatingCategoriesUnknownLectureIsNotFoundRatherThanAServerError() {

        UUID lectureId = UUID.randomUUID();

        when(lectureService.getById(lectureId))
                .thenReturn(null);

        ApiException thrown = catchThrowableOfType(
                ApiException.class,
                () -> ratingService.getRatingCategories(lectureId));
        assertThat(thrown).as("nothing was thrown").isNotNull();

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(thrown.getMessage()).isEqualTo("Lecture not found");
    }


    @Test
    void getRatingCategoriesReturnsCategoriesOfLectureType() {

        UUID lectureId = UUID.randomUUID();

        Lecture lecture = mock(Lecture.class);


        LectureType lectureType =
                Arrays.stream(
                                LectureType.values()
                        )
                        .filter(type ->
                                !type.getDefaultCategories()
                                        .isEmpty())
                        .findFirst()
                        .orElseThrow();


        when(lectureService.getById(lectureId))
                .thenReturn(lecture);

        when(lecture.getLectureType())
                .thenReturn(lectureType);


        RatingCategoriesResponse response = ratingService.getRatingCategories(lectureId);


        assertThat(response.success()).isTrue();

        assertThat(response.categories().size()).isEqualTo(lectureType
                        .getDefaultCategories()
                        .size());

        assertThat(response.message()).isEqualTo("Found "
                        + lectureType
                        .getDefaultCategories()
                        .size()
                        + " categories");
    }




    private RatingTopic topic(RatingCategory category, double value) {

        RatingTopic topic = new RatingTopic();

        topic.setCategory(category);
        topic.setValue(value);

        return topic;
    }
}
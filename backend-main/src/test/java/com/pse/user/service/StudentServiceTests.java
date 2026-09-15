package com.pse.user.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.user.dto.UserRatingResponse;
import com.pse.lecture.model.Lecture;
import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.model.RatingTopic;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.VoteType;
import com.pse.shared.error.ApiException;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerVote;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentVote;
import com.pse.user.dto.StudentProfileResponse;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The profile-facing half of the student service: it assembles a profile from an entity
 * graph and soft-deletes an account. Failure is never an exception here -- every path
 * returns a response object with {@code success = false}, which means a caller that only
 * checks for a thrown error sees a broken lookup as a successful one. So the assertions are
 * on the response shape as much as on the values.
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceTests {

    private static final UUID STUDENT_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AuditWriter auditWriter;

    private StudentService service() {
        return new StudentService(studentRepository, auditWriter);
    }

    private static Student student() {
        Student student = new Student();
        student.setId(STUDENT_ID);
        student.setUsername("ada");
        student.setKitEmail("ada@kit.edu");
        return student;
    }

    /** A comment carrying the given votes, attributed to the student. */
    private static Comment commentWithVotes(Student student, VoteType... votes) {
        Comment comment = new Comment();
        comment.setStudent(student);
        for (VoteType vote : votes) {
            CommentVote commentVote = new CommentVote();
            commentVote.setVote(vote);
            commentVote.setComment(comment);
            comment.getVotes().add(commentVote);
        }
        return comment;
    }

    private static Answer answerWithVotes(Student student, VoteType... votes) {
        Answer answer = new Answer();
        answer.setStudent(student);
        for (VoteType vote : votes) {
            AnswerVote answerVote = new AnswerVote();
            answerVote.setVote(vote);
            answerVote.setAnswer(answer);
            answer.getVotes().add(answerVote);
        }
        return answer;
    }

    /**
     * A rating on its own lecture with one topic, so the weighted average is a real number
     * rather than the zero an empty topic list returns.
     */
    /**
     * A lecture with the columns the schema declares {@code nullable = false} actually set.
     * {@code new Lecture()} leaves {@code semesterSeason} null, which no row in the database
     * can be, and a response builder that reads it is right to fail on one.
     */
    private static Lecture lecture() {
        Lecture lecture = new Lecture();
        lecture.setName("Algorithmen 1");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        return lecture;
    }

    private static Rating ratingScoring(double value) {
        Rating rating = new Rating();
        rating.setLecture(lecture());
        RatingTopic topic = new RatingTopic();
        topic.setCategory(RatingCategory.ORGANIZATION);
        topic.setValue(value);
        topic.setRating(rating);
        rating.getTopics().add(topic);
        return rating;
    }

    // ---------- getUserInformation ----------

    @Test
    void getUserInformation_commentAndAnswerVotes_scoresUpvotesMinusDownvotes() {
        // Given -- one up and one down on a comment, one up on an answer
        Student student = student();
        student.getComments().add(commentWithVotes(student, VoteType.UP, VoteType.DOWN));
        student.getAnswers().add(answerWithVotes(student, VoteType.UP));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        // When
        StudentProfileResponse response = service().getUserInformation(STUDENT_ID);

        // Then
        assertThat(response.credibilityScore()).isEqualTo(1);
    }

    @Test
    void getUserInformation_oneUpAndOneDownVote_scoresZero() {
        // Given
        Student student = student();
        student.getAnswers().add(answerWithVotes(student, VoteType.UP, VoteType.DOWN));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        // When
        StudentProfileResponse response = service().getUserInformation(STUDENT_ID);

        // Then
        assertThat(response.credibilityScore()).isZero();
    }

    /**
     * The score counts every vote on content the student wrote, whoever cast it -- which is
     * the point of a credibility score, and worth pinning so a later "exclude self-votes"
     * change has to be deliberate.
     */
    @Test
    void getUserInformation_votesCastByOthers_stillCountTowardsTheAuthorsScore() {
        // Given -- three upvotes on one comment, none of them the author's own
        Student student = student();
        student.getComments().add(
                commentWithVotes(student, VoteType.UP, VoteType.UP, VoteType.UP));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        // When
        StudentProfileResponse response = service().getUserInformation(STUDENT_ID);

        // Then
        assertThat(response.credibilityScore()).isEqualTo(3);
    }

    @Test
    void getUserInformation_studentWithRatingsAndComments_reportsTheAverageAndTheCounts() {
        // Given
        Student student = student();
        student.getRatings().add(ratingScoring(4.0));
        student.getRatings().add(ratingScoring(2.0));
        student.getComments().add(commentWithVotes(student));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        // When
        StudentProfileResponse response = service().getUserInformation(STUDENT_ID);

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Success");
        assertThat(response.username()).isEqualTo("ada");
        assertThat(response.averageRating()).isEqualTo(3.0);
        assertThat(response.ratingsCount()).isEqualTo(2);
        assertThat(response.commentsWritten()).isEqualTo(1);
        assertThat(response.profilePicture()).contains("seed=" + STUDENT_ID);
    }

    @Test
    void getUserInformation_studentWithNoRatings_reportsAZeroAverage() {
        // Given
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student()));

        // When
        StudentProfileResponse response = service().getUserInformation(STUDENT_ID);

        // Then -- the empty-collection guard, not a division by zero
        assertThat(response.averageRating()).isZero();
        assertThat(response.ratingsCount()).isZero();
    }

    /**
     * F-5. This used to answer HTTP 200 carrying {@code success = false} and nulls, with a
     * message that said "authenticate" although the only thing that happened was that no row
     * matched the id -- authentication had already succeeded in the security chain. Both
     * halves are corrected: a 404, and a message that says what actually failed.
     */
    @Test
    void getUserInformation_unknownStudent_isNotFoundRatherThanASuccessfulFailure() {
        // Given
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service().getUserInformation(STUDENT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("Student not found")
                .extracting(thrown -> ((ApiException) thrown).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * F-9. The score was written back onto the managed entity inside a method annotated
     * {@code @Transactional(readOnly = true)}, so whether it reached the database was up to
     * dirty checking rather than to anything in the code. It also aimed at a column an
     * administrator owns -- {@code ModerationUserService} can set {@code credibilityScore}
     * and {@code UserRevertHandler} can revert it -- so a student opening their own profile
     * could quietly undo that adjustment. The response still carries the computed value.
     */
    @Test
    void getUserInformation_derivedScore_isReportedWithoutBeingWrittenBackToTheEntity() {
        // Given -- an administrator has set the stored score to something else
        Student student = student();
        student.setCredibilityScore(42);
        student.getComments().add(commentWithVotes(student, VoteType.UP, VoteType.UP));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        // When
        StudentProfileResponse response = service().getUserInformation(STUDENT_ID);

        // Then
        assertThat(response.credibilityScore()).isEqualTo(2);
        assertThat(student.getCredibilityScore())
                .as("a read must not overwrite a column somebody else owns")
                .isEqualTo(42);
        verify(studentRepository, never()).save(any());
    }

    // ---------- getUserRatings ----------

    @Test
    void getUserRatings_studentWithRatings_mapsEachRatingToItsLectureAndOverallScore() {
        // Given
        Student student = student();
        student.getRatings().add(ratingScoring(4.0));
        student.getRatings().add(ratingScoring(1.0));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        // When
        UserRatingResponse response = service().getUserRatings(STUDENT_ID);

        // Then -- the count is in the message, so it is part of the contract
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Success, found 2 ratings.");
        assertThat(response.ratings()).hasSize(2);
        assertThat(response.ratings()).extracting("overallRating")
                .containsExactly(4.0, 1.0);
        assertThat(response.ratings().getFirst().lecture()).isNotNull();
    }

    @Test
    void getUserRatings_studentWithNoRatings_reportsZeroRatingsInTheMessage() {
        // Given
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student()));

        // When
        UserRatingResponse response = service().getUserRatings(STUDENT_ID);

        // Then -- a success with an empty list, not a failure
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Success, found 0 ratings.");
        assertThat(response.ratings()).isEmpty();
    }

    /** F-5, and the same misleading "authenticate" wording as above. */
    @Test
    void getUserRatings_unknownStudent_isNotFoundRatherThanAnEmptyList() {
        // Given
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service().getUserRatings(STUDENT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("Student not found")
                .extracting(thrown -> ((ApiException) thrown).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- deleteAccount ----------

    /**
     * The account is re-looked-up by email rather than by the id of the instance handed in,
     * so this is the path a test has to stub. The status write is the whole deletion: rows
     * stay, the account becomes unreachable.
     */
    @Test
    void deleteAccount_existingStudent_softDeletesAndNamesTheEmailInTheMessage() {
        // Given
        Student student = student();
        when(studentRepository.findByKitEmail("ada@kit.edu")).thenReturn(Optional.of(student));

        // When
        BasicResponse response = service().deleteAccount(student);

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Deleted Account successfully: ada@kit.edu");
        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.DELETED);
    }

    /**
     * F-5. The one arm that is not a 404: no principal at all is an authentication failure,
     * and 401 is what the rest of the API answers for it.
     */
    @Test
    void deleteAccount_nullStudent_isUnauthorizedRatherThanASuccessfulFailure() {
        // When / Then
        assertThatThrownBy(() -> service().deleteAccount(null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Not logged in")
                .extracting(thrown -> ((ApiException) thrown).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(studentRepository);
    }

    /**
     * An authenticated principal whose row is already gone. Nothing is written, and the
     * caller is told -- which is what stops {@code AccountService} revoking the tokens of
     * an account it did not manage to delete.
     */
    @Test
    void deleteAccount_emailNotInTheTable_isNotFoundWithoutWriting() {
        // Given
        when(studentRepository.findByKitEmail("ada@kit.edu")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service().deleteAccount(student()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Student not found")
                .extracting(thrown -> ((ApiException) thrown).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(studentRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }

    /**
     * The self-deletion is recorded, which it was not until F-46 was measured: the three
     * classes on this path held no {@code AuditWriter} at all, so an account could leave with
     * nothing anywhere saying it had.
     *
     * <p>{@code USER_SELF_DELETED} rather than {@code USER_DELETED} because the two are not the
     * same event -- the admin path anonymises the row in the same transaction and this one does
     * not, so the account keeps its real address, and the admin panel is told in writing that a
     * {@code DELETED} row with a real username has no {@code USER_DELETED} entry. The metadata
     * says so as a value rather than only in prose.
     *
     * <p>The label comes from {@code UserResponseMapper.label} and is asserted here in full,
     * because the panel recovers a deleted account's identity from {@code target_label} and this
     * assertion is what stops the format drifting away from the one that document promises.
     */
    @Test
    void deleteAccount_existingStudent_recordsTheDeletionAgainstTheAccountItself() {
        // Given
        Student student = student();
        when(studentRepository.findByKitEmail("ada@kit.edu")).thenReturn(Optional.of(student));

        // When
        service().deleteAccount(student);

        // Then
        verify(auditWriter).writeStudentAction(
                eq(student),
                eq(AuditAction.USER_SELF_DELETED),
                eq(AuditTargetType.USER),
                eq(STUDENT_ID),
                eq("ada (ada@kit.edu)"),
                eq(Map.of("anonymized", false))
        );
    }
}

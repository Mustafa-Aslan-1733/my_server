package com.pse.social.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.shared.enums.VoteType;
import com.pse.shared.error.ApiException;
import com.pse.social.dto.request.VoteAnswerRequest;
import com.pse.social.dto.request.VoteCommentRequest;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerVote;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentVote;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.CommentVoteRepository;
import com.pse.user.model.Student;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Voting used to be the one write path in {@code SocialService} that was neither
 * transactional nor audited. It is both now -- F-1 -- and these tests pin what it does,
 * including what it deliberately still does not do. The vote type is validated at the
 * boundary rather than here (F-2), which the two null-vote tests below spell out.
 *
 * <p>Scope is the two vote methods only, hence the file name; the submit and report paths
 * are a separate batch.
 */
@ExtendWith(MockitoExtension.class)
class VoteServiceTests {

    private static final UUID COMMENT_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final UUID ANSWER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private CommentVoteRepository commentVoteRepository;

    @Mock
    private AnswerVoteRepository answerVoteRepository;

    @Mock
    private AuditWriter auditWriter;

    private final Student student = new Student();
    private final Student author = new Student();
    private final Lecture lecture = new Lecture();
    private final Comment comment = new Comment();
    private final Answer answer = new Answer();

    /**
     * Both entities are wired to an author and a lecture because the columns behind them are
     * {@code @ManyToOne(optional = false)} -- a persisted row cannot have either missing, and
     * the audit label reads both. They are eagerly fetched by the {@code findById} the vote
     * methods already make, so reading them costs no extra query.
     */
    @BeforeEach
    void wireTheObjectGraph() {
        author.setUsername("ada");
        lecture.setCode("IN0001");
        lecture.setName("Algorithmen 1");
        comment.setStudent(author);
        comment.setLecture(lecture);
        answer.setStudent(author);
        answer.setComment(comment);
    }

    /**
     * There are no nulls here any more, and that is the point rather than a loss.
     *
     * <p>This used to construct a nine-argument {@code SocialService} with four of the
     * collaborators passed as {@code null} rather than mocked, so that a later edit reaching
     * for one of them failed immediately instead of passing against a lenient mock. The class
     * under test now has exactly the five collaborators the vote methods use, so the
     * structure says what the nulls were saying, and it says it to production code as well as
     * to this test.
     */
    private VoteService service() {
        return new VoteService(
                commentRepository,
                answerRepository,
                commentVoteRepository,
                answerVoteRepository,
                auditWriter
        );
    }

    // ---------- voteComment ----------

    @Test
    void voteComment_unknownComment_isNotFoundWithoutTouchingTheVoteTable() {
        // Given
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        // When / Then -- F-5: this was a 200 carrying success:false until it was corrected
        assertThatThrownBy(() ->
                service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.UP)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Comment not found")
                .extracting(thrown -> ((ApiException) thrown).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(commentVoteRepository);
    }

    @Test
    void voteComment_studentWhoHasNotVotedYet_createsAVoteWiredToTheStudentAndComment() {
        // Given
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByStudentAndComment(student, comment))
                .thenReturn(Optional.empty());

        // When
        BasicResponse response =
                service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.UP));

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Comment vote saved successfully");
        ArgumentCaptor<CommentVote> saved = ArgumentCaptor.forClass(CommentVote.class);
        verify(commentVoteRepository).save(saved.capture());
        assertThat(saved.getValue().getStudent()).isSameAs(student);
        assertThat(saved.getValue().getComment()).isSameAs(comment);
        assertThat(saved.getValue().getVote()).isEqualTo(VoteType.UP);
    }

    /**
     * The table has a unique constraint on (student, comment), so changing a vote has to
     * update the row rather than insert a second one -- otherwise the second vote fails at
     * flush with a constraint violation the caller never sees coming.
     */
    @Test
    void voteComment_studentWhoAlreadyVoted_mutatesTheExistingRowRatherThanInsertingASecond() {
        // Given
        CommentVote existing = new CommentVote();
        existing.setStudent(student);
        existing.setComment(comment);
        existing.setVote(VoteType.UP);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByStudentAndComment(student, comment))
                .thenReturn(Optional.of(existing));

        // When
        service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.DOWN));

        // Then
        ArgumentCaptor<CommentVote> saved = ArgumentCaptor.forClass(CommentVote.class);
        verify(commentVoteRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getVote()).isEqualTo(VoteType.DOWN);
    }

    /**
     * Deliberate, not a gap: voting up twice re-saves an up vote rather than clearing it.
     * F-3 added a way to withdraw a vote, and it is an explicit {@link VoteType#NONE} rather
     * than a toggle -- a toggle would have changed what an existing client's second identical
     * request does, and no client asked for that.
     */
    @Test
    void voteComment_repeatedIdenticalVote_isRewrittenRatherThanToggledOff() {
        // Given
        CommentVote existing = new CommentVote();
        existing.setStudent(student);
        existing.setComment(comment);
        existing.setVote(VoteType.UP);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByStudentAndComment(student, comment))
                .thenReturn(Optional.of(existing));

        // When
        BasicResponse response =
                service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.UP));

        // Then
        assertThat(response.success()).isTrue();
        verify(commentVoteRepository).save(existing);
        assertThat(existing.getVote()).isEqualTo(VoteType.UP);
    }

    // ---------- withdrawal (F-3) and the audit trail (F-1) ----------

    /**
     * F-3. {@code NONE} deletes the row rather than storing a third direction, which is why
     * {@code comment_votes.vote} can stay {@code nullable = false}. Before this there was no
     * way at all to take a vote back.
     */
    @Test
    void voteComment_withdrawal_deletesTheRowRatherThanStoringAThirdDirection() {
        // Given
        CommentVote existing = new CommentVote();
        existing.setStudent(student);
        existing.setComment(comment);
        existing.setVote(VoteType.UP);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByStudentAndComment(student, comment))
                .thenReturn(Optional.of(existing));

        // When
        BasicResponse response =
                service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.NONE));

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Comment vote withdrawn successfully");
        verify(commentVoteRepository).delete(existing);
        verify(commentVoteRepository, never()).save(any());
    }

    /** Withdrawing a vote that was never cast is not an error -- there is nothing to delete. */
    @Test
    void voteComment_withdrawalWithNoVoteOnRecord_succeedsWithoutTouchingTheRow() {
        // Given
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByStudentAndComment(student, comment))
                .thenReturn(Optional.empty());

        // When
        BasicResponse response =
                service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.NONE));

        // Then
        assertThat(response.success()).isTrue();
        verify(commentVoteRepository, never()).delete(any());
        verify(commentVoteRepository, never()).save(any());
    }

    @Test
    void voteAnswer_withdrawal_deletesTheRow() {
        // Given
        AnswerVote existing = new AnswerVote();
        existing.setStudent(student);
        existing.setAnswer(answer);
        existing.setVote(VoteType.DOWN);
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));
        when(answerVoteRepository.findByStudentAndAnswer(student, answer))
                .thenReturn(Optional.of(existing));

        // When
        BasicResponse response =
                service().voteAnswer(ANSWER_ID, student, new VoteAnswerRequest(VoteType.NONE));

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Answer vote withdrawn successfully");
        verify(answerVoteRepository).delete(existing);
        verify(answerVoteRepository, never()).save(any());
    }

    /**
     * F-1. Every other write in {@code SocialService} recorded itself and the two vote
     * methods did not, so an account flipping hundreds of votes left nothing in the
     * moderation trail. The direction travels as metadata rather than as a change set,
     * because a vote has no "before": a first vote and an overwrite are the same action
     * from the log's point of view.
     */
    @Test
    void voteComment_writesTheVoteToTheActivityLog() {
        // Given
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByStudentAndComment(student, comment))
                .thenReturn(Optional.empty());

        // When
        service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.UP));

        // Then
        verify(auditWriter).writeStudentAction(
                student,
                AuditAction.COMMENT_VOTED,
                AuditTargetType.COMMENT,
                comment.getId(),
                "Vote on ada in IN0001 — Algorithmen 1",
                Map.of("vote", "UP"));
    }

    @Test
    void voteAnswer_writesTheVoteToTheActivityLog() {
        // Given
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));
        when(answerVoteRepository.findByStudentAndAnswer(student, answer))
                .thenReturn(Optional.empty());

        // When
        service().voteAnswer(ANSWER_ID, student, new VoteAnswerRequest(VoteType.DOWN));

        // Then
        verify(auditWriter).writeStudentAction(
                student,
                AuditAction.ANSWER_VOTED,
                AuditTargetType.ANSWER,
                answer.getId(),
                "Vote on ada in IN0001 — Algorithmen 1",
                Map.of("vote", "DOWN"));
    }

    /** A withdrawal is an action too, and the one most worth seeing in a manipulation case. */
    @Test
    void voteComment_withdrawal_isRecordedAsItsOwnEntry() {
        // Given
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByStudentAndComment(student, comment))
                .thenReturn(Optional.empty());

        // When
        service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.NONE));

        // Then
        verify(auditWriter).writeStudentAction(
                student,
                AuditAction.COMMENT_VOTED,
                AuditTargetType.COMMENT,
                comment.getId(),
                "Vote on ada in IN0001 — Algorithmen 1",
                Map.of("vote", "NONE"));
    }

    /** A refused vote writes nothing: an entry for something that did not happen is worse. */
    @Test
    void voteComment_unknownComment_writesNoAuditEntry() {
        // Given
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() ->
                service().voteComment(COMMENT_ID, student, new VoteCommentRequest(VoteType.UP)))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(auditWriter);
    }

    /**
     * The service itself does not validate the vote type: a null still reaches
     * {@code setVote} and {@code save}. Rejection lives at the boundary instead --
     * {@code @NotNull} on {@link VoteCommentRequest} plus {@code @Valid} on the handler,
     * which is how every other validated endpoint in this codebase does it. So the
     * contract is "a null never gets this far", and the test that proves the 400 is
     * {@code SocialApiIntegrationTests.voteWithoutAVoteTypeIsRejectedAsABadRequest}.
     *
     * <p>Kept because it is the only thing standing between a future unvalidated caller
     * and a constraint violation at flush time. See F-2 in docs/test-findings.md.
     */
    @Test
    void voteComment_nullVoteType_reachesSaveBecauseValidationLivesAtTheBoundary() {
        // Given
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByStudentAndComment(student, comment))
                .thenReturn(Optional.empty());

        // When
        BasicResponse response =
                service().voteComment(COMMENT_ID, student, new VoteCommentRequest(null));

        // Then -- reported as a success
        assertThat(response.success()).isTrue();
        ArgumentCaptor<CommentVote> saved = ArgumentCaptor.forClass(CommentVote.class);
        verify(commentVoteRepository).save(saved.capture());
        assertThat(saved.getValue().getVote()).isNull();
    }

    // ---------- voteAnswer ----------

    @Test
    void voteAnswer_unknownAnswer_isNotFoundWithoutTouchingTheVoteTable() {
        // Given
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.empty());

        // When / Then -- the two paths word this the same way now; the comment path said
        // "cannot find comment" and this one "cannot find Answer", capital and all
        assertThatThrownBy(() ->
                service().voteAnswer(ANSWER_ID, student, new VoteAnswerRequest(VoteType.UP)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Answer not found")
                .extracting(thrown -> ((ApiException) thrown).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(answerVoteRepository);
    }

    /** The answer path has to reach the answer vote table, not the comment one. */
    @Test
    void voteAnswer_studentWhoHasNotVotedYet_createsAVoteWiredToTheStudentAndAnswer() {
        // Given
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));
        when(answerVoteRepository.findByStudentAndAnswer(student, answer))
                .thenReturn(Optional.empty());

        // When
        BasicResponse response =
                service().voteAnswer(ANSWER_ID, student, new VoteAnswerRequest(VoteType.DOWN));

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Answer vote saved successfully");
        ArgumentCaptor<AnswerVote> saved = ArgumentCaptor.forClass(AnswerVote.class);
        verify(answerVoteRepository).save(saved.capture());
        assertThat(saved.getValue().getStudent()).isSameAs(student);
        assertThat(saved.getValue().getAnswer()).isSameAs(answer);
        assertThat(saved.getValue().getVote()).isEqualTo(VoteType.DOWN);
        verify(commentVoteRepository, never()).save(any());
    }

    @Test
    void voteAnswer_studentWhoAlreadyVoted_mutatesTheExistingRowRatherThanInsertingASecond() {
        // Given
        AnswerVote existing = new AnswerVote();
        existing.setStudent(student);
        existing.setAnswer(answer);
        existing.setVote(VoteType.DOWN);
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));
        when(answerVoteRepository.findByStudentAndAnswer(student, answer))
                .thenReturn(Optional.of(existing));

        // When
        service().voteAnswer(ANSWER_ID, student, new VoteAnswerRequest(VoteType.UP));

        // Then
        ArgumentCaptor<AnswerVote> saved = ArgumentCaptor.forClass(AnswerVote.class);
        verify(answerVoteRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getVote()).isEqualTo(VoteType.UP);
    }

    /** Same arrangement as the comment path: {@code @NotNull} at the boundary, not here. */
    @Test
    void voteAnswer_nullVoteType_reachesSaveBecauseValidationLivesAtTheBoundary() {
        // Given
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));
        when(answerVoteRepository.findByStudentAndAnswer(student, answer))
                .thenReturn(Optional.empty());

        // When
        BasicResponse response =
                service().voteAnswer(ANSWER_ID, student, new VoteAnswerRequest(null));

        // Then
        assertThat(response.success()).isTrue();
        ArgumentCaptor<AnswerVote> saved = ArgumentCaptor.forClass(AnswerVote.class);
        verify(answerVoteRepository).save(saved.capture());
        assertThat(saved.getValue().getVote()).isNull();
    }

    // ------------------------------------------------------------------------------
    // Moved here from SocialServiceTests, which covered voting before this class
    // existed. Kept rather than folded into the cases above: they mock the entities
    // instead of building them, so they fail differently and are worth both.
    // ------------------------------------------------------------------------------



    @Test
    void voteCommentUnknownCommentIsNotFound() {

        UUID commentId = UUID.randomUUID();

        Student student = mock(Student.class);

        VoteCommentRequest request = mock(VoteCommentRequest.class);

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.empty());


        ApiException thrown = catchThrowableOfType(ApiException.class, () -> service().voteComment(
                        commentId,
                        student,
                        request
                ));
        assertThat(thrown).as("nothing was thrown").isNotNull();


        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(thrown.getMessage()).isEqualTo("Comment not found");

        verify(commentVoteRepository, never()).save(any());
    }



    @Test
    void voteCommentCreatesVoteWhenStudentHasNotVotedBefore() {

        UUID commentId = UUID.randomUUID();

        Student student = mock(Student.class);
        Comment comment = mock(Comment.class);

        VoteCommentRequest request = mock(VoteCommentRequest.class);

        when(request.voteType())
                .thenReturn(VoteType.UP);

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(commentVoteRepository
                .findByStudentAndComment(
                        student,
                        comment
                ))
                .thenReturn(Optional.empty());

        when(comment.getStudent())
                .thenReturn(mock(Student.class));

        when(comment.getLecture())
                .thenReturn(mock(Lecture.class));



        BasicResponse response =
                service().voteComment(
                        commentId,
                        student,
                        request
                );


        assertThat(response.success()).isTrue();


        ArgumentCaptor<CommentVote> captor =
                ArgumentCaptor.forClass(
                        CommentVote.class
                );

        verify(commentVoteRepository).save(captor.capture());


        CommentVote vote = captor.getValue();

        assertThat(vote.getStudent()).isSameAs(student);
        assertThat(vote.getComment()).isSameAs(comment);
        assertThat(vote.getVote()).isEqualTo(VoteType.UP);
    }



    @Test
    void voteCommentUpdatesExistingVote() {

        UUID commentId = UUID.randomUUID();

        Student student = mock(Student.class);
        Comment comment = mock(Comment.class);

        CommentVote existingVote = mock(CommentVote.class);

        VoteCommentRequest request = mock(VoteCommentRequest.class);

        when(request.voteType())
                .thenReturn(VoteType.DOWN);

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(commentVoteRepository
                .findByStudentAndComment(
                        student,
                        comment
                ))
                .thenReturn(
                        Optional.of(existingVote)
                );

        when(comment.getStudent())
                .thenReturn(mock(Student.class));

        when(comment.getLecture())
                .thenReturn(mock(Lecture.class));



        BasicResponse response =
                service().voteComment(
                        commentId,
                        student,
                        request
                );


        assertThat(response.success()).isTrue();

        verify(existingVote).setVote(VoteType.DOWN);

        verify(commentVoteRepository).save(existingVote);
    }



    @Test
    void voteAnswerUnknownAnswerIsNotFound() {

        UUID answerId = UUID.randomUUID();

        Student student = mock(Student.class);

        VoteAnswerRequest request = mock(VoteAnswerRequest.class);

        when(answerRepository.findById(answerId))
                .thenReturn(Optional.empty());


        ApiException thrown = catchThrowableOfType(ApiException.class, () -> service().voteAnswer(
                        answerId,
                        student,
                        request
                ));
        assertThat(thrown).as("nothing was thrown").isNotNull();


        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(thrown.getMessage()).isEqualTo("Answer not found");

        verify(answerVoteRepository, never()).save(any());
    }



    @Test
    void voteAnswerUpdatesExistingVote() {

        UUID answerId = UUID.randomUUID();

        Student student = mock(Student.class);
        Answer answer = mock(Answer.class);

        AnswerVote existingVote = mock(AnswerVote.class);

        VoteAnswerRequest request = mock(VoteAnswerRequest.class);

        when(request.voteType())
                .thenReturn(VoteType.DOWN);

        when(answerRepository.findById(answerId))
                .thenReturn(Optional.of(answer));

        when(answerVoteRepository
                .findByStudentAndAnswer(
                        student,
                        answer
                ))
                .thenReturn(
                        Optional.of(existingVote)
                );

        Comment votedComment = mock(Comment.class);

        when(votedComment.getLecture())
                .thenReturn(mock(Lecture.class));

        when(answer.getStudent())
                .thenReturn(mock(Student.class));

        when(answer.getComment())
                .thenReturn(votedComment);



        BasicResponse response =
                service().voteAnswer(
                        answerId,
                        student,
                        request
                );


        assertThat(response.success()).isTrue();

        verify(existingVote).setVote(VoteType.DOWN);

        verify(answerVoteRepository).save(existingVote);
    }
}

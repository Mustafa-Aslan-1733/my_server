package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import org.mockito.junit.jupiter.MockitoExtension;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.moderation.dto.request.ContentUpdateRequest;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.ContentStatus;
import com.pse.shared.error.ApiException;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.service.AnswerCleanup;
import com.pse.user.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Editing and removing a question or an answer, and what each edit leaves in the audit log.
 *
 * <p>Split out of {@code ModerationContentServiceTests} with the code it covers.
 */
@ExtendWith(MockitoExtension.class)
class ContentModerationServiceTests {

    private static final UUID COMMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID ANSWER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private CommentRepository commentRepository;
    @Mock private AnswerRepository answerRepository;
    @Mock private CommentReportRepository commentReportRepository;
    @Mock private AnswerReportRepository answerReportRepository;
    @Mock private AnswerCleanup answerCleanup;
    @Mock private WarningRepository warningRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private AuditWriter auditWriter;

    private ContentModerationService service() {
        return new ContentModerationService(
                commentRepository, answerRepository, commentReportRepository,
                answerReportRepository, answerCleanup, warningRepository,
                adminRepository, auditWriter);
    }


    // ------------------------------------------------------------------- fixtures

    private void givenComment(Comment comment) {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
    }


    private static ContentUpdateRequest visible() {
        return new ContentUpdateRequest(null, ContentStatus.VISIBLE);
    }


    private static Comment comment(String content) {
        Comment comment = new Comment();
        comment.setId(COMMENT_ID);
        comment.setContent(content);
        comment.setStatus(ContentStatus.VISIBLE);
        comment.setStudent(author());
        comment.setLecture(lecture());
        comment.setCreatedAt(LocalDateTime.now());
        return comment;
    }


    private static Answer answer(String content) {
        Answer answer = new Answer();
        answer.setId(ANSWER_ID);
        answer.setContent(content);
        answer.setStatus(ContentStatus.VISIBLE);
        answer.setStudent(author());
        answer.setComment(comment("Parent comment"));
        answer.setCreatedAt(LocalDateTime.now());
        return answer;
    }


    private static AuthenticatedUser principal() {
        return new AuthenticatedUser(null, null, new Admin());
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> changesWritten() {
        ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter).write(any(), any(), any(), any(), any(), changes.capture(), any());
        return changes.getValue();
    }


    private String labelWritten() {
        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).write(any(), any(), any(), any(), label.capture(), any(), any());
        return label.getValue();
    }


    @SuppressWarnings("unchecked")
    private static Object before(Map<String, Object> changes, String field) {
        return ((Map<String, Object>) changes.get(field)).get("before");
    }


    @SuppressWarnings("unchecked")
    private static Object after(Map<String, Object> changes, String field) {
        return ((Map<String, Object>) changes.get(field)).get("after");
    }


    private static Lecture lecture() {
        Lecture lecture = new Lecture();
        lecture.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        lecture.setName("Algorithmen 1");
        lecture.setCode("ALG1");
        return lecture;
    }


    private static Student author() {
        Student student = new Student();
        student.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        student.setUsername("author");
        student.setKitEmail("author@student.kit.edu");
        return student;
    }


    // ------------------------------------------------------------- the shared guard

    @Test
    void updateComment_unknownComment_isNotFound() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateComment(principal(), COMMENT_ID, visible()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Comment not found")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }


    @Test
    void updateAnswer_unknownAnswer_isNotFound() {
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateAnswer(principal(), ANSWER_ID, visible()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Answer not found");
    }


    @Test
    void updateComment_nullRequest_isRejectedAsNoUpdate() {
        givenComment(comment("Original"));

        assertThatThrownBy(() -> service().updateComment(principal(), COMMENT_ID, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied");

        verify(commentRepository, never()).save(any());
    }


    @Test
    void updateComment_bothFieldsNull_isRejectedAsNoUpdate() {
        givenComment(comment("Original"));

        assertThatThrownBy(() -> service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest(null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied");
    }


    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t "})
    void updateComment_contentThatIsBlankOnceTrimmed_isRejected(String content) {
        givenComment(comment("Original"));

        assertThatThrownBy(() -> service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest(content, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid content");
    }


    @Test
    void updateComment_contentLongerThanTheColumn_isRejected() {
        givenComment(comment("Original"));

        assertThatThrownBy(() -> service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest("x".repeat(10_001), null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid content");
    }


    @Test
    void updateComment_contentAtTheLengthLimit_isAccepted() {
        Comment comment = comment("Original");
        givenComment(comment);
        String content = "x".repeat(10_000);

        service().updateComment(principal(), COMMENT_ID, new ContentUpdateRequest(content, null));

        assertThat(comment.getContent()).isEqualTo(content);
    }


    @Test
    void updateComment_contentResubmittedUnchanged_writesNothing() {
        Comment comment = comment("Original");
        givenComment(comment);

        BasicResponse response = service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest("  Original  ", null));

        assertThat(response.success()).isTrue();
        verify(commentRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }


    @Test
    void updateComment_statusResubmittedUnchanged_writesNothing() {
        Comment comment = comment("Original");
        comment.setStatus(ContentStatus.VISIBLE);
        givenComment(comment);

        BasicResponse response = service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest(null, ContentStatus.VISIBLE));

        assertThat(response.success()).isTrue();
        verifyNoInteractions(auditWriter);
    }


    @Test
    void updateComment_hidingAComment_recordsTheStatusChangeAndSaves() {
        Comment comment = comment("Original");
        comment.setStatus(ContentStatus.VISIBLE);
        givenComment(comment);

        service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest(null, ContentStatus.HIDDEN));

        assertThat(comment.getStatus()).isEqualTo(ContentStatus.HIDDEN);
        assertThat(before(changesWritten(), "status")).isEqualTo("VISIBLE");
        assertThat(after(changesWritten(), "status")).isEqualTo("HIDDEN");
        verify(commentRepository).save(comment);
    }


    // ------------------------------------------------------------------ the preview

    /**
     * The audit entry records a preview, not the whole post. A comment can be 10 000
     * characters and the trail has to stay readable — but the truncation must be visible,
     * or a reader cannot tell a shortened value from the real one.
     */
    @Test
    void updateComment_longContent_isRecordedAsATruncatedPreviewWithAnEllipsis() {
        Comment comment = comment("x".repeat(500));
        givenComment(comment);

        service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest("y".repeat(500), null));

        String recordedBefore = (String) before(changesWritten(), "content");
        String recordedAfter = (String) after(changesWritten(), "content");

        assertThat(recordedBefore).hasSize(121).endsWith("…").startsWith("x");
        assertThat(recordedAfter).hasSize(121).endsWith("…").startsWith("y");
    }


    @Test
    void updateComment_contentAtThePreviewBoundary_isRecordedWhole() {
        Comment comment = comment("x".repeat(120));
        givenComment(comment);

        service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest("y".repeat(120), null));

        assertThat((String) after(changesWritten(), "content")).hasSize(120).doesNotContain("…");
    }


    // ------------------------------------------------------------------- the label

    @Test
    void updateComment_lectureWithACode_isLabelledCodeFirst() {
        Comment comment = comment("Original");
        comment.getLecture().setCode("ALG1");
        comment.getLecture().setName("Algorithmen 1");
        givenComment(comment);

        service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest(null, ContentStatus.HIDDEN));

        assertThat(labelWritten()).isEqualTo("Comment by author in ALG1 — Algorithmen 1");
    }


    /**
     * Both halves of the code guard, because a lecture entered without one is common enough
     * that a label reading "null — Algorithmen 1" would reach the panel.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void updateComment_lectureWithABlankCode_isLabelledByNameAlone(String code) {
        Comment comment = comment("Original");
        comment.getLecture().setCode(code);
        comment.getLecture().setName("Algorithmen 1");
        givenComment(comment);

        service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest(null, ContentStatus.HIDDEN));

        assertThat(labelWritten()).isEqualTo("Comment by author in Algorithmen 1");
    }


    @Test
    void updateComment_lectureWithNoCodeAtAll_isLabelledByNameAlone() {
        Comment comment = comment("Original");
        comment.getLecture().setCode(null);
        comment.getLecture().setName("Algorithmen 1");
        givenComment(comment);

        service().updateComment(principal(), COMMENT_ID,
                new ContentUpdateRequest(null, ContentStatus.HIDDEN));

        assertThat(labelWritten()).isEqualTo("Comment by author in Algorithmen 1");
    }


    // -------------------------------------------------------------- the answer side

    /**
     * Where the two methods genuinely differ: the action, the target type, and the route to
     * the lecture, which on the answer side runs through the parent comment. A copy-paste
     * that left the comment repository or the comment action in place would pass every test
     * above and fail this one.
     */
    @Test
    void updateAnswer_hidingAnAnswer_isAuditedAsAnAnswerUpdateLabelledThroughItsComment() {
        Answer answer = answer("Original");
        answer.setStatus(ContentStatus.VISIBLE);
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));

        service().updateAnswer(principal(), ANSWER_ID,
                new ContentUpdateRequest(null, ContentStatus.HIDDEN));

        assertThat(answer.getStatus()).isEqualTo(ContentStatus.HIDDEN);
        verify(answerRepository).save(answer);
        verify(commentRepository, never()).save(any());

        ArgumentCaptor<AuditAction> action = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditWriter).write(any(), action.capture(), any(), any(), any(), any(), any());
        assertThat(action.getValue()).isEqualTo(AuditAction.ANSWER_UPDATED);
        assertThat(labelWritten()).isEqualTo("Answer by author in ALG1 — Algorithmen 1");
    }


    @Test
    void updateAnswer_contentResubmittedUnchanged_writesNothing() {
        Answer answer = answer("Original");
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));

        BasicResponse response = service().updateAnswer(principal(), ANSWER_ID,
                new ContentUpdateRequest("Original", null));

        assertThat(response.success()).isTrue();
        verify(answerRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }
}

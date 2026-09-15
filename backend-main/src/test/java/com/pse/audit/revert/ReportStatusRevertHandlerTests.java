package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.moderation.service.ModerationAnswerReportService;
import com.pse.moderation.service.ModerationCommentService;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.ReportStatus;
import com.pse.shared.enums.ContentStatus;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerReport;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentReport;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.CommentReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Comment reports and answer reports are separate target types with one field between them,
 * so one source file supplies both handlers. With this little logic per handler the failure
 * worth catching is the copy-paste one: a handler wired to the other type's repository or
 * update service, which would silently revert the wrong report -- or nothing at all.
 */
@ExtendWith(MockitoExtension.class)
class ReportStatusRevertHandlerTests {

    private static final UUID FIRST = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final UUID SECOND = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(null, null, null);

    @Mock
    private CommentReportRepository commentReportRepository;

    @Mock
    private AnswerReportRepository answerReportRepository;

    @Mock
    private ModerationCommentService commentService;

    @Mock
    private ModerationAnswerReportService answerReportService;

    private ReportStatusRevertHandler.CommentReportHandler commentReportHandler() {
        return new ReportStatusRevertHandler.CommentReportHandler(
                commentReportRepository, commentService);
    }

    private ReportStatusRevertHandler.AnswerReportHandler answerReportHandler() {
        return new ReportStatusRevertHandler.AnswerReportHandler(
                answerReportRepository, answerReportService);
    }

    private static CommentReport commentReport(UUID id) {
        CommentReport report = new CommentReport();
        report.setId(id);
        report.setStatus(ReportStatus.ACTION_TAKEN);
        Comment comment = new Comment();
        comment.setStatus(ContentStatus.HIDDEN);
        report.setComment(comment);
        return report;
    }

    private static AnswerReport answerReport(UUID id) {
        AnswerReport report = new AnswerReport();
        report.setId(id);
        report.setStatus(ReportStatus.ACTION_TAKEN);
        Answer answer = new Answer();
        answer.setStatus(ContentStatus.HIDDEN);
        report.setAnswer(answer);
        return report;
    }

    private static Map<String, Object> before(Object status) {
        Map<String, Object> before = new HashMap<>();
        before.put("status", status);
        return before;
    }

    // ---------- CommentReportHandler ----------

    @Test
    void commentReportHandler_targetType_isCommentReport() {
        // When / Then
        assertThat(commentReportHandler().targetType())
                .isEqualTo(AuditTargetType.COMMENT_REPORT);
    }

    /**
     * This read {@code containsExactly(entry("status", ...))} and pinned the defect: the map
     * offered one key while {@code ModerationCommentService} records two, so every
     * {@code ACTION_TAKEN} transition came back {@code ACTION_NOT_REVERTIBLE}. Inverted
     * rather than deleted when it was fixed.
     */
    @Test
    void commentReportHandler_currentValues_mapsTheStatusAndTheCommentVisibility() {
        // Given
        when(commentReportRepository.findAllById(List.of(FIRST)))
                .thenReturn(List.of(commentReport(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values =
                commentReportHandler().currentValues(List.of(FIRST));

        // Then -- both enums by name, because that is how they survived the JSON column
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(
                entry("status", "ACTION_TAKEN"), entry("commentStatus", "HIDDEN"));
    }

    /** An id with no surviving row is how {@code TARGET_MISSING} is detected upstream. */
    @Test
    void commentReportHandler_currentValues_deletedReport_isAbsentFromTheResult() {
        // Given
        when(commentReportRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(commentReport(SECOND)));

        // When
        Map<UUID, Map<String, Object>> values =
                commentReportHandler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(SECOND);
    }

    @Test
    void commentReportHandler_currentValues_noTargets_returnsAnEmptyMap() {
        // Given
        when(commentReportRepository.findAllById(List.of())).thenReturn(List.of());

        // When / Then
        assertThat(commentReportHandler().currentValues(List.of())).isEmpty();
        verifyNoInteractions(commentService);
    }

    /**
     * Putting a report back to {@code OPEN} is the revert that matters: {@code ACTION_TAKEN}
     * hid the comment the report is about, and this is the path that makes it visible again.
     */
    @Test
    void commentReportHandler_applyInverse_replaysTheStatusThroughTheCommentService() {
        // When
        commentReportHandler().applyInverse(PRINCIPAL, FIRST, before("OPEN"));

        // Then
        verify(commentService).updateReportStatus(PRINCIPAL, FIRST, ReportStatus.OPEN);
        verifyNoInteractions(answerReportService);
    }

    @Test
    void commentReportHandler_applyInverse_beforeHalfWithoutTheStatus_passesNull() {
        // When
        commentReportHandler().applyInverse(PRINCIPAL, FIRST, new HashMap<>());

        // Then -- null reads as "leave alone" to the update service
        verify(commentService).updateReportStatus(PRINCIPAL, FIRST, null);
    }

    // ---------- AnswerReportHandler ----------

    @Test
    void answerReportHandler_targetType_isAnswerReport() {
        // When / Then
        assertThat(answerReportHandler().targetType())
                .isEqualTo(AuditTargetType.ANSWER_REPORT);
    }

    @Test
    void answerReportHandler_currentValues_mapsTheStatusAndTheAnswerVisibility() {
        // Given
        when(answerReportRepository.findAllById(List.of(FIRST)))
                .thenReturn(List.of(answerReport(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values =
                answerReportHandler().currentValues(List.of(FIRST));

        // Then -- the answer-side mirror; inverted with its comment-side twin
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(
                entry("status", "ACTION_TAKEN"), entry("answerStatus", "HIDDEN"));
    }

    @Test
    void answerReportHandler_currentValues_deletedReport_isAbsentFromTheResult() {
        // Given
        when(answerReportRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(answerReport(SECOND)));

        // When
        Map<UUID, Map<String, Object>> values =
                answerReportHandler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(SECOND);
    }

    /** The answer handler has to reach the answer report service, not the comment one. */
    @Test
    void answerReportHandler_applyInverse_replaysTheStatusThroughTheAnswerReportService() {
        // When
        answerReportHandler().applyInverse(PRINCIPAL, FIRST, before("DISMISSED"));

        // Then
        verify(answerReportService).updateReportStatus(PRINCIPAL, FIRST, ReportStatus.DISMISSED);
        verifyNoInteractions(commentService);
    }
}

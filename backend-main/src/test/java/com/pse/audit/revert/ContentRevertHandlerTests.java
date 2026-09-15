package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.moderation.dto.request.ContentUpdateRequest;
import com.pse.moderation.service.ContentModerationService;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.ContentStatus;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comments and answers are separate target types with an identical field set, so one source
 * file supplies both handlers. The pair is tested together for the same reason -- and
 * because the failure worth catching is the copy-paste one, a handler wired to the other
 * type's repository or update method.
 */
@ExtendWith(MockitoExtension.class)
class ContentRevertHandlerTests {

    private static final UUID FIRST = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final UUID SECOND = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(null, null, null);

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private ContentModerationService contentService;

    private ContentRevertHandler.CommentHandler commentHandler() {
        return new ContentRevertHandler.CommentHandler(commentRepository, contentService);
    }

    private ContentRevertHandler.AnswerHandler answerHandler() {
        return new ContentRevertHandler.AnswerHandler(answerRepository, contentService);
    }

    private static Comment comment(UUID id) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setContent("Edited by a moderator");
        comment.setStatus(ContentStatus.HIDDEN);
        return comment;
    }

    private static Answer answer(UUID id) {
        Answer answer = new Answer();
        answer.setId(id);
        answer.setContent("Edited by a moderator");
        answer.setStatus(ContentStatus.HIDDEN);
        return answer;
    }

    private static Map<String, Object> before(String content, Object status) {
        Map<String, Object> before = new HashMap<>();
        before.put("content", content);
        before.put("status", status);
        return before;
    }

    // ---------- CommentHandler ----------

    @Test
    void commentHandler_targetType_isComment() {
        // When / Then
        assertThat(commentHandler().targetType()).isEqualTo(AuditTargetType.COMMENT);
    }

    @Test
    void commentHandler_currentValues_mapsContentAndStatus() {
        // Given
        when(commentRepository.findAllById(List.of(FIRST))).thenReturn(List.of(comment(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = commentHandler().currentValues(List.of(FIRST));

        // Then
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(
                entry("content", "Edited by a moderator"),
                entry("status", "HIDDEN")
        );
    }

    @Test
    void commentHandler_currentValues_deletedComment_isAbsentFromTheResult() {
        // Given -- a comment removed since the entry was written comes back from no query
        when(commentRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(comment(SECOND)));

        // When
        Map<UUID, Map<String, Object>> values =
                commentHandler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(SECOND);
    }

    @Test
    void commentHandler_applyInverse_replaysContentAndStatusThroughTheContentService() {
        // Given
        Map<String, Object> before = before("Original text", "VISIBLE");

        // When
        commentHandler().applyInverse(PRINCIPAL, FIRST, before);

        // Then
        ArgumentCaptor<ContentUpdateRequest> request =
                ArgumentCaptor.forClass(ContentUpdateRequest.class);
        verify(contentService).updateComment(eq(PRINCIPAL), eq(FIRST), request.capture());
        assertThat(request.getValue().content()).isEqualTo("Original text");
        assertThat(request.getValue().status()).isEqualTo(ContentStatus.VISIBLE);
    }

    /**
     * Putting a hidden comment back is the revert that matters most here: a status set by
     * mistake takes a post out of the app, and the entry's before half is the only record
     * of what it used to be.
     */
    @Test
    void commentHandler_applyInverse_statusOnly_leavesContentNull() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("status", "VISIBLE");

        // When
        commentHandler().applyInverse(PRINCIPAL, FIRST, before);

        // Then
        ArgumentCaptor<ContentUpdateRequest> request =
                ArgumentCaptor.forClass(ContentUpdateRequest.class);
        verify(contentService).updateComment(eq(PRINCIPAL), eq(FIRST), request.capture());
        assertThat(request.getValue().content()).isNull();
        assertThat(request.getValue().status()).isEqualTo(ContentStatus.VISIBLE);
    }

    // ---------- AnswerHandler ----------

    @Test
    void answerHandler_targetType_isAnswer() {
        // When / Then
        assertThat(answerHandler().targetType()).isEqualTo(AuditTargetType.ANSWER);
    }

    @Test
    void answerHandler_currentValues_mapsContentAndStatus() {
        // Given
        when(answerRepository.findAllById(List.of(FIRST))).thenReturn(List.of(answer(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = answerHandler().currentValues(List.of(FIRST));

        // Then
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(
                entry("content", "Edited by a moderator"),
                entry("status", "HIDDEN")
        );
    }

    @Test
    void answerHandler_currentValues_deletedAnswer_isAbsentFromTheResult() {
        // Given
        when(answerRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(answer(SECOND)));

        // When
        Map<UUID, Map<String, Object>> values =
                answerHandler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(SECOND);
    }

    /** The answer handler has to reach updateAnswer, not updateComment. */
    @Test
    void answerHandler_applyInverse_replaysThroughUpdateAnswer() {
        // Given
        Map<String, Object> before = before("Original text", "VISIBLE");

        // When
        answerHandler().applyInverse(PRINCIPAL, FIRST, before);

        // Then
        ArgumentCaptor<ContentUpdateRequest> request =
                ArgumentCaptor.forClass(ContentUpdateRequest.class);
        verify(contentService).updateAnswer(eq(PRINCIPAL), eq(FIRST), request.capture());
        assertThat(request.getValue().content()).isEqualTo("Original text");
        assertThat(request.getValue().status()).isEqualTo(ContentStatus.VISIBLE);
    }
}

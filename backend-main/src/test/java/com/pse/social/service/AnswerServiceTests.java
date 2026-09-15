package com.pse.social.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.shared.enums.NotificationType;
import com.pse.social.dto.request.*;
import com.pse.social.model.*;
import com.pse.social.repository.*;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)class AnswerServiceTests {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuditWriter auditWriter;

    private AnswerService answerService;

    @BeforeEach
    void setUp() {
        answerService = new AnswerService(commentRepository, answerRepository, notificationRepository, auditWriter);
    }




    @Test
    void submitAnswerUnknownCommentIsNotFound() {
        // The lookup now fails before the request's other fields are read, so stubbing
        // them here would be an unnecessary stubbing rather than a described behaviour.

        Student student = mock(Student.class);

        AnswerSubmitRequest request = mock(AnswerSubmitRequest.class);

        UUID commentId = UUID.randomUUID();

        when(request.commentID())
                .thenReturn(commentId.toString());


        when(commentRepository.findById(commentId))
                .thenReturn(Optional.empty());


        ApiException thrown = catchThrowableOfType(ApiException.class, () -> answerService.submitAnswer(
                        student,
                        request
                ));
        assertThat(thrown).as("nothing was thrown").isNotNull();


        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(thrown.getMessage()).isEqualTo("Comment not found");

        verify(answerRepository, never()).save(any());

        verify(notificationRepository, never()).save(any());
    }



    @Test
    void submitAnswerCreatesAnswerAndNotification() {

        Student answerAuthor = mock(Student.class);
        Student commentAuthor = mock(Student.class);

        Comment comment = mock(Comment.class);
        Lecture lecture = mock(Lecture.class);

        AnswerSubmitRequest request = mock(AnswerSubmitRequest.class);

        UUID commentId = UUID.randomUUID();

        when(request.commentID())
                .thenReturn(commentId.toString());

        when(request.content())
                .thenReturn("Helpful answer");

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(comment.getStudent())
                .thenReturn(commentAuthor);

        when(comment.getLecture())
                .thenReturn(lecture);

        when(commentAuthor.getUsername())
                .thenReturn("commentAuthor");

        when(lecture.getCode())
                .thenReturn("LA1");

        when(lecture.getName())
                .thenReturn("Lineare Algebra 1");


        BasicResponse response =
                answerService.submitAnswer(
                        answerAuthor,
                        request
                );


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Answer submitted successfully");


        ArgumentCaptor<Answer> answerCaptor =
                ArgumentCaptor.forClass(Answer.class);

        verify(answerRepository)
                .save(answerCaptor.capture());

        Answer answer =
                answerCaptor.getValue();

        assertThat(answer.getStudent()).isSameAs(answerAuthor);

        assertThat(answer.getComment()).isSameAs(comment);

        assertThat(answer.getContent()).isEqualTo("Helpful answer");


        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(
                        Notification.class
                );

        verify(notificationRepository)
                .save(notificationCaptor.capture());


        Notification notification =
                notificationCaptor.getValue();

        assertThat(notification.getRecipient()).isSameAs(commentAuthor);

        assertThat(notification.getLecture()).isSameAs(lecture);

        assertThat(notification.getOwnComment()).isSameAs(comment);

        assertThat(notification.getAnswer()).isSameAs(answer);

        assertThat(notification.getType()).isEqualTo(NotificationType.ANSWER);


        verify(auditWriter)
                .writeStudentAction(
                        eq(answerAuthor),
                        eq(AuditAction.ANSWER_CREATED),
                        eq(AuditTargetType.ANSWER),
                        nullable(UUID.class),
                        eq(
                                "Answer to commentAuthor "
                                        + "in LA1 — Lineare Algebra 1"
                        ),
                        anyMap()
                );
    }
}

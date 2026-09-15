package com.pse.social.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.shared.enums.ReportReason;
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

@ExtendWith(MockitoExtension.class)class ContentReportServiceTests {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private CommentReportRepository commentReportRepository;

    @Mock
    private AnswerReportRepository answerReportRepository;

    @Mock
    private AuditWriter auditWriter;

    private ContentReportService contentReportService;

    @BeforeEach
    void setUp() {
        contentReportService = new ContentReportService(commentRepository, answerRepository,
                commentReportRepository, answerReportRepository, auditWriter);
    }


    @Test
    void submitCommentReportUnknownCommentIsNotFound() {
        // The lookup now fails before the request's other fields are read, so stubbing
        // them here would be an unnecessary stubbing rather than a described behaviour.

        Student student = mock(Student.class);

        CommentReportRequest request = mock(CommentReportRequest.class);

        UUID commentId = UUID.randomUUID();

        when(request.commentID())
                .thenReturn(commentId.toString());

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.empty());


        ApiException thrown = catchThrowableOfType(ApiException.class, () -> contentReportService.submitCommentReport(
                        student,
                        request
                ));
        assertThat(thrown).as("nothing was thrown").isNotNull();


        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(commentReportRepository, never()).save(any());
    }


    @Test
    void submitCommentReportSavesReport() {

        Student reporter = mock(Student.class);
        Student author = mock(Student.class);

        Comment comment = mock(Comment.class);
        Lecture lecture = mock(Lecture.class);

        CommentReportRequest request = mock(CommentReportRequest.class);

        UUID commentId = UUID.randomUUID();

        when(request.commentID())
                .thenReturn(commentId.toString());

        when(request.reportReason())
                .thenReturn(ReportReason.HARASSMENT);

        when(request.explanation())
                .thenReturn("Rude");

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(comment.getStudent())
                .thenReturn(author);

        when(author.getUsername())
                .thenReturn("author");

        when(comment.getLecture())
                .thenReturn(lecture);

        when(lecture.getCode())
                .thenReturn("LA1");

        when(lecture.getName())
                .thenReturn("Lineare Algebra 1");


        BasicResponse response =
                contentReportService.submitCommentReport(
                        reporter,
                        request
                );


        assertThat(response.success()).isTrue();


        ArgumentCaptor<CommentReport> captor =
                ArgumentCaptor.forClass(
                        CommentReport.class
                );

        verify(commentReportRepository).save(captor.capture());


        CommentReport report =
                captor.getValue();

        assertThat(report.getReporter()).isSameAs(reporter);
        assertThat(report.getComment()).isSameAs(comment);

        assertThat(report.getReason()).isEqualTo(ReportReason.HARASSMENT);

        assertThat(report.getExplanation()).isEqualTo("Rude");


        verify(auditWriter)
                .writeStudentAction(
                        eq(reporter),
                        eq(AuditAction.COMMENT_REPORT_CREATED),
                        eq(AuditTargetType.COMMENT_REPORT),
                        nullable(UUID.class),
                        eq(
                                "Report about author "
                                        + "in LA1 — Lineare Algebra 1"
                        ),
                        anyMap()
                );
    }


    @Test
    void submitAnswerReportUnknownAnswerIsNotFound() {
        // The lookup now fails before the request's other fields are read, so stubbing
        // them here would be an unnecessary stubbing rather than a described behaviour.

        Student student = mock(Student.class);

        AnswerReportRequest request = mock(AnswerReportRequest.class);

        UUID answerId = UUID.randomUUID();

        when(request.answerID())
                .thenReturn(answerId.toString());

        when(answerRepository.findById(answerId))
                .thenReturn(Optional.empty());


        ApiException thrown = catchThrowableOfType(ApiException.class, () -> contentReportService.submitAnswerReport(
                        student,
                        request
                ));
        assertThat(thrown).as("nothing was thrown").isNotNull();


        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(answerReportRepository, never()).save(any());
    }
}

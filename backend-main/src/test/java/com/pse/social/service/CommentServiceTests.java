package com.pse.social.service;

import com.pse.social.mapper.SocialResponseMapper;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.service.LectureService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)class CommentServiceTests {

    @Mock
    private LectureService lectureService;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AuditWriter auditWriter;

    @Mock
    private SocialResponseMapper mapper;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(lectureService, commentRepository, mapper, auditWriter);
    }




    @Test
    void submitCommentUnknownLectureIsNotFound() {
        // The lookup now fails before the request's other fields are read, so stubbing
        // them here would be an unnecessary stubbing rather than a described behaviour.

        Student student = mock(Student.class);

        CommentSubmitRequest request = mock(CommentSubmitRequest.class);

        when(request.lectureID()).thenReturn(
                UUID.randomUUID().toString()
        );


        when(lectureService.getById(
                UUID.fromString(request.lectureID())
        )).thenReturn(null);


        ApiException thrown = catchThrowableOfType(ApiException.class, () -> commentService.submitComment(
                        student,
                        request
                ));
        assertThat(thrown).as("nothing was thrown").isNotNull();


        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(thrown.getMessage()).isEqualTo("Lecture not found");

        verify(commentRepository, never()).save(any());

        verifyNoInteractions(auditWriter);
    }

    /**
     * Characterization of F-47, the comment half of the second consequence. <b>Current
     * behaviour, not intended behaviour</b> -- see items 37 and 38 in {@code docs/TODO.md}, and
     * {@code RatingServiceTests.submitRatingIsAcceptedForADeactivatedLecture}, which pins the
     * same rule on the other write path.
     *
     * <p>{@code submitComment} resolves the lecture through {@code LectureService.getById}
     * and checks only that it exists, so a deactivated lecture keeps collecting comments
     * while being invisible in the catalogue.
     *
     * <p>The {@code lenient()} is the finding: the service never asks. Invert this test when
     * the rule arrives; do not delete it.
     */
    @Test
    void submitCommentIsAcceptedForADeactivatedLecture() {

        Student student = mock(Student.class);
        Lecture deactivated = mock(Lecture.class);
        CommentSubmitRequest request = mock(CommentSubmitRequest.class);

        UUID lectureId = UUID.randomUUID();

        lenient().when(deactivated.isActive()).thenReturn(false);

        when(request.lectureID()).thenReturn(lectureId.toString());
        when(request.content()).thenReturn("still reachable");
        when(lectureService.getById(lectureId)).thenReturn(deactivated);
        lenient().when(deactivated.getCode()).thenReturn("ALT");
        lenient().when(deactivated.getName()).thenReturn("Alte Vorlesung");

        BasicResponse response = commentService.submitComment(student, request);

        assertThat(response.success()).isTrue();

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getLecture()).isSameAs(deactivated);

        verify(deactivated, never()).isActive();
    }




    @Test
    void submitCommentSavesCommentAndWritesAudit() {

        Student student = mock(Student.class);

        Lecture lecture = mock(Lecture.class);

        CommentSubmitRequest request = mock(CommentSubmitRequest.class);

        String lectureId = UUID.randomUUID().toString();

        when(request.lectureID())
                .thenReturn(lectureId);

        when(request.content())
                .thenReturn("Great lecture");

        when(lectureService.getById(UUID.fromString(lectureId)))
                .thenReturn(lecture);

        when(lecture.getCode()).thenReturn("CS101");
        when(lecture.getName()).thenReturn("Algorithms");


        BasicResponse response =
                commentService.submitComment(
                        student,
                        request
                );


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Comment submitted successfully");


        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);

        verify(commentRepository).save(captor.capture());

        Comment saved = captor.getValue();

        assertThat(saved.getStudent()).isSameAs(student);
        assertThat(saved.getLecture()).isSameAs(lecture);
        assertThat(saved.getContent()).isEqualTo("Great lecture");


        verify(auditWriter)
                .writeStudentAction(
                        eq(student),
                        eq(AuditAction.COMMENT_CREATED),
                        eq(AuditTargetType.COMMENT),
                        nullable(UUID.class),
                        eq("CS101 — Algorithms"),
                        anyMap()
                );
    }
}

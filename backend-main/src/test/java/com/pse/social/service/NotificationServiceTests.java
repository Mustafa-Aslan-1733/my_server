package com.pse.social.service;

import com.pse.lecture.model.Lecture;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.NotificationType;
import com.pse.shared.enums.UserStatus;
import com.pse.social.dto.response.NotificationsResponse;
import com.pse.social.model.*;
import com.pse.social.repository.*;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)class NotificationServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void getNotificationsReturnsDeletedUserForAnswerNotification() {

        Student recipient = mock(Student.class);

        Student deletedAuthor = new Student();
        deletedAuthor.setId(UUID.randomUUID());
        deletedAuthor.setUsername("oldName");
        deletedAuthor.setStatus(UserStatus.DELETED);

        Answer answer = new Answer();
        answer.setId(UUID.randomUUID());
        answer.setStudent(deletedAuthor);
        answer.setContent("Some answer");

        Lecture lecture = new Lecture();
        lecture.setId(UUID.randomUUID());
        lecture.setName("Algorithmen 1");
        lecture.setCode("ALG1");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setProfessors(new HashSet<>());
        lecture.setComments(new ArrayList<>());
        lecture.setRatings(new ArrayList<>());

        Comment ownComment = new Comment();
        ownComment.setId(UUID.randomUUID());

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setType(NotificationType.ANSWER);
        notification.setAnswer(answer);
        notification.setLecture(lecture);
        notification.setOwnComment(ownComment);
        notification.setCreatedAt(LocalDateTime.now());

        when(notificationRepository
                .findAllByRecipientAndSeenFalseOrderByCreatedAtDesc(recipient))
                .thenReturn(List.of(notification));

        NotificationsResponse response =
                notificationService.getNotifications(recipient);

        assertThat(response.success()).isTrue();
        assertThat(response.notifications()).hasSize(1);

        var notificationResponse = response.notifications().getFirst();

        assertThat(notificationResponse.type())
                .isEqualTo(NotificationType.ANSWER);

        assertThat(notificationResponse.userName())
                .isEqualTo("Deleted User");

        assertThat(notificationResponse.content())
                .isEqualTo("Some answer");

        assertThat(notificationResponse.ownCommentID())
                .isEqualTo(ownComment.getId());
    }

    @Test
    void getNotificationsReturnsWarningNotification() {

        Student student = mock(Student.class);

        Notification notification = mock(Notification.class);

        UUID notificationId = UUID.randomUUID();

        LocalDateTime createdAt = LocalDateTime.now();

        when(notificationRepository
                .findAllByRecipientAndSeenFalseOrderByCreatedAtDesc(
                        student
                ))
                .thenReturn(List.of(notification));

        when(notification.getId())
                .thenReturn(notificationId);

        when(notification.getType())
                .thenReturn(NotificationType.WARNING);

        when(notification.getMessage())
                .thenReturn("Please behave");

        when(notification.getCreatedAt())
                .thenReturn(createdAt);


        NotificationsResponse response = notificationService.getNotifications(student);


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Found: 1 Notifications");

        assertThat(response.notifications().size()).isEqualTo(1);

        assertThat(response.notifications()
                        .get(0)
                        .type()).isEqualTo(NotificationType.WARNING);

        assertThat(response.notifications()
                        .get(0)
                        .message()).isEqualTo("Please behave");
    }



    @Test
    void markNotificationAsSeenForeignNotificationIsNotFound() {

        Student student = mock(Student.class);

        UUID notificationId = UUID.randomUUID();

        when(notificationRepository
                .findByRecipientAndId(
                        student,
                        notificationId
                ))
                .thenReturn(Optional.empty());


        ApiException thrown = catchThrowableOfType(ApiException.class, () -> notificationService.markNotificationAsSeen(
                        student,
                        notificationId
                ));
        assertThat(thrown).as("nothing was thrown").isNotNull();


        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(thrown.getMessage()).isEqualTo("Notification not found");
    }



    @Test
    void markNotificationAsSeenMarksNotification() {

        Student student = mock(Student.class);

        Notification notification = mock(Notification.class);

        UUID notificationId = UUID.randomUUID();

        when(notificationRepository
                .findByRecipientAndId(
                        student,
                        notificationId
                ))
                .thenReturn(
                        Optional.of(notification)
                );


        BasicResponse response =
                notificationService.markNotificationAsSeen(
                        student,
                        notificationId
                );


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Marked Notifications as seen");

        verify(notification).setSeen(true);
    }
}

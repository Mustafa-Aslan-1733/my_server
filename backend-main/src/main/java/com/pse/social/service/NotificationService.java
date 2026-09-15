package com.pse.social.service;

import java.util.List;
import java.util.UUID;

import com.pse.lecture.mapper.LectureResponseMapper;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.NotificationType;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.social.dto.response.NotificationResponse;
import com.pse.social.dto.response.NotificationsResponse;
import com.pse.social.model.Notification;
import com.pse.social.repository.NotificationRepository;
import com.pse.user.model.Student;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a student has not read yet: someone answered their question, or a moderator warned
 * them.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Creates NotificationService.
     *
     * @param notificationRepository the notificationRepository
     */
    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Returns getNotifications.
     *
     * @param student the student
     * @return the result
     */
    @Transactional(readOnly = true)
    public NotificationsResponse getNotifications(Student student) {

        List<NotificationResponse> responses = notificationRepository
                .findAllByRecipientAndSeenFalseOrderByCreatedAtDesc(student)
                .stream()
                .map(NotificationService::toResponse)
                .toList();

        return new NotificationsResponse(
                "Found: " + responses.size() + " Notifications", true, responses);
    }

    /**
     * Returns markNotificationAsSeen.
     *
     * @param student the student
     * @param notificationId the notificationId
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse markNotificationAsSeen(Student student, UUID notificationId) {

        // Looked up by recipient as well as by id, which is what makes this the owner's
        // notification and not just any notification with that id.
        Notification notification = notificationRepository
                .findByRecipientAndId(student, notificationId)
                .orElse(null);

        if (notification == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
        }

        notification.setSeen(true);

        return new BasicResponse("Marked Notifications as seen", true);
    }

    private static NotificationResponse toResponse(Notification notification) {

        // Rows written before the type column existed are answer notifications.
        NotificationType type = notification.getType() == null
                ? NotificationType.ANSWER
                : notification.getType();

        if (type == NotificationType.WARNING) {
            return new NotificationResponse(
                    notification.getId(),
                    type,
                    null,
                    null,
                    null,
                    null,
                    notification.getMessage(),
                    notification.getCreatedAt()
            );
        }

        Student student = notification.getAnswer().getStudent();

        boolean deleted = student.getStatus() == UserStatus.DELETED;

        String userName = deleted ? "Deleted User" : student.getUsername();

        return new NotificationResponse(
                notification.getId(),
                type,
                LectureResponseMapper.toResponse(notification.getLecture()),
                notification.getOwnComment().getId(),
                userName,
                notification.getAnswer().getContent(),
                null,
                notification.getCreatedAt()
        );
    }


}

package com.pse.social.dto.response;


import com.pse.lecture.dto.response.LectureResponse;
import com.pse.shared.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;


/**
 * One notification in the student's feed.
 *
 * <p>{@code type} is the discriminator. For {@code ANSWER} the answer author is in
 * {@code userName} and their text in {@code content}, and {@code message} is null.
 * For {@code WARNING} the reason is in {@code message}, and {@code userName} and
 * {@code content} are null — a warning is not attributed to a person publicly and
 * carries no answer.
 *
 * @param notificationID the notificationID
 * @param type the type
 * @param lectureResponse the lectureResponse
 * @param ownCommentID the ownCommentID
 * @param userName the userName
 * @param content the content
 * @param message the message
 * @param createdAt the createdAt
 */
public record NotificationResponse(
        UUID notificationID,
        NotificationType type,

        LectureResponse lectureResponse,
        UUID ownCommentID,

        String userName,
        String content,
        String message,
        LocalDateTime createdAt
) {
}

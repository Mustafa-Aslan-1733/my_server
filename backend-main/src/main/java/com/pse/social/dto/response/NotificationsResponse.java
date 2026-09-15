package com.pse.social.dto.response;



import java.util.List;

/**
 * Represents NotificationsResponse.
 *
 * @param message the message
 * @param success the success
 * @param notifications the notifications
 */
public record NotificationsResponse(
        String message,
        boolean success,
        List<NotificationResponse> notifications

) {
}

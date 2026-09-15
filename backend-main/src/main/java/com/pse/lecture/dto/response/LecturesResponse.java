package com.pse.lecture.dto.response;

import java.util.List;

/**
 * Represents LecturesResponse.
 *
 * @param message the message
 * @param success the success
 * @param lectures the lectures
 */
public record LecturesResponse(
        String message,
        boolean success,
        List<LectureResponse> lectures
) {
}

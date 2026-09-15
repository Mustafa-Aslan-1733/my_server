package com.pse.lecture.dto.response;


/**
 * Represents LectureDetailResponse.
 *
 * @param message the message
 * @param success the success
 * @param lecture the lecture
 */
public record LectureDetailResponse(
        String message,
        boolean success,
        LectureResponse lecture
) {
}

package com.pse.user.dto;

/**
 * Represents StudentProfileResponse.
 *
 * @param message the message
 * @param success the success
 * @param username the username
 * @param profilePicture the profilePicture
 * @param averageRating the averageRating
 * @param ratingsCount the ratingsCount
 * @param commentsWritten the commentsWritten
 * @param credibilityScore the credibilityScore
 */
public record StudentProfileResponse(
        String message,
        boolean success,

        String username,
        String profilePicture,
        double averageRating,
        int ratingsCount,
        int commentsWritten,
        int credibilityScore
) { }

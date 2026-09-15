package com.pse.shared.util;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.pse.shared.error.ApiException;

/**
 * Turning a caller's string into a {@code UUID} without answering 500 for a bad one.
 *
 * <p>{@code UUID.fromString} throws {@code IllegalArgumentException} for a malformed value and
 * {@code NullPointerException} for a missing one, and {@code GlobalExceptionHandler} maps
 * neither -- both fall to its catch-all and become "Unexpected backend error". That is the
 * wrong answer twice over: the caller sent a bad request, and a 500 tells them the backend
 * broke instead.
 *
 * <p>Named because three places had already written it privately -- {@code AuditLogService},
 * {@code RatingModerationService} and, guarding a different parse the same way,
 * {@link KeysetCursorCodec} -- while the four {@code /social} submit routes had not, and
 * answered 500. A rule that lives in three copies is a rule that only holds where somebody
 * remembered it.
 */
public final class Uuids {

    private Uuids() {
    }

    /**
     * A required id: absent is as wrong as malformed, and both are the caller's fault.
     *
     * @param errorMessage what the caller is told; name the field, because a request carrying
     *                     two ids gives no clue which one was rejected otherwise
     *
     * @param value the value
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    public static UUID parse(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return parsed(value, errorMessage);
    }

    /**
     * An optional id -- a filter parameter, where absent means "do not filter".
     *
     * @return {@code null} for a null or blank value; never for a malformed one, which is
     *         still a bad request
     *
     * @param value the value
     * @param errorMessage the errorMessage
     */
    public static UUID parseOrNull(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parsed(value, errorMessage);
    }

    private static UUID parsed(String value, String errorMessage) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }
}

package com.pse.shared.util;

import com.pse.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque cursor for keyset pagination over a {@code (createdAt DESC, id DESC)} ordering.
 *
 * <p>Shared by every paginated listing so the cursor format, its size cap and its failure
 * mode are defined once. A page-number scheme would skip or repeat rows as the underlying
 * table is written to between pages; a keyset cursor cannot.
 */
@Component
public class KeysetCursorCodec {

    private static final String VERSION = "1";

    /**
     * Returns encode.
     *
     * @param createdAt the createdAt
     * @param id the id
     * @return the result
     */
    public String encode(Instant createdAt, UUID id) {
        // Keep the database timestamp precision in the opaque cursor. Public DTOs still
        // use fixed millisecond UTC formatting, but truncating here can skip same-ms rows.
        String payload = VERSION + "|" + createdAt + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Entities that store a {@code LocalDateTime} rather than an {@code Instant} hold it as
     * UTC, the same assumption {@link UtcDates} makes when formatting one.
     *
     * @param createdAt the createdAt
     * @param id the id
     * @return the result
     */
    public String encode(LocalDateTime createdAt, UUID id) {
        return encode(createdAt.toInstant(ZoneOffset.UTC), id);
    }

    /**
     * @param errorMessage what a malformed cursor is reported as. Each listing names its own
     *                     so the caller can tell which parameter it got wrong.
     * @param value the value
     * @return the result
     */
    public Cursor decode(String value, String errorMessage) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw invalidCursor(errorMessage);
        }
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8
            );
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw invalidCursor(errorMessage);
            }
            return new Cursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Instant.parse throws DateTimeParseException, which is not an
            // IllegalArgumentException, so a narrower catch here leaks a 500.
            throw invalidCursor(errorMessage);
        }
    }

    private ApiException invalidCursor(String errorMessage) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorMessage);
    }

    /**
     * Represents Cursor.
     *
     * @param createdAt the createdAt
     * @param id the id
     */
    public record Cursor(Instant createdAt, UUID id) {

        /**
         * The same instant as a UTC {@code LocalDateTime}, for entities that store one.
         *
         * @return the result
         */
        public LocalDateTime createdAtLocal() {
            return LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC);
        }
    }
}

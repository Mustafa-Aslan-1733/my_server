package com.pse.shared.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Provides UtcDates.
 */
public final class UtcDates {

    private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    private UtcDates() {
    }

    /**
     * Returns format.
     *
     * @param value the value
     * @return the result
     */
    public static String format(LocalDateTime value) {
        return value == null ? null : format(value.toInstant(ZoneOffset.UTC));
    }

    /**
     * Returns format.
     *
     * @param value the value
     * @return the result
     */
    public static String format(Instant value) {
        return value == null ? null : FORMATTER.format(value);
    }
}

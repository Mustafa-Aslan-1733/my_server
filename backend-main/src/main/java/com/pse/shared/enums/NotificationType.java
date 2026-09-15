package com.pse.shared.enums;

/**
 * Distinguishes what a notification row is about.
 *
 * <p>{@link #ANSWER} rows carry an answer and no message; {@link #WARNING} rows
 * carry a message and no answer. Readers must branch on this before touching
 * either field.
 */
public enum NotificationType {
    /**
     * The ANSWER.
     */
    ANSWER,
    /**
     * The WARNING.
     */
    WARNING
}

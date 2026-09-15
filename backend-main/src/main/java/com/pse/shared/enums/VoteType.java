package com.pse.shared.enums;

/**
 * The direction of a vote on a comment or an answer.
 *
 * <p>{@link #NONE} is a withdrawal rather than a third direction -- F-3. It is never
 * persisted: the vote row is deleted instead, which is why the {@code vote} column can stay
 * {@code nullable = false}. Adding it is additive, so a client that only ever sends
 * {@code UP} or {@code DOWN} is unaffected; taking a vote back is something the API simply
 * had no way to express before.
 */
public enum VoteType {
    /**
     * The UP.
     */
    UP,
    /**
     * The DOWN.
     */
    DOWN,
    /**
     * The NONE.
     */
    NONE
}

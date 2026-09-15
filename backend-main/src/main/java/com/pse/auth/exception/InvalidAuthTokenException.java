package com.pse.auth.exception;

/**
 * Provides InvalidAuthTokenException.
 */
public class InvalidAuthTokenException extends RuntimeException {

    /**
     * Creates InvalidAuthTokenException.
     *
     * @param message the message
     */
    public InvalidAuthTokenException(String message) {
        super(message);
    }

}
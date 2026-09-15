package com.pse.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Provides ApiException.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * Creates ApiException.
     *
     * @param status the status
     * @param message the message
     */
    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    /**
     *
     * Simple ApiException
     *
     * @param code a stable, machine-readable reason the caller can branch on, for the cases
     *             where a human-readable message is not enough — a refused revert has several
     *             distinct causes and the panel has to tell the operator which one it hit.
     *             Null for the ordinary errors, and then omitted from the response entirely.
     *
     * @param status the status
     * @param message the message
     */
    public ApiException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * Returns getStatus.
     *
     * @return the result
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Returns getCode.
     *
     * @return the result
     */
    public String getCode() {
        return code;
    }
}

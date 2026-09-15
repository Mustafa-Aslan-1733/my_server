package com.pse.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Provides RateLimitException.
 */
public class RateLimitException extends ApiException {

    private final long retryAfterSeconds;

    /**
     * Creates RateLimitException.
     *
     * @param retryAfterSeconds the retryAfterSeconds
     */
    public RateLimitException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    /**
     * Returns getRetryAfterSeconds.
     *
     * @return the result
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

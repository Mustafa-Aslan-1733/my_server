package com.pse.auth.dto.response;

/**
 * Answer of a successful login. {@code expiresAt} is the moment the returned token
 * stops authenticating, as a UTC ISO 8601 timestamp. It is served because the two APIs
 * hand out sessions of very different lengths and a client cannot know which it got.
 *
 * @param message the message
 * @param success the success
 * @param authToken the authToken
 * @param expiresAt the expiresAt
 */
public record LoginResponse(String message, boolean success, String authToken, String expiresAt) { }

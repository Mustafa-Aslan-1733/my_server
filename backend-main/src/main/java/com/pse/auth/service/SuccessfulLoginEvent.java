package com.pse.auth.service;

/**
 * Represents SuccessfulLoginEvent.
 *
 * @param email the email
 * @param ipAddress the ipAddress
 * @param userAgent the userAgent
 */
public record SuccessfulLoginEvent(String email, String ipAddress, String userAgent) {
}

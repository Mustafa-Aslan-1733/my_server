package com.pse.auth.dto.request;


/**
 * Represents LogoutRequest.
 *
 * @param email the email
 */
public record LogoutRequest(
        String email
) { }
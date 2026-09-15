package com.pse.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents OTPRequest.
 *
 * @param email the email
 */
public record OTPRequest(
        @NotBlank String email
) { }


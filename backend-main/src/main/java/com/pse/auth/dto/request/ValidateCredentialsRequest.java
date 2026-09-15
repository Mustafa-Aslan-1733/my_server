package com.pse.auth.dto.request;


import jakarta.validation.constraints.NotBlank;

/**
 * Represents ValidateCredentialsRequest.
 *
 * @param email the email
 */
public record ValidateCredentialsRequest(

        @NotBlank String email

) { }
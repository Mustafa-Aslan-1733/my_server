package com.pse.auth.dto.request;

import com.pse.auth.model.SessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Represents LoginRequest.
 *
 * @param email the email
 * @param loginToken the loginToken
 * @param sessionType the sessionType
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank @Size(max = 128) String loginToken,
        SessionType sessionType
) { }

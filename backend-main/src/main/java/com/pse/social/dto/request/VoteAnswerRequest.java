package com.pse.social.dto.request;

import com.pse.shared.enums.VoteType;
import jakarta.validation.constraints.NotNull;

/**
 * Represents VoteAnswerRequest.
 *
 * @param voteType the voteType
 */
public record VoteAnswerRequest(
        @NotNull VoteType voteType
) {
}

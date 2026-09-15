package com.pse.social.dto.request;

import com.pse.shared.enums.VoteType;
import jakarta.validation.constraints.NotNull;


/**
 * Represents VoteCommentRequest.
 *
 * @param voteType the voteType
 */
public record VoteCommentRequest(
        @NotNull VoteType voteType
) {
}

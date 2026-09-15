package com.pse.social.dto.response;

import com.pse.shared.enums.ContentStatus;
import com.pse.shared.enums.VoteType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents AnswerResponse.
 *
 * @param commentID the commentID
 * @param content the content
 * @param status the status
 * @param studentUsername the studentUsername
 * @param profilePicture the profilePicture
 * @param createdAt the createdAt
 * @param downVotes the downVotes
 * @param upVotes the upVotes
 * @param userVote the userVote
 */
public record AnswerResponse(
        UUID commentID,
        String content,
        ContentStatus status,
        String studentUsername,
        String profilePicture,
        LocalDateTime createdAt,
        int downVotes,
        int upVotes,
        VoteType userVote

) {
}

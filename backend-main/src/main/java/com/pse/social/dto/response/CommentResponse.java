package com.pse.social.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.pse.shared.enums.ContentStatus;
import com.pse.shared.enums.VoteType;

/**
 * Represents CommentResponse.
 *
 * @param commentID the commentID
 * @param content the content
 * @param status the status
 * @param studentUsername the studentUsername
 * @param profilePicture the profilePicture
 * @param lectureID the lectureID
 * @param createdAt the createdAt
 * @param downVotes the downVotes
 * @param upVotes the upVotes
 * @param userVote the userVote
 * @param answers the answers
 */
public record CommentResponse(

        UUID commentID,
        String content,
        ContentStatus status,
        String studentUsername,
        String profilePicture,
        UUID lectureID,
        LocalDateTime createdAt,
        int downVotes,
        int upVotes,
        VoteType userVote,

        List<AnswerResponse> answers

) {
}

package com.pse.social.repository;


import com.pse.shared.enums.UserStatus;
import com.pse.shared.enums.VoteType;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentVote;
import com.pse.user.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Defines CommentVoteRepository.
 */
public interface CommentVoteRepository extends JpaRepository<CommentVote, UUID> {


    /**
     * Returns findByStudentAndComment.
     *
     * @param student the student
     * @param comment the comment
     * @return the result
     */
    Optional<CommentVote> findByStudentAndComment(Student student, Comment comment);


    int countByCommentAndVoteAndStudentStatus(
            Comment comment,
            VoteType vote,
            UserStatus status
    );


    /**
     * Returns countByCommentAndVote.
     *
     * @param comment the comment
     * @param vote the vote
     * @return the result
     */
    int countByCommentAndVote(Comment comment, VoteType vote);
}

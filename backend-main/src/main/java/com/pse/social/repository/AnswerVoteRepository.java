package com.pse.social.repository;


import com.pse.shared.enums.UserStatus;
import com.pse.shared.enums.VoteType;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerVote;

import com.pse.user.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines AnswerVoteRepository.
 */
public interface AnswerVoteRepository extends JpaRepository<AnswerVote, UUID> {


    /**
     * Returns findByStudentAndAnswer.
     *
     * @param student the student
     * @param answer the answer
     * @return the result
     */
    Optional<AnswerVote> findByStudentAndAnswer(Student student, Answer answer);

    /**
     * Returns countByAnswerAndVote.
     *
     * @param answer the answer
     * @param vote the vote
     * @return the result
     */
    int countByAnswerAndVote(Answer answer, VoteType vote);

    int countByAnswerAndVoteAndStudentStatus(
            Answer answer,
            VoteType vote,
            UserStatus status
    );

    /**
     * Answer votes are not cascaded from {@code Answer}, so deleting an answer without
     * clearing these first violates the foreign key.
     *
     * @param answerIds the answerIds
     * @return the result
     */
    @Modifying
    @Query("DELETE FROM AnswerVote vote WHERE vote.answer.id IN :answerIds")
    int deleteByAnswerIdIn(@Param("answerIds") Collection<UUID> answerIds);

}

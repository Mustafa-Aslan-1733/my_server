package com.pse.social.repository;



import com.pse.social.model.Answer;
import com.pse.shared.repository.IdCount;
import com.pse.social.model.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


/**
 * Defines AnswerRepository.
 */
public interface AnswerRepository extends JpaRepository<Answer, UUID> {


    /**
     * Returns findAllByOrderByCreatedAtDesc.
     *
     * @return the result
     */
    @EntityGraph(attributePaths = {"student", "comment", "comment.student", "comment.lecture"})
    List<Answer> findAllByOrderByCreatedAtDesc();

    /**
     * Returns findAllByComment.
     *
     * @param comment the comment
     * @return the result
     */
    List<Answer> findAllByComment(Comment comment);

    /**
     * Ids of every answer in a lecture's threads, for clearing them before a cascade.
     *
     * @param lectureId the lectureId
     * @return the result
     */
    @Query("SELECT answer.id FROM Answer answer WHERE answer.comment.lecture.id = :lectureId")
    List<UUID> findIdsByLectureId(@Param("lectureId") UUID lectureId);

    /**
     * Answers per comment, as {@code [commentId, count]} rows.
     *
     * @return the result
     */
    @Query("""
        SELECT answer.comment.id AS id, COUNT(answer) AS count
        FROM Answer answer
        GROUP BY answer.comment.id
            """)
    List<IdCount> countGroupedByComment();
}

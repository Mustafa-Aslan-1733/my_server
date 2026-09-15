package com.pse.social.repository;


import com.pse.shared.repository.IdCount;
import com.pse.social.model.AnswerReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * Defines AnswerReportRepository.
 */
public interface AnswerReportRepository extends JpaRepository<AnswerReport, UUID> {

    /**
     * Find all AnswerReports and order it by creation in descending order.
     *
     * @return  s
     */
    @EntityGraph(attributePaths = {
        "answer", "answer.student", "answer.comment", "answer.comment.lecture", "reporter"
    })
    List<AnswerReport> findAllByOrderByCreatedAtDesc();

    /**
     * Finds AnswerReport By Id for Update.
     *
     * @param id of answerIds.
     * @return AnswerReport
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
        "answer", "answer.student", "answer.comment", "answer.comment.lecture", "reporter"
    })
    @Query("select report from AnswerReport report where report.id = :id")
    Optional<AnswerReport> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Reports per answer, as {@code [answerId, count]} rows.
     *
     * @return List of IdCount
     */
    @Query("""
        SELECT report.answer.id AS id, COUNT(report) AS count
        FROM AnswerReport report
        GROUP BY report.answer.id
            """)
    List<IdCount> countGroupedByAnswer();
}

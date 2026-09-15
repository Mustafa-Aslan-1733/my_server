package com.pse.social.repository;



import com.pse.shared.enums.ReportStatus;
import com.pse.shared.repository.IdCount;
import com.pse.social.model.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import com.pse.user.model.Student;


/**
 * Defines CommentReportRepository.
 */
public interface CommentReportRepository extends JpaRepository<CommentReport, UUID> {

    /**
     * Returns countByCommentStudent.
     *
     * @param student the student
     * @return the result
     */
    long countByCommentStudent(Student student);

    /**
     * Returns countByStatus.
     *
     * @param status the status
     * @return the result
     */
    long countByStatus(ReportStatus status);

    /**
     * Reports received per comment author, as {@code [studentId, count]} rows.
     * The per-row equivalent of {@link #countByCommentStudent(Student)}.
     *
     * @return the result
     */
    @Query("""
        SELECT report.comment.student.id AS id, COUNT(report) AS count
        FROM CommentReport report
        GROUP BY report.comment.student.id
        """)
    List<IdCount> countGroupedByCommentStudent();

    /**
     * Reports per comment, as {@code [commentId, count]} rows.
     *
     * @return the result
     */
    @Query("""
        SELECT report.comment.id AS id, COUNT(report) AS count
        FROM CommentReport report
        GROUP BY report.comment.id
        """)
    List<IdCount> countGroupedByComment();

    /**
     * Returns findAllByOrderByCreatedAtDesc.
     *
     * @return the result
     */
    @EntityGraph(attributePaths = {"comment", "comment.student", "comment.lecture", "reporter"})
    List<CommentReport> findAllByOrderByCreatedAtDesc();

    /**
     * Returns findByIdForUpdate.
     *
     * @param id the id
     * @return the result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"comment", "comment.student", "comment.lecture", "reporter"})
    @Query("select report from CommentReport report where report.id = :id")
    Optional<CommentReport> findByIdForUpdate(@Param("id") UUID id);
}

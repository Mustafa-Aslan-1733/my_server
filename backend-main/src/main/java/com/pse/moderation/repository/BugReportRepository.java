package com.pse.moderation.repository;



import java.util.UUID;

import com.pse.moderation.model.BugReport;
import com.pse.shared.enums.ReportStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * Defines BugReportRepository.
 */
public interface BugReportRepository extends JpaRepository<BugReport, UUID> {

    /**
     * Returns findAllByOrderByCreatedAtDesc.
     *
     * @return the result
     */
    @EntityGraph(attributePaths = "reporter")
    List<BugReport> findAllByOrderByCreatedAtDesc();

    /**
     * Returns countByStatus.
     *
     * @param status the status
     * @return the result
     */
    long countByStatus(ReportStatus status);

    /**
     * Returns findByIdForUpdate.
     *
     * @param id the id
     * @return the result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "reporter")
    @Query("select report from BugReport report where report.id = :id")
    Optional<BugReport> findByIdForUpdate(@Param("id") UUID id);
}

package com.pse.moderation.repository;



import java.util.List;
import com.pse.shared.repository.IdCount;
import java.util.UUID;

import com.pse.moderation.model.Warning;
import com.pse.user.model.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


/**
 * Defines WarningRepository.
 */
public interface WarningRepository extends JpaRepository<Warning, UUID> {


    /**
     * Returns findByStudent.
     *
     * @param student the student
     * @return the result
     */
    List<Warning> findByStudent(Student student);

    /**
     * The warning history as the panel reads it. The issuing admin and the student behind
     * it are fetched with the warnings because the response names the issuer; without the
     * graph that name costs two extra queries per warning.
     *
     * @param student the student
     * @return the result
     */
    @EntityGraph(attributePaths = {"admin", "admin.student"})
    List<Warning> findByStudentOrderByCreatedAtDesc(Student student);

    /**
     * Returns countByStudent.
     *
     * @param student the student
     * @return the result
     */
    long countByStudent(Student student);

    /**
     * Warning counts for every student that has at least one, as
     * {@code [studentId, count]} rows. Lets the user list resolve its counters in
     * one query instead of one per row.
     *
     * @return the result
     */
    @Query("""
        SELECT warning.student.id AS id, COUNT(warning) AS count
        FROM Warning warning
        GROUP BY warning.student.id
        """)
    List<IdCount> countGroupedByStudent();

}

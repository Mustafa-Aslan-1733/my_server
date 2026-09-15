package com.pse.professor.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.pse.professor.model.Professor;

/**
 * Defines ProfessorRepository.
 */
public interface ProfessorRepository extends JpaRepository<Professor, UUID> {

    /**
     * The reads below fetch {@code lectures} with the professor because the response carries
     * {@code lectureIds}. {@code Professor.lectures} is the inverse side of the many-to-many
     * and is lazy, and open-in-view is disabled, so without the graph these reads are an N+1
     * at best and a LazyInitializationException at worst.
     *
     * @return the result
     */
    @EntityGraph(attributePaths = "lectures")
    /*
     * List, not Set. These queries carry an ORDER BY, and the order is what the catalogue
     * listing serves to the client -- but a Set declares no order, so the contract said the
     * opposite of what the query means. It worked only because Spring Data happens to
     * materialise a LinkedHashSet, which is a fact about the framework rather than a
     * guarantee anybody wrote down. F-21's shape at the type level.
     */
    List<Professor> findByActiveTrueOrderByLastNameAscFirstNameAsc();

    /**
     * Returns findAllByOrderByLastNameAscFirstNameAsc.
     *
     * @return the result
     */
    @EntityGraph(attributePaths = "lectures")
    List<Professor> findAllByOrderByLastNameAscFirstNameAsc();

    /**
     * Returns findWithLecturesById.
     *
     * @param id the id
     * @return the result
     */
    @EntityGraph(attributePaths = "lectures")
    Optional<Professor> findWithLecturesById(UUID id);

    /**
     * Returns findByFirstNameAndLastName.
     *
     * @param firstName the firstName
     * @param lastName the lastName
     * @return the result
     */
    Optional<Professor> findByFirstNameAndLastName(String firstName, String lastName);
}

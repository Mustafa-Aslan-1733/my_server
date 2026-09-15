package com.pse.auth.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.pse.auth.model.Token;
import com.pse.user.model.Student;

/**
 * Defines TokenRepository.
 */
public interface TokenRepository extends JpaRepository<Token, UUID> {

    /**
     * Returns findByHashAndRevokedFalseAndExpiresAtAfter.
     *
     * @param hash the hash
     * @param now the now
     * @return the result
     */
    @EntityGraph(attributePaths = "student")
    Optional<Token> findByHashAndRevokedFalseAndExpiresAtAfter(
            String hash,
            LocalDateTime now
    );

    /**
     * Returns findByLegacyValueAndRevokedFalseAndExpiresAtAfter.
     *
     * @param legacyValue the legacyValue
     * @param now the now
     * @return the result
     */
    @EntityGraph(attributePaths = "student")
    Optional<Token> findByLegacyValueAndRevokedFalseAndExpiresAtAfter(
            String legacyValue,
            LocalDateTime now
    );

    /**
     * Returns findByEmail.
     *
     * @param email the email
     * @return the result
     */
    List<Token> findByEmail(String email);

    /**
     * Returns findByStudent.
     *
     * @param student the student
     * @return the result
     */
    List<Token> findByStudent(Student student);

    /**
     * Returns findByStudentOrEmail.
     *
     * @param student the student
     * @param email the email
     * @return the result
     */
    List<Token> findByStudentOrEmail(Student student, String email);
}

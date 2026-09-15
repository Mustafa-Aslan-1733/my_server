package com.pse.auth.repository;



import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pse.auth.model.OneTimePassword;

import jakarta.persistence.LockModeType;

/**
 * Defines OneTimePasswordRepository.
 */
public interface OneTimePasswordRepository extends JpaRepository<OneTimePassword, Integer> {

   
    /**
     * Returns findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc.
     *
     * @param email the email
     * @param expiresAt the expiresAt
     * @return the result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OneTimePassword> findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email,
            LocalDateTime expiresAt
    );

    /**
     * Returns consumeOutstandingForEmail.
     *
     * @param email the email
     * @return the result
     */
    @Modifying
    @Query("""
            update OneTimePassword otp
            set otp.used = true
            where otp.email = :email and otp.used = false
            """)
    int consumeOutstandingForEmail(@Param("email") String email);
}

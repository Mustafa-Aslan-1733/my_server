package com.pse.auth.repository;

import com.pse.auth.model.AuthRateLimitBucket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;
import java.util.UUID;

/**
 * Defines AuthRateLimitBucketRepository.
 */
public interface AuthRateLimitBucketRepository extends JpaRepository<AuthRateLimitBucket, UUID> {

    /**
     * Returns findByOperationAndSubjectHash.
     *
     * @param operation the operation
     * @param subjectHash the subjectHash
     * @return the result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthRateLimitBucket> findByOperationAndSubjectHash(String operation, String subjectHash);

    /**
     * Returns deleteByOperationAndSubjectHash.
     *
     * @param operation the operation
     * @param subjectHash the subjectHash
     */
    @Modifying
    void deleteByOperationAndSubjectHash(String operation, String subjectHash);

}

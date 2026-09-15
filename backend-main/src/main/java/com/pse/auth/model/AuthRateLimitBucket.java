package com.pse.auth.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provides AuthRateLimitBucket.
 */
@Entity
@Table(
        name = "auth_rate_limit_buckets",
        uniqueConstraints = @UniqueConstraint(
                name = "auth_rate_limit_operation_subject_key",
                columnNames = {"operation", "subject_hash"}
        )
)

@Getter
@Setter
@NoArgsConstructor
public class AuthRateLimitBucket {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 40)
    private String operation;

    @Column(name = "subject_hash", nullable = false, length = 64)
    private String subjectHash;

    @Column(name = "window_started_at", nullable = false)
    private LocalDateTime windowStartedAt;

    @Column(nullable = false)
    private int attempts;

    @Version
    private long version;
}

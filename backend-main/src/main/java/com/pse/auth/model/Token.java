package com.pse.auth.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.pse.user.model.Student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provides Token.
 */
@Entity
@Table(name = "tokens")
@Getter
@Setter
@NoArgsConstructor
public class Token {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String email;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(columnDefinition = "TEXT")
    private String hash;

    /**
     * Temporary compatibility column for sessions created by deployments that
     * stored the raw token in {@code tokens.value}. New sessions never populate
     * this field; a successful legacy authentication upgrades it to {@link #hash}.
     */
    @Column(name = "value", columnDefinition = "TEXT")
    private String legacyValue;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Null only for sessions issued before session types were introduced. Those
     * legacy sessions keep their original role-based behaviour until they expire.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type")
    private SessionType sessionType;

    @Column(nullable = false)
    private boolean revoked = false;

}

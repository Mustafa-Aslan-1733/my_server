package com.pse.moderation.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import com.pse.user.model.Student;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provides Admin.
 */
@Entity
@Table(
        name = "admins",
        uniqueConstraints = {@UniqueConstraint(columnNames = "student_id")}
)


@Getter
@Setter
@NoArgsConstructor
public class Admin {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "student_id", unique = true)
    private Student student;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

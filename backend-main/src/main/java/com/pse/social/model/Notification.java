package com.pse.social.model;


import com.pse.shared.enums.NotificationType;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;
import com.pse.lecture.model.Lecture;

/**
 * Provides Notification.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id")
    private Student recipient;


    @ManyToOne
    @JoinColumn(name = "lecture_id")
    private Lecture lecture;

    @ManyToOne
    @JoinColumn(name = "own_comment_id")
    private Comment ownComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private NotificationType type = NotificationType.ANSWER;

    /** Null for {@link NotificationType#WARNING} rows, which have no answer. */
    @ManyToOne
    @JoinColumn(name = "answer_id")
    private Answer answer;

    /** Only populated for {@link NotificationType#WARNING} rows. */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private boolean seen;


    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

package com.pse.user.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pse.moderation.model.Warning;
import com.pse.rating.model.Rating;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerReport;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentReport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.pse.shared.enums.UserStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provides Student.
 */
@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "kit_email", nullable = false, unique = true)
    private String kitEmail;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "credibility_score", nullable = false)
    private int credibilityScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String biography;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_reason")
    private String blockedReason;

    /** Set when the account is deleted and its identifying fields are scrubbed. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * The account's ratings, newest first.
     *
     * <p>The {@code @OrderBy} is load-bearing. This bag is what {@code GET /account/ratings}
     * serialises, and without it the array came back in whatever order the database chose --
     * F-21's defect on the one list the sweep behind F-35 did not reach. The id breaks a tie
     * so two ratings written in the same instant still come out the same way twice.
     */
    @OneToMany(mappedBy = "student")
    @OrderBy("createdAt DESC, id ASC")
    private List<Rating> ratings = new ArrayList<>();

    @OneToMany(mappedBy = "student")
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "student")
    private List<Answer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "reporter")
    private List<CommentReport> commentReports = new ArrayList<>();

    @OneToMany(mappedBy = "reporter")
    private List<AnswerReport> answerReports = new ArrayList<>();

    @OneToMany(mappedBy = "student")
    private List<Warning> warnings = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}

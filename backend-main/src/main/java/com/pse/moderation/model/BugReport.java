package com.pse.moderation.model;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

import com.pse.shared.enums.ReportStatus;
import com.pse.shared.enums.BugSeverity;
import com.pse.shared.enums.IssueState;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Provides BugReport.
 */
@Entity
@Table(name = "bug_reports")
@Getter
@Setter
@NoArgsConstructor
public class BugReport {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BugSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.OPEN;

    @ManyToOne
    @JoinColumn(name = "reporter_id")
    private Student reporter;

    @ManyToOne
    @JoinColumn(name = "resolved_by_admin_id")
    private Admin resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Where the tracker issue for this report lives, once one has been opened. Null until
     * then, which is what tells the panel to offer "create issue" instead of a link.
     */
    @Column(name = "issue_url")
    private String issueUrl;

    /** The project-scoped issue number, for display. */
    @Column(name = "issue_iid")
    private Integer issueIid;

    /**
     * Carries a database-level default, which the other columns here do not need. Production
     * runs {@code ddl-auto=update} against a populated {@code bug_reports}, and Postgres
     * refuses to add a NOT NULL column unless the rows already there have something to take.
     * The Java initialiser only covers rows this application creates, so without the default
     * the column is never added — and a failed schema update is logged, not fatal, so the
     * application would come up and fail on the first read instead.
     */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'NONE'")
    @Column(name = "issue_state", nullable = false)
    private IssueState issueState = IssueState.NONE;
}
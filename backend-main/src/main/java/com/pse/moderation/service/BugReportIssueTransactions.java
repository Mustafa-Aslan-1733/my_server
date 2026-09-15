package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.moderation.model.BugReport;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.IssueState;
import com.pse.shared.error.ApiException;
import com.pse.shared.util.UtcDates;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * The two transaction boundaries issue creation needs, kept apart on purpose.
 *
 * <p>Marking a report as {@code FAILED} cannot happen inside the transaction that failed:
 * the exception that makes it a failure rolls that transaction back, and the marker with it,
 * leaving the report looking as though nothing had ever been attempted. So the attempt runs
 * in one transaction and the marker in another, and the orchestration between them lives
 * outside both. They are on their own bean because a method calling a sibling on {@code this}
 * bypasses the proxy, and with it the transaction boundary that is the whole point here.
 */
@Service
public class BugReportIssueTransactions {

    private final BugReportRepository repository;
    private final GitLabClient gitLabClient;
    private final AuditWriter auditWriter;

    /**
     * Creates BugReportIssueTransactions.
     *
     * @param repository the repository
     * @param gitLabClient the gitLabClient
     * @param auditWriter the auditWriter
     */
    public BugReportIssueTransactions(
            BugReportRepository repository,
            GitLabClient gitLabClient,
            AuditWriter auditWriter
    ) {
        this.repository = repository;
        this.gitLabClient = gitLabClient;
        this.auditWriter = auditWriter;
    }

    /**
     * Creates the issue, or returns the one already recorded.
     *
     * <p>The row is locked for the duration, which is what makes this idempotent against a
     * double-click and against two admins working the same queue: the second caller waits
     * and then sees {@code CREATED} rather than opening a duplicate issue.
     *
     * @param principal the principal
     * @param reportId the reportId
     * @return the result
     */
    @Transactional
    public Attempt createOrReturnExisting(AuthenticatedUser principal, UUID reportId) {
        BugReport report = repository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bug report not found"));

        if (report.getIssueState() == IssueState.CREATED && report.getIssueUrl() != null) {
            return new Attempt(report.getIssueUrl(), report.getIssueIid(), true);
        }

        GitLabClient.CreatedIssue issue =
                gitLabClient.createIssue(issueTitle(report), issueDescription(report));

        report.setIssueUrl(issue.url());
        report.setIssueIid(issue.iid());
        report.setIssueState(IssueState.CREATED);
        repository.save(report);

        auditWriter.write(
                principal.admin(),
                AuditAction.BUG_REPORT_ISSUE_CREATED,
                AuditTargetType.BUG_REPORT,
                report.getId(),
                report.getTitle(),
                AuditWriter.nullableChange("issueUrl", null, issue.url()),
                Map.of("issueIid", issue.iid() == null ? "" : issue.iid())
        );
        return new Attempt(issue.url(), issue.iid(), false);
    }

    /**
     * Records that creation was tried and did not work, in its own transaction so it outlives
     * the failure. Not audited: nothing about the report changed that an administrator did.
     *
     * @param reportId the reportId
     */
    @Transactional
    public void markFailed(UUID reportId) {
        repository.findById(reportId).ifPresent(report -> {
            if (report.getIssueState() != IssueState.CREATED) {
                report.setIssueState(IssueState.FAILED);
                repository.save(report);
            }
        });
    }

    private static String issueTitle(BugReport report) {
        return "[" + report.getSeverity().name() + "] " + report.getTitle();
    }

    /**
     * The issue body. Carries the report id so an issue can be traced back, and the reporter's
     * display name rather than their address — the tracker is a wider audience than the panel.
     */
    private static String issueDescription(BugReport report) {
        return report.getDescription()
                + System.lineSeparator() + System.lineSeparator() + "---" + System.lineSeparator()
                + "Reported by: "
                + (report.getReporter() == null ? "Unknown" : report.getReporter().getUsername())
                + System.lineSeparator()
                + "Reported at: " + UtcDates.format(report.getCreatedAt()) + System.lineSeparator()
                + "Bug report id: " + report.getId();
    }

    /**
     * Creates Attempt.
     *
     * @param url the url
     * @param iid the iid
     * @param existing whether the issue was already there, rather than opened just now
     */
    public record Attempt(String url, Integer iid, boolean existing) {
    }
}

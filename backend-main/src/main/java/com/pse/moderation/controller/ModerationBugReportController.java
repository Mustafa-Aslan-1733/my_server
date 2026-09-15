package com.pse.moderation.controller;

import com.pse.shared.dto.BasicResponse;
import com.pse.moderation.dto.request.BugReportRequest;
import com.pse.moderation.dto.request.BugReportUpdateRequest;
import com.pse.moderation.dto.response.BugReportsResponse;
import com.pse.moderation.dto.response.GitLabIssueResponse;
import com.pse.moderation.service.ModerationBugReportService;
import com.pse.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Bug report moderation, plus the one endpoint that is not moderation: {@code POST /reports},
 * where a student submits a report. That one is app API and deliberately has no {@code /admin}
 * twin, which is why the admin routes carry their absolute paths instead of a class-level
 * base path.
 */
@RestController
public class ModerationBugReportController {

    private final ModerationBugReportService service;

    /**
     * Creates ModerationBugReportController.
     *
     * @param service the service
     */
    public ModerationBugReportController(ModerationBugReportService service) {
        this.service = service;
    }

    /**
     * Returns getAllReports.
     *
     * @return the result
     */
    @GetMapping({"/admin/reports", "/reports"})
    public BugReportsResponse getAllReports() {
        return service.getAllReports();
    }

    /**
     * Returns updateBugReport.
     *
     * @param principal the principal
     * @param id the id
     * @param request the request
     * @return the result
     */
    @PatchMapping({"/admin/reports/{id}", "/reports/{id}"})
    public BasicResponse updateBugReport(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody BugReportUpdateRequest request
    ) {
        return service.updateBugReport(principal, id, request);
    }

    /**
     * Returns deleteBugReport.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @DeleteMapping({"/admin/reports/{id}", "/reports/{id}"})
    public BasicResponse deleteBugReport(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return service.deleteBugReport(principal, id);
    }

    /**
     * Opens a GitLab issue for this report, or returns the one it already has. Admin-only,
     * and matched explicitly in the security chain — {@code POST /reports} itself is the
     * student submission endpoint and stays open.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @PostMapping({"/admin/reports/{id}/gitlab-issue", "/reports/{id}/gitlab-issue"})
    public GitLabIssueResponse createGitLabIssue(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return service.createIssue(principal, id);
    }

    /**
     * Returns sendBugReport.
     *
     * @param authHeader the authHeader
     * @param request the request
     * @return the result
     */
    @PostMapping("/reports")
    public BasicResponse sendBugReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BugReportRequest request
    ) {
        return service.sendBugReport(authHeader, request);
    }
}

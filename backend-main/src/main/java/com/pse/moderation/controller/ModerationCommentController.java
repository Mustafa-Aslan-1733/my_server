package com.pse.moderation.controller;

import com.pse.shared.dto.BasicResponse;
import com.pse.moderation.dto.request.ContentUpdateRequest;
import com.pse.moderation.dto.request.UpdateStatusRequest;
import com.pse.moderation.dto.response.ManagedCommentsResponse;
import com.pse.moderation.dto.response.ReportedCommentsResponse;
import com.pse.moderation.service.ModerationCommentService;
import com.pse.moderation.service.ContentModerationService;
import com.pse.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Provides ModerationCommentController.
 */
@RestController
@RequestMapping({"/admin/comments", "/comments"})
public class ModerationCommentController {

    private final ModerationCommentService service;
    private final ContentModerationService contentService;

    /**
     * Creates ModerationCommentController.
     *
     * @param service the service
     * @param contentService the contentService
     */
    public ModerationCommentController(
            ModerationCommentService service,
            ContentModerationService contentService
    ) {
        this.service = service;
        this.contentService = contentService;
    }

    /**
     * Returns getComments.
     *
     * @return the result
     */
    @GetMapping
    public ManagedCommentsResponse getComments() {
        return contentService.getComments();
    }

    /**
     * Returns getReportedComments.
     *
     * @return the result
     */
    @GetMapping("/reported")
    public ReportedCommentsResponse getReportedComments() {
        return service.getReportedComments();
    }

    /**
     * Returns updateReportStatus.
     *
     * @param principal the principal
     * @param id the id
     * @param request the request
     * @return the result
     */
    @PatchMapping("/reported/{id}")
    public BasicResponse updateReportStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody UpdateStatusRequest request
    ) {
        return service.updateReportStatus(principal, id, request.status());
    }

    /**
     * Returns deleteReport.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @DeleteMapping("/reported/{id}")
    public BasicResponse deleteReport(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return service.deleteReport(principal, id);
    }

    /**
     * Returns updateComment.
     *
     * @param principal the principal
     * @param id the id
     * @param request the request
     * @return the result
     */
    @PatchMapping("/{id}")
    public BasicResponse updateComment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody ContentUpdateRequest request
    ) {
        return contentService.updateComment(principal, id, request);
    }

    /**
     * Returns deleteComment.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @DeleteMapping("/{id}")
    public BasicResponse deleteComment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return contentService.deleteComment(principal, id);
    }
}

package com.pse.moderation.controller;

import com.pse.shared.dto.BasicResponse;
import com.pse.moderation.dto.request.ContentUpdateRequest;
import com.pse.moderation.dto.request.UpdateStatusRequest;
import com.pse.moderation.dto.response.ManagedAnswersResponse;
import com.pse.moderation.dto.response.ReportedAnswersResponse;
import com.pse.moderation.service.ModerationAnswerReportService;
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
 * Provides ModerationAnswerController.
 */
@RestController
@RequestMapping({"/admin/answers", "/answers"})
public class ModerationAnswerController {

    private final ContentModerationService contentService;
    private final ModerationAnswerReportService reportService;

    /**
     * Creates ModerationAnswerController.
     *
     * @param contentService the contentService
     * @param reportService the reportService
     */
    public ModerationAnswerController(
            ContentModerationService contentService,
            ModerationAnswerReportService reportService
    ) {
        this.contentService = contentService;
        this.reportService = reportService;
    }

    /**
     * Returns getAnswers.
     *
     * @return the result
     */
    @GetMapping
    public ManagedAnswersResponse getAnswers() {
        return contentService.getAnswers();
    }

    /**
     * Returns getReportedAnswers.
     *
     * @return the result
     */
    @GetMapping("/reported")
    public ReportedAnswersResponse getReportedAnswers() {
        return reportService.getReportedAnswers();
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
        return reportService.updateReportStatus(principal, id, request.status());
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
        return reportService.deleteReport(principal, id);
    }

    /**
     * Returns updateAnswer.
     *
     * @param principal the principal
     * @param id the id
     * @param request the request
     * @return the result
     */
    @PatchMapping("/{id}")
    public BasicResponse updateAnswer(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody ContentUpdateRequest request
    ) {
        return contentService.updateAnswer(principal, id, request);
    }

    /**
     * Returns deleteAnswer.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @DeleteMapping("/{id}")
    public BasicResponse deleteAnswer(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return contentService.deleteAnswer(principal, id);
    }
}

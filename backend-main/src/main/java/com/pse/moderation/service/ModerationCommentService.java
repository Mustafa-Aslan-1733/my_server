package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import java.util.Set;
import com.pse.shared.repository.IdCount;
import com.pse.moderation.mapper.UserReferenceMapper;
import com.pse.lecture.mapper.LectureLabels;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.moderation.dto.response.ReportedCommentResponse;
import com.pse.moderation.dto.response.ReportedCommentsResponse;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.ContentStatus;
import com.pse.shared.enums.ReportStatus;
import com.pse.shared.error.ApiException;
import com.pse.shared.util.UtcDates;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentReport;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides ModerationCommentService.
 */
@Service
public class ModerationCommentService {

    private final CommentReportRepository reportRepository;
    private final CommentRepository commentRepository;
    private final WarningRepository warningRepository;
    private final AdminRepository adminRepository;
    private final AuditWriter auditWriter;
    private final Clock clock;

    /**
     * Creates ModerationCommentService.
     *
     * @param reportRepository the reportRepository
     * @param commentRepository the commentRepository
     * @param warningRepository the warningRepository
     * @param adminRepository the adminRepository
     * @param auditWriter the auditWriter
     * @param clock the clock
     */
    public ModerationCommentService(
            CommentReportRepository reportRepository,
            CommentRepository commentRepository,
            WarningRepository warningRepository,
            AdminRepository adminRepository,
            AuditWriter auditWriter,
            Clock clock
    ) {
        this.reportRepository = reportRepository;
        this.commentRepository = commentRepository;
        this.warningRepository = warningRepository;
        this.adminRepository = adminRepository;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    /**
     * Returns getReportedComments.
     *
     * @return the result
     */
    @Transactional(readOnly = true)
    public ReportedCommentsResponse getReportedComments() {
        Set<UUID> adminIds = Set.copyOf(adminRepository.findAllAdminStudentIds());
        Map<UUID, Integer> warningCounts =
                IdCount.asMap(warningRepository.countGroupedByStudent());

        List<ReportedCommentResponse> comments = reportRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(report -> toResponse(report, adminIds, warningCounts))
                .toList();
        return new ReportedCommentsResponse(
                "Success",
                true,
                comments
        );
    }

    /**
     * Returns updateReportStatus.
     *
     * @param principal the principal
     * @param reportId the reportId
     * @param status the status
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse updateReportStatus(
            AuthenticatedUser principal,
            UUID reportId,
            ReportStatus status
    ) {
        if (status == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report status");
        }
        CommentReport report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
        if (report.getStatus() == status) {
            return new BasicResponse("Updated", true);
        }

        ReportStatus before = report.getStatus();
        report.setStatus(status);
        report.setReviewedBy(principal.admin());
        report.setReviewedAt(LocalDateTime.now(clock));
        reportRepository.save(report);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", Map.of("before", before.name(), "after", status.name()));

        // ACTION_TAKEN is the only status that means "this content was moderated",
        // so it is the only one that hides the comment. Moving the report to any
        // other status puts the comment back. Answers are nested under the comment
        // in the student read path, so they disappear and return with it.
        Comment comment = report.getComment();
        ContentStatus commentBefore = comment.getStatus();
        ContentStatus commentAfter = ReportOutcome.visibilityFor(status);
        if (commentBefore != commentAfter) {
            comment.setStatus(commentAfter);
            commentRepository.save(comment);
            changes.put(
                    "commentStatus",
                    Map.of("before", commentBefore.name(), "after", commentAfter.name())
            );
        }

        auditWriter.write(
                principal.admin(),
                AuditAction.COMMENT_REPORT_STATUS_CHANGED,
                AuditTargetType.COMMENT_REPORT,
                report.getId(),
                "Report about " + report.getComment().getStudent().getUsername()
                        + " in " + postContext(report),
                changes,
                Map.of()
        );
        return new BasicResponse("Updated", true);
    }

    /**
     * Removes the report record itself. The reported comment is untouched: withdrawing a
     * report that should not have been filed is not a judgement on the content.
     *
     * @param principal the principal
     * @param reportId the reportId
     * @return the result
     */
    @Transactional
    public BasicResponse deleteReport(AuthenticatedUser principal, UUID reportId) {
        CommentReport report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
        String label = "Report about " + report.getComment().getStudent().getUsername()
                + " in " + postContext(report);
        ReportStatus status = report.getStatus();

        reportRepository.delete(report);

        Map<String, Object> changes = AuditWriter.lifecycle(true, false);
        auditWriter.write(
                principal.admin(),
                AuditAction.COMMENT_REPORT_DELETED,
                AuditTargetType.COMMENT_REPORT,
                reportId,
                label,
                changes,
                Map.of("status", status.name())
        );
        return new BasicResponse("Deleted report successfully", true);
    }

    private ReportedCommentResponse toResponse(
            CommentReport report,
            Set<UUID> adminIds,
            Map<UUID, Integer> warningCounts
    ) {
        return new ReportedCommentResponse(
                report.getId(),
                report.getComment().getId(),
                report.getComment().getContent(),
                postContext(report),
                report.getExplanation() == null ? "" : report.getExplanation(),
                UserReferenceMapper.toReference(report.getComment().getStudent(), adminIds, warningCounts),
                UserReferenceMapper.toReference(report.getReporter(), adminIds, warningCounts),
                report.getReason(),
                report.getStatus(),
                UtcDates.format(report.getCreatedAt())
        );
    }

    private String postContext(CommentReport report) {
        return LectureLabels.of(report.getComment().getLecture());
    }
}

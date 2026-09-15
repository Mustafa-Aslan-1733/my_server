package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.lecture.mapper.LectureLabels;
import com.pse.moderation.dto.request.ContentUpdateRequest;
import com.pse.moderation.dto.response.ManagedAnswerResponse;
import com.pse.moderation.dto.response.ManagedAnswersResponse;
import com.pse.moderation.dto.response.ManagedCommentResponse;
import com.pse.moderation.dto.response.ManagedCommentsResponse;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.ContentStatus;
import com.pse.shared.error.ApiException;
import com.pse.shared.repository.IdCount;
import com.pse.shared.util.UtcDates;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.service.AnswerCleanup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pse.moderation.mapper.UserReferenceMapper;

/**
 * What an administrator may do to a question or an answer: read the moderation listing, edit
 * the text or the visibility, remove it.
 *
 * <p>Ratings used to be in here too. They are a different table reached by a different
 * controller, and the only thing the two shared was this class -- see
 * {@code RatingModerationService}.
 */
@Service
public class ContentModerationService {

    private static final int MAX_CONTENT_LENGTH = 10_000;

    private static final int PREVIEW_LENGTH = 120;

    private final CommentRepository commentRepository;
    private final AnswerRepository answerRepository;
    private final CommentReportRepository commentReportRepository;
    private final AnswerReportRepository answerReportRepository;
    private final AnswerCleanup answerCleanup;
    private final WarningRepository warningRepository;
    private final AdminRepository adminRepository;
    private final AuditWriter auditWriter;

    /**
     * Creates ContentModerationService.
     *
     * @param commentRepository the commentRepository
     * @param answerRepository the answerRepository
     * @param commentReportRepository the commentReportRepository
     * @param answerReportRepository the answerReportRepository
     * @param answerCleanup the answerCleanup
     * @param warningRepository the warningRepository
     * @param adminRepository the adminRepository
     * @param auditWriter the auditWriter
     */
    public ContentModerationService(
            CommentRepository commentRepository,
            AnswerRepository answerRepository,
            CommentReportRepository commentReportRepository,
            AnswerReportRepository answerReportRepository,
            AnswerCleanup answerCleanup,
            WarningRepository warningRepository,
            AdminRepository adminRepository,
            AuditWriter auditWriter
    ) {
        this.commentRepository = commentRepository;
        this.answerRepository = answerRepository;
        this.commentReportRepository = commentReportRepository;
        this.answerReportRepository = answerReportRepository;
        this.answerCleanup = answerCleanup;
        this.warningRepository = warningRepository;
        this.adminRepository = adminRepository;
        this.auditWriter = auditWriter;
    }

    /**
     * Returns getComments.
     *
     * @return the result
     */
    @Transactional(readOnly = true)
    public ManagedCommentsResponse getComments() {
        // Batch queries rather than one per row: the counts and the role lookup would
        // otherwise be three extra queries per comment.
        Set<UUID> adminIds = Set.copyOf(adminRepository.findAllAdminStudentIds());
        Map<UUID, Integer> warningCounts = IdCount.asMap(warningRepository.countGroupedByStudent());
        Map<UUID, Integer> answerCounts = IdCount.asMap(answerRepository.countGroupedByComment());
        Map<UUID, Integer> reportCounts = IdCount.asMap(commentReportRepository.countGroupedByComment());

        List<ManagedCommentResponse> comments = commentRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(comment -> new ManagedCommentResponse(
                        comment.getId(),
                        comment.getContent(),
                        comment.getStatus(),
                        UserReferenceMapper.toReference(comment.getStudent(), adminIds, warningCounts),
                        LectureLabels.of(comment.getLecture()),
                        comment.getLecture().getId(),
                        answerCounts.getOrDefault(comment.getId(), 0),
                        reportCounts.getOrDefault(comment.getId(), 0),
                        UtcDates.format(comment.getCreatedAt())
                ))
                .toList();
        return new ManagedCommentsResponse("Successfully found all comments", true, comments);
    }

    /**
     * Returns updateComment.
     *
     * @param principal the principal
     * @param commentId the commentId
     * @param request the request
     * @return the result
     */
    @Transactional
    public BasicResponse updateComment(
            AuthenticatedUser principal,
            UUID commentId,
            ContentUpdateRequest request
    ) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Comment not found"));
        Map<String, Object> changes = contentChanges(
                request,
                comment.getContent(),
                comment.getStatus(),
                comment::setContent,
                comment::setStatus
        );
        if (changes.isEmpty()) {
            return new BasicResponse("Updated comment successfully", true);
        }

        commentRepository.save(comment);
        auditWriter.write(
                principal.admin(),
                AuditAction.COMMENT_UPDATED,
                AuditTargetType.COMMENT,
                comment.getId(),
                "Comment by " + comment.getStudent().getUsername()
                        + " in " + LectureLabels.of(comment.getLecture()),
                changes,
                Map.of()
        );
        return new BasicResponse("Updated comment successfully", true);
    }

    /**
     * Removes a comment and the thread under it. This is a real delete, not a hide: use
     * {@code status = HIDDEN} to take content out of the student app while keeping it.
     *
     * @param principal the principal
     * @param commentId the commentId
     * @return the result
     */
    @Transactional
    public BasicResponse deleteComment(AuthenticatedUser principal, UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Comment not found"));
        String label = "Comment by " + comment.getStudent().getUsername()
                + " in " + LectureLabels.of(comment.getLecture());

        List<Answer> answers = answerRepository.findAllByComment(comment);
        int answerCount = answers.size();
        answerCleanup.clear(answers.stream().map(Answer::getId).toList());

        // Votes, answers and reports are cascaded from the comment; the two tables
        // cleared above are the ones that are not.
        commentRepository.delete(comment);

        Map<String, Object> changes = AuditWriter.lifecycle(true, false);
        auditWriter.write(
                principal.admin(),
                AuditAction.COMMENT_DELETED,
                AuditTargetType.COMMENT,
                commentId,
                label,
                changes,
                Map.of("deletedAnswers", answerCount)
        );
        return new BasicResponse("Deleted comment successfully", true);
    }

    /**
     * Returns getAnswers.
     *
     * @return the result
     */
    @Transactional(readOnly = true)
    public ManagedAnswersResponse getAnswers() {
        Set<UUID> adminIds = Set.copyOf(adminRepository.findAllAdminStudentIds());
        Map<UUID, Integer> warningCounts = IdCount.asMap(warningRepository.countGroupedByStudent());
        Map<UUID, Integer> reportCounts = IdCount.asMap(answerReportRepository.countGroupedByAnswer());

        List<ManagedAnswerResponse> answers = answerRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(answer -> new ManagedAnswerResponse(
                        answer.getId(),
                        answer.getContent(),
                        answer.getStatus(),
                        UserReferenceMapper.toReference(answer.getStudent(), adminIds, warningCounts),
                        answer.getComment().getId(),
                        preview(answer.getComment().getContent()),
                        LectureLabels.of(answer.getComment().getLecture()),
                        reportCounts.getOrDefault(answer.getId(), 0),
                        UtcDates.format(answer.getCreatedAt())
                ))
                .toList();
        return new ManagedAnswersResponse("Successfully found all answers", true, answers);
    }

    /**
     * Returns updateAnswer.
     *
     * @param principal the principal
     * @param answerId the answerId
     * @param request the request
     * @return the result
     */
    @Transactional
    public BasicResponse updateAnswer(
            AuthenticatedUser principal,
            UUID answerId,
            ContentUpdateRequest request
    ) {
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Answer not found"));
        Map<String, Object> changes = contentChanges(
                request,
                answer.getContent(),
                answer.getStatus(),
                answer::setContent,
                answer::setStatus
        );
        if (changes.isEmpty()) {
            return new BasicResponse("Updated answer successfully", true);
        }

        answerRepository.save(answer);
        auditWriter.write(
                principal.admin(),
                AuditAction.ANSWER_UPDATED,
                AuditTargetType.ANSWER,
                answer.getId(),
                "Answer by " + answer.getStudent().getUsername()
                        + " in " + LectureLabels.of(answer.getComment().getLecture()),
                changes,
                Map.of()
        );
        return new BasicResponse("Updated answer successfully", true);
    }

    /**
     * Returns deleteAnswer.
     *
     * @param principal the principal
     * @param answerId the answerId
     * @return the result
     */
    @Transactional
    public BasicResponse deleteAnswer(AuthenticatedUser principal, UUID answerId) {
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Answer not found"));
        String label = "Answer by " + answer.getStudent().getUsername()
                + " in " + LectureLabels.of(answer.getComment().getLecture());

        answerCleanup.clear(List.of(answerId));
        answerRepository.delete(answer);

        Map<String, Object> changes = AuditWriter.lifecycle(true, false);
        auditWriter.write(
                principal.admin(),
                AuditAction.ANSWER_DELETED,
                AuditTargetType.ANSWER,
                answerId,
                label,
                changes,
                Map.of()
        );
        return new BasicResponse("Deleted answer successfully", true);
    }

    private Map<String, Object> contentChanges(
            ContentUpdateRequest request,
            String currentContent,
            ContentStatus currentStatus,
            java.util.function.Consumer<String> applyContent,
            java.util.function.Consumer<ContentStatus> applyStatus
    ) {
        if (request == null || (request.content() == null && request.status() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No update supplied");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.content() != null) {
            String content = request.content().trim();
            if (content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid content");
            }
            if (!content.equals(currentContent)) {
                changes.put("content", AuditWriter.value(preview(currentContent), preview(content)));
                applyContent.accept(content);
            }
        }
        if (request.status() != null && request.status() != currentStatus) {
            changes.put("status", AuditWriter.value(currentStatus.name(), request.status().name()));
            applyStatus.accept(request.status());
        }
        return changes;
    }

    private static String preview(String content) {
        String text = content == null ? "" : content;
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
    }
}

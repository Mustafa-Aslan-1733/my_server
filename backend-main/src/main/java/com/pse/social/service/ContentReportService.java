package com.pse.social.service;

import java.util.Map;

import com.pse.shared.util.Uuids;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.lecture.mapper.LectureLabels;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.ReportReason;
import com.pse.shared.error.ApiException;
import com.pse.social.dto.request.AnswerReportRequest;
import com.pse.social.dto.request.CommentReportRequest;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerReport;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentReport;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.user.model.Student;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student flagging a question or an answer for a moderator to look at.
 *
 * <p>The two methods are near-identical and, like the two in {@link VoteService}, are left
 * that way: {@code CommentReport} and {@code AnswerReport} are separate tables reached
 * through separate repositories, and the lecture a report is about is one navigation step
 * away in one case and two in the other.
 */
@Service
public class ContentReportService {

    private final CommentRepository commentRepository;
    private final AnswerRepository answerRepository;
    private final CommentReportRepository commentReportRepository;
    private final AnswerReportRepository answerReportRepository;
    private final AuditWriter auditWriter;

    /**
     * Creates ContentReportService.
     *
     * @param commentRepository the commentRepository
     * @param answerRepository the answerRepository
     * @param commentReportRepository the commentReportRepository
     * @param answerReportRepository the answerReportRepository
     * @param auditWriter the auditWriter
     */
    public ContentReportService(
            CommentRepository commentRepository,
            AnswerRepository answerRepository,
            CommentReportRepository commentReportRepository,
            AnswerReportRepository answerReportRepository,
            AuditWriter auditWriter
    ) {
        this.commentRepository = commentRepository;
        this.answerRepository = answerRepository;
        this.commentReportRepository = commentReportRepository;
        this.answerReportRepository = answerReportRepository;
        this.auditWriter = auditWriter;
    }

    /**
     * Returns submitCommentReport.
     *
     * @param student the student
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse submitCommentReport(Student student, CommentReportRequest request) {

        Comment comment = commentRepository
                .findById(Uuids.parse(request.commentID(), "Invalid comment id"))
                .orElse(null);
        if (comment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Comment not found");
        }

        CommentReport report = new CommentReport();
        report.setReporter(student);
        report.setComment(comment);
        report.setReason(request.reportReason());
        report.setExplanation(request.explanation());

        commentReportRepository.save(report);

        auditWriter.writeStudentAction(
                student,
                AuditAction.COMMENT_REPORT_CREATED,
                AuditTargetType.COMMENT_REPORT,
                report.getId(),
                "Report about " + comment.getStudent().getUsername()
                        + " in " + LectureLabels.of(comment.getLecture()),
                reasonMetadata(request.reportReason())
        );

        return new BasicResponse("Comment Report submitted successfully", true);
    }

    /**
     * Returns submitAnswerReport.
     *
     * @param student the student
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse submitAnswerReport(Student student, AnswerReportRequest request) {

        Answer answer = answerRepository
                .findById(Uuids.parse(request.answerID(), "Invalid answer id"))
                .orElse(null);
        if (answer == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Answer not found");
        }

        AnswerReport report = new AnswerReport();
        report.setReporter(student);
        report.setAnswer(answer);
        report.setReason(request.reportReason());
        report.setExplanation(request.explanation());

        answerReportRepository.save(report);

        auditWriter.writeStudentAction(
                student,
                AuditAction.ANSWER_REPORT_CREATED,
                AuditTargetType.ANSWER_REPORT,
                report.getId(),
                "Report about " + answer.getStudent().getUsername()
                        + " in " + LectureLabels.of(answer.getComment().getLecture()),
                reasonMetadata(request.reportReason())
        );

        return new BasicResponse("Answer Report submitted successfully", true);
    }

    /**
     * Null-safe on purpose: the reason is validated at the boundary (F-2), and the audit
     * write must not be the thing that turns a request that slipped past it into an NPE.
     */
    private static Map<String, Object> reasonMetadata(ReportReason reason) {
        return Map.of("reason", reason == null ? "UNSPECIFIED" : reason.name());
    }
}

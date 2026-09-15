package com.pse.social.service;

import java.util.Map;
import java.util.UUID;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.lecture.mapper.LectureLabels;
import com.pse.lecture.model.Lecture;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.VoteType;
import com.pse.shared.error.ApiException;
import com.pse.social.dto.request.VoteAnswerRequest;
import com.pse.social.dto.request.VoteCommentRequest;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerVote;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentVote;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.CommentVoteRepository;
import com.pse.user.model.Student;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Up- and down-votes on questions and on answers.
 *
 * <p><b>The two methods below are the same shape twice, and are deliberately not merged.</b>
 * {@code CommentVote} and {@code AnswerVote} are separate tables with separate join columns
 * and no common supertype, and their repositories have separate finders, so a shared body
 * would have to be handed the lookup, the delete, the constructor, the save, the audit
 * constants and both response strings -- six or seven function arguments to save forty lines,
 * and unreadable at the point where the vote rules actually live. Giving the two entities a
 * common JPA supertype instead would be a schema change, which this is not.
 *
 * <p>What they do share is extracted: the audit write and the label it carries.
 */
@Service
public class VoteService {

    private final CommentRepository commentRepository;
    private final AnswerRepository answerRepository;
    private final CommentVoteRepository commentVoteRepository;
    private final AnswerVoteRepository answerVoteRepository;
    private final AuditWriter auditWriter;

    /**
     * Creates VoteService.
     *
     * @param commentRepository the commentRepository
     * @param answerRepository the answerRepository
     * @param commentVoteRepository the commentVoteRepository
     * @param answerVoteRepository the answerVoteRepository
     * @param auditWriter the auditWriter
     */
    public VoteService(
            CommentRepository commentRepository,
            AnswerRepository answerRepository,
            CommentVoteRepository commentVoteRepository,
            AnswerVoteRepository answerVoteRepository,
            AuditWriter auditWriter
    ) {
        this.commentRepository = commentRepository;
        this.answerRepository = answerRepository;
        this.commentVoteRepository = commentVoteRepository;
        this.answerVoteRepository = answerVoteRepository;
        this.auditWriter = auditWriter;
    }

    /**
     * Transactional so the vote and the audit entry commit together -- {@link AuditWriter}
     * joins the caller's transaction on purpose, and a vote recorded without its entry is
     * exactly the blind spot F-1 describes.
     *
     * @param commentId the commentId
     * @param student the student
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse voteComment(UUID commentId, Student student, VoteCommentRequest request) {

        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Comment not found");
        }

        CommentVote commentVote = commentVoteRepository
                .findByStudentAndComment(student, comment)
                .orElse(null);
        String label = voteLabel(comment.getStudent(), comment.getLecture());

        // F-3: NONE withdraws the vote rather than recording a third direction, so the row
        // goes away and the column never has to hold it.
        if (request.voteType() == VoteType.NONE) {
            if (commentVote != null) {
                commentVoteRepository.delete(commentVote);
            }
            writeVoteAudit(student, AuditAction.COMMENT_VOTED, AuditTargetType.COMMENT,
                    comment.getId(), label, VoteType.NONE);
            return new BasicResponse("Comment vote withdrawn successfully", true);
        }

        if (commentVote == null) {
            commentVote = new CommentVote();
            commentVote.setStudent(student);
            commentVote.setComment(comment);
        }
        commentVote.setVote(request.voteType());
        commentVoteRepository.save(commentVote);

        writeVoteAudit(student, AuditAction.COMMENT_VOTED, AuditTargetType.COMMENT,
                comment.getId(), label, request.voteType());

        return new BasicResponse("Comment vote saved successfully", true);
    }

    /**
     * The answer half of {@link #voteComment}, transactional for the same reason.
     *
     * @param answerId the answerId
     * @param student the student
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse voteAnswer(UUID answerId, Student student, VoteAnswerRequest request) {

        Answer answer = answerRepository.findById(answerId).orElse(null);
        if (answer == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Answer not found");
        }

        AnswerVote answerVote = answerVoteRepository
                .findByStudentAndAnswer(student, answer)
                .orElse(null);
        String label = voteLabel(answer.getStudent(), answer.getComment().getLecture());

        if (request.voteType() == VoteType.NONE) {
            if (answerVote != null) {
                answerVoteRepository.delete(answerVote);
            }
            writeVoteAudit(student, AuditAction.ANSWER_VOTED, AuditTargetType.ANSWER,
                    answer.getId(), label, VoteType.NONE);
            return new BasicResponse("Answer vote withdrawn successfully", true);
        }

        if (answerVote == null) {
            answerVote = new AnswerVote();
            answerVote.setStudent(student);
            answerVote.setAnswer(answer);
        }
        answerVote.setVote(request.voteType());
        answerVoteRepository.save(answerVote);

        writeVoteAudit(student, AuditAction.ANSWER_VOTED, AuditTargetType.ANSWER,
                answer.getId(), label, request.voteType());

        return new BasicResponse("Answer vote saved successfully", true);
    }

    /**
     * F-1. Every other write in this area recorded itself; the two vote methods did not, so
     * an account flipping hundreds of votes left no trace on the moderation side at all.
     * The direction is metadata rather than a change set, because a vote has no "before":
     * an overwrite and a first vote are the same action from the log's point of view.
     */
    private void writeVoteAudit(
            Student student,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            VoteType vote
    ) {
        auditWriter.writeStudentAction(
                student,
                action,
                targetType,
                targetId,
                targetLabel,
                // Null-safe for the same reason the report writes are: the vote type is
                // validated at the boundary (F-2), and the audit write must not be the thing
                // that turns a request that slipped past it into an NPE.
                Map.of("vote", vote == null ? "UNSPECIFIED" : vote.name())
        );
    }

    private String voteLabel(Student author, Lecture lecture) {
        return "Vote on " + author.getUsername() + " in " + LectureLabels.of(lecture);
    }
}

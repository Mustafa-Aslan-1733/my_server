package com.pse.social.service;


import com.pse.shared.util.Uuids;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.lecture.mapper.LectureLabels;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.NotificationType;
import com.pse.shared.error.ApiException;
import com.pse.social.dto.request.AnswerSubmitRequest;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.model.Notification;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.user.model.Student;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replies to a question, and the notification the question's author gets for one.
 */
@Service
public class AnswerService {

    private final CommentRepository commentRepository;
    private final AnswerRepository answerRepository;
    private final NotificationRepository notificationRepository;
    private final AuditWriter auditWriter;

    /**
     * Creates AnswerService.
     *
     * @param commentRepository the commentRepository
     * @param answerRepository the answerRepository
     * @param notificationRepository the notificationRepository
     * @param auditWriter the auditWriter
     */
    public AnswerService(
            CommentRepository commentRepository,
            AnswerRepository answerRepository,
            NotificationRepository notificationRepository,
            AuditWriter auditWriter
    ) {
        this.commentRepository = commentRepository;
        this.answerRepository = answerRepository;
        this.notificationRepository = notificationRepository;
        this.auditWriter = auditWriter;
    }

    /**
     * Returns submitAnswer.
     *
     * @param student the student
     * @param request the request
     * @throws ApiException if comment not found
     * @return the result
     */
    @Transactional
    public BasicResponse submitAnswer(Student student, AnswerSubmitRequest request) {

        Comment comment = commentRepository
                .findById(Uuids.parse(request.commentID(), "Invalid comment id"))
                .orElse(null);
        if (comment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Comment not found");
        }

        Answer answer = new Answer();
        answer.setStudent(student);
        answer.setContent(request.content());
        answer.setComment(comment);

        answerRepository.save(answer);

        Notification notification = new Notification();
        notification.setRecipient(comment.getStudent());
        notification.setLecture(comment.getLecture());
        notification.setOwnComment(comment);
        notification.setType(NotificationType.ANSWER);
        notification.setAnswer(answer);
        notificationRepository.save(notification);

        auditWriter.writeStudentAction(
                student,
                AuditAction.ANSWER_CREATED,
                AuditTargetType.ANSWER,
                answer.getId(),
                "Answer to " + comment.getStudent().getUsername()
                        + " in " + LectureLabels.of(comment.getLecture()),
                ContentAudit.metadataFor(request.content())
        );

        return new BasicResponse("Answer submitted successfully", true);
    }
}

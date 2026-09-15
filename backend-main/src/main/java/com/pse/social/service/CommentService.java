package com.pse.social.service;

import java.util.List;
import java.util.UUID;

import com.pse.shared.util.Uuids;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.lecture.mapper.LectureLabels;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.service.LectureService;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.ContentStatus;
import com.pse.shared.error.ApiException;
import com.pse.social.dto.request.CommentSubmitRequest;
import com.pse.social.dto.response.CommentResponse;
import com.pse.social.dto.response.CommentsResponse;
import com.pse.social.mapper.SocialResponseMapper;
import com.pse.social.model.Comment;
import com.pse.social.repository.CommentRepository;
import com.pse.user.model.Student;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Questions students post under a lecture, and the two ways they are read back.
 */
@Service
public class CommentService {

    private final LectureService lectureService;
    private final CommentRepository commentRepository;
    private final SocialResponseMapper mapper;
    private final AuditWriter auditWriter;

    /**
     * Creates CommentService.
     *
     * @param lectureService the lectureService
     * @param commentRepository the commentRepository
     * @param mapper the mapper
     * @param auditWriter the auditWriter
     */
    public CommentService(
            LectureService lectureService,
            CommentRepository commentRepository,
            SocialResponseMapper mapper,
            AuditWriter auditWriter
    ) {
        this.lectureService = lectureService;
        this.commentRepository = commentRepository;
        this.mapper = mapper;
        this.auditWriter = auditWriter;
    }

    /**
     * Returns submitComment.
     *
     * @param student the student
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse submitComment(Student student, CommentSubmitRequest request) {

        Lecture lecture = lectureService.getById(
                Uuids.parse(request.lectureID(), "Invalid lecture id"));
        if (lecture == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Lecture not found");
        }

        Comment comment = new Comment();
        comment.setStudent(student);
        comment.setContent(request.content());
        comment.setLecture(lecture);

        commentRepository.save(comment);

        auditWriter.writeStudentAction(
                student,
                AuditAction.COMMENT_CREATED,
                AuditTargetType.COMMENT,
                comment.getId(),
                LectureLabels.of(lecture),
                ContentAudit.metadataFor(request.content())
        );

        return new BasicResponse("Comment submitted successfully", true);
    }

    /**
     * Only VISIBLE comments reach the student app. A comment is set to HIDDEN when an admin
     * resolves a report against it as ACTION_TAKEN.
     *
     * @param lectureId the lectureId
     * @param student the student
     * @return the result
     */
    @Transactional(readOnly = true)
    public CommentsResponse getComments(UUID lectureId, Student student) {

        List<CommentResponse> comments = commentRepository
                .findAllByLectureIdAndStatusOrderByCreatedAtDesc(lectureId, ContentStatus.VISIBLE)
                .stream()
                .map(comment -> mapper.toResponse(comment, student))
                .toList();

        return new CommentsResponse("Found " + comments.size() + " comments", true, comments);
    }

    /**
     * The app's sync route: every visible comment, with nobody's own vote resolved.
     *
     * @return the result
     */
    @Transactional(readOnly = true)
    public CommentsResponse getAllComments() {

        List<CommentResponse> comments = commentRepository
                .findAllByStatusOrderByCreatedAtDesc(ContentStatus.VISIBLE)
                .stream()
                .map(comment -> mapper.toResponse(comment, null))
                .toList();

        return new CommentsResponse("Comments loaded successfully", true, comments);
    }
}

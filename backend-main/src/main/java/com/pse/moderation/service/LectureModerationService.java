package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.lecture.mapper.LectureLabels;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.dto.request.LectureUpdateRequest;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.error.ApiException;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.service.AnswerCleanup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an administrator may change about a lecture: its name, code, semester, type, whether
 * it is listed, and who teaches it.
 */
@Service
public class LectureModerationService {

    private final LectureRepository lectureRepository;
    private final ProfessorRepository professorRepository;
    private final AnswerRepository answerRepository;
    private final AnswerCleanup answerCleanup;
    private final AuditWriter auditWriter;

    /**
     * Creates LectureModerationService.
     *
     * @param lectureRepository the lectureRepository
     * @param professorRepository the professorRepository
     * @param answerRepository the answerRepository
     * @param answerCleanup the answerCleanup
     * @param auditWriter the auditWriter
     */
    public LectureModerationService(
            LectureRepository lectureRepository,
            ProfessorRepository professorRepository,
            AnswerRepository answerRepository,
            AnswerCleanup answerCleanup,
            AuditWriter auditWriter
    ) {
        this.lectureRepository = lectureRepository;
        this.professorRepository = professorRepository;
        this.answerRepository = answerRepository;
        this.answerCleanup = answerCleanup;
        this.auditWriter = auditWriter;
    }

    /**
     * Returns updateLecture.
     *
     * @param principal the principal
     * @param lectureId the lectureId
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse updateLecture(
            AuthenticatedUser principal,
            UUID lectureId,
            LectureUpdateRequest request
    ) {
        if (request == null
                || (request.name() == null
                && request.code() == null
                && request.semesterYear() == null
                && request.semesterSeason() == null
                && request.active() == null
                && request.lectureType() == null
                && request.professorIds() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No update supplied");
        }

        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lecture not found"));
        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty() || name.length() > 300) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid lecture name");
            }
            if (!name.equals(lecture.getName())) {
                changes.put("name", AuditWriter.value(lecture.getName(), name));
                lecture.setName(name);
            }
        }
        if (request.code() != null) {
            String code = request.code().trim();
            if (code.length() > 100) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid lecture code");
            }
            String before = lecture.getCode() == null ? "" : lecture.getCode();
            if (!code.equals(before)) {
                changes.put("code", AuditWriter.value(before, code));
                lecture.setCode(code);
            }
        }
        if (request.semesterYear() != null && request.semesterYear() != lecture.getSemesterYear()) {
            int year = request.semesterYear();
            if (year < 1900 || year > 2200) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid semester year");
            }
            changes.put("semesterYear", AuditWriter.value(lecture.getSemesterYear(), year));
            lecture.setSemesterYear(year);
        }
        if (request.semesterSeason() != null
                && request.semesterSeason() != lecture.getSemesterSeason()) {
            changes.put("semesterSeason",
                    AuditWriter.value(lecture.getSemesterSeason().name(), request.semesterSeason().name()));
            lecture.setSemesterSeason(request.semesterSeason());
        }
        if (request.active() != null && request.active() != lecture.isActive()) {
            changes.put("active", AuditWriter.value(lecture.isActive(), request.active()));
            lecture.setActive(request.active());
        }
        if (request.lectureType() != null && request.lectureType() != lecture.getLectureType()) {
            changes.put("lectureType",
                    AuditWriter.value(lecture.getLectureType().name(), request.lectureType().name()));
            lecture.setLectureType(request.lectureType());
        }
        if (request.professorIds() != null) {
            List<Professor> professors = CatalogueEdits.resolveAll(
                    request.professorIds(), professorRepository::findAllById, "Professor not found");
            List<UUID> before = lecture.getProfessors().stream().map(Professor::getId).toList();
            List<UUID> after = professors.stream().map(Professor::getId).toList();
            // Compared as a multiset -- BUG-3. lecture.getProfessors() is a Set and the
            // resolved list carries the order of the request, so comparing by position made
            // an unchanged assignment look edited and wrote a bogus audit event for it.
            if (!CatalogueEdits.sameAssignment(before, after)) {
                changes.put("professors", AuditWriter.value(CatalogueEdits.labels(before), CatalogueEdits.labels(after)));
                lecture.getProfessors().clear();
                lecture.getProfessors().addAll(professors);
            }
        }

        if (changes.isEmpty()) {
            return new BasicResponse("Updated lecture successfully", true);
        }

        lectureRepository.save(lecture);
        auditWriter.write(
                principal.admin(),
                AuditAction.LECTURE_UPDATED,
                AuditTargetType.LECTURE,
                lecture.getId(),
                LectureLabels.of(lecture),
                changes,
                Map.of()
        );
        return new BasicResponse("Updated lecture successfully", true);
    }

    /**
     * Removes a lecture together with every comment, answer and rating filed under it.
     *
     * <p>This throws away student contributions, which is rarely what is wanted — setting
     * {@code active = false} takes a lecture out of the app while keeping its history.
     * Deleting is for a lecture that should never have existed.
     *
     * @param principal the principal
     * @param lectureId the lectureId
     * @return the result
     */
    @Transactional
    public BasicResponse deleteLecture(AuthenticatedUser principal, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lecture not found"));
        String label = LectureLabels.of(lecture);
        int comments = lecture.getComments().size();
        int ratings = lecture.getRatings().size();

        // Comments and ratings are cascaded from the lecture, and answers from their
        // comment; answer votes and notifications are not cascaded from anything here.
        answerCleanup.clear(answerRepository.findIdsByLectureId(lectureId));

        // The professor assignment is a join table owned by the lecture, so its rows go
        // with it. Clearing it explicitly keeps the in-memory state consistent first.
        lecture.getProfessors().clear();
        lectureRepository.delete(lecture);

        Map<String, Object> changes = AuditWriter.lifecycle(true, false);
        auditWriter.write(
                principal.admin(),
                AuditAction.LECTURE_DELETED,
                AuditTargetType.LECTURE,
                lectureId,
                label,
                changes,
                Map.of("deletedComments", comments, "deletedRatings", ratings)
        );
        return new BasicResponse("Deleted lecture successfully", true);
    }
}

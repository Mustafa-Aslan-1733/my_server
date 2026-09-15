package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.dto.request.ProfessorUpdateRequest;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.error.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an administrator may change about a professor: their name, whether they are listed,
 * and which lectures they are assigned to.
 */
@Service
public class ProfessorModerationService {

    private final ProfessorRepository professorRepository;
    private final LectureRepository lectureRepository;
    private final AuditWriter auditWriter;

    /**
     * Creates ProfessorModerationService.
     *
     * @param professorRepository the professorRepository
     * @param lectureRepository the lectureRepository
     * @param auditWriter the auditWriter
     */
    public ProfessorModerationService(
            ProfessorRepository professorRepository,
            LectureRepository lectureRepository,
            AuditWriter auditWriter
    ) {
        this.professorRepository = professorRepository;
        this.lectureRepository = lectureRepository;
        this.auditWriter = auditWriter;
    }

    /**
     * Returns updateProfessor.
     *
     * @param principal the principal
     * @param professorId the professorId
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse updateProfessor(
            AuthenticatedUser principal,
            UUID professorId,
            ProfessorUpdateRequest request
    ) {
        if (request == null
                || (request.firstName() == null
                && request.lastName() == null
                && request.active() == null
                && request.lectureIds() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No update supplied");
        }

        Professor professor = professorRepository.findById(professorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Professor not found"));
        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.firstName() != null) {
            String firstName = CatalogueEdits.requireName(request.firstName(), "Invalid first name");
            if (!firstName.equals(professor.getFirstName())) {
                changes.put("firstName", AuditWriter.value(professor.getFirstName(), firstName));
                professor.setFirstName(firstName);
            }
        }
        if (request.lastName() != null) {
            String lastName = CatalogueEdits.requireName(request.lastName(), "Invalid last name");
            if (!lastName.equals(professor.getLastName())) {
                changes.put("lastName", AuditWriter.value(professor.getLastName(), lastName));
                professor.setLastName(lastName);
            }
        }
        if (request.active() != null && request.active() != professor.isActive()) {
            changes.put("active", AuditWriter.value(professor.isActive(), request.active()));
            professor.setActive(request.active());
        }
        if (request.lectureIds() != null) {
            changes.putAll(reassignLectures(professor, request.lectureIds()));
        }

        if (changes.isEmpty()) {
            return new BasicResponse("Updated professor successfully", true);
        }

        professorRepository.save(professor);
        auditWriter.write(
                principal.admin(),
                AuditAction.PROFESSOR_UPDATED,
                AuditTargetType.PROFESSOR,
                professor.getId(),
                professorLabel(professor),
                changes,
                Map.of()
        );
        return new BasicResponse("Updated professor successfully", true);
    }

    /**
     * Removes a professor. Lectures survive — they are only detached, since a lecture
     * without a listed professor is still a lecture people rated.
     *
     * @param principal the principal
     * @param professorId the professorId
     * @return the result
     */
    @Transactional
    public BasicResponse deleteProfessor(AuthenticatedUser principal, UUID professorId) {
        Professor professor = professorRepository.findById(professorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Professor not found"));
        String label = professorLabel(professor);

        // The join table is owned by Lecture, so the rows have to be removed from that
        // side; deleting the inverse side alone leaves them pointing at nothing.
        int detached = professor.getLectures().size();
        reassignLectures(professor, List.of());
        professorRepository.delete(professor);

        Map<String, Object> changes = AuditWriter.lifecycle(true, false);
        auditWriter.write(
                principal.admin(),
                AuditAction.PROFESSOR_DELETED,
                AuditTargetType.PROFESSOR,
                professorId,
                label,
                changes,
                Map.of("detachedLectures", detached)
        );
        return new BasicResponse("Deleted professor successfully", true);
    }

    /** Rewrites the professor's lecture assignment from the owning (lecture) side. */
    private Map<String, Object> reassignLectures(Professor professor, List<UUID> lectureIds) {
        List<Lecture> after = CatalogueEdits.resolveAll(lectureIds, lectureRepository::findAllById, "Lecture not found");
        List<UUID> beforeIds = professor.getLectures().stream().map(Lecture::getId).toList();
        List<UUID> afterIds = after.stream().map(Lecture::getId).toList();
        // The mirror image of the professor assignment above, and unordered for the same
        // reason: professor.getLectures() is a Set.
        if (CatalogueEdits.sameAssignment(beforeIds, afterIds)) {
            return Map.of();
        }

        for (Lecture lecture : new ArrayList<>(professor.getLectures())) {
            if (!afterIds.contains(lecture.getId())) {
                lecture.getProfessors().removeIf(held -> held.getId().equals(professor.getId()));
                lectureRepository.save(lecture);
            }
        }
        for (Lecture lecture : after) {
            if (lecture.getProfessors().stream()
                    .noneMatch(held -> held.getId().equals(professor.getId()))) {
                lecture.getProfessors().add(professor);
                lectureRepository.save(lecture);
            }
        }
        professor.getLectures().clear();
        professor.getLectures().addAll(after);

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("lectures", AuditWriter.value(CatalogueEdits.labels(beforeIds), CatalogueEdits.labels(afterIds)));
        return changes;
    }

    private static String professorLabel(Professor professor) {
        return professor.getFirstName() + " " + professor.getLastName();
    }
}

package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.dto.request.LectureUpdateRequest;
import com.pse.moderation.dto.request.ProfessorUpdateRequest;
import com.pse.moderation.service.LectureModerationService;
import com.pse.moderation.service.ProfessorModerationService;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.model.LectureType;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.SemesterSeason;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Undoes lecture and professor field edits, including their mutual assignment. */
public final class CatalogueRevertHandler {

    private CatalogueRevertHandler() {
    }

    /**
 * Provides LectureHandler.
 */
    @Component
    public static class LectureHandler implements AuditRevertHandler {

        private final LectureRepository lectureRepository;
        private final LectureModerationService catalogService;

        /**
         * Creates LectureHandler.
         *
         * @param lectureRepository the lectureRepository
         * @param catalogService the catalogService
         */
        public LectureHandler(
                LectureRepository lectureRepository,
                LectureModerationService catalogService
        ) {
            this.lectureRepository = lectureRepository;
            this.catalogService = catalogService;
        }

        @Override
        public AuditTargetType targetType() {
            return AuditTargetType.LECTURE;
        }

        // Transactional because the professor assignment is a lazy collection and
        // open-in-view is disabled.
        @Override
        @Transactional(readOnly = true)
        public Map<UUID, Map<String, Object>> currentValues(Collection<UUID> targetIds) {
            Map<UUID, Map<String, Object>> values = new HashMap<>();
            for (Lecture lecture : lectureRepository.findAllById(targetIds)) {
                Map<String, Object> current = new LinkedHashMap<>();
                current.put("name", lecture.getName());
                current.put("code", lecture.getCode());
                current.put("semesterYear", lecture.getSemesterYear());
                current.put("semesterSeason", lecture.getSemesterSeason().name());
                current.put("active", lecture.isActive());
                current.put("lectureType", lecture.getLectureType().name());
                current.put("professors", lecture.getProfessors().stream()
                        .map(professor -> professor.getId().toString())
                        .toList());
                values.put(lecture.getId(), current);
            }
            return values;
        }

        @Override
        public void applyInverse(
                AuthenticatedUser principal,
                UUID targetId,
                Map<String, Object> before
        ) {
            catalogService.updateLecture(principal, targetId, new LectureUpdateRequest(
                    RevertValues.asString(before, "name"),
                    RevertValues.asString(before, "code"),
                    RevertValues.asInteger(before, "semesterYear"),
                    RevertValues.asEnum(before, "semesterSeason", SemesterSeason.class),
                    RevertValues.asBoolean(before, "active"),
                    RevertValues.asEnum(before, "lectureType", LectureType.class),
                    RevertValues.asIds(before, "professors")
            ));
        }
    }

    /**
     * Provides ProfessorHandler.
     */
    @Component
    public static class ProfessorHandler implements AuditRevertHandler {

        private final ProfessorRepository professorRepository;
        private final ProfessorModerationService catalogService;

        /**
         * Creates ProfessorHandler.
         *
         * @param professorRepository the professorRepository
         * @param catalogService the catalogService
         */
        public ProfessorHandler(
                ProfessorRepository professorRepository,
                ProfessorModerationService catalogService
        ) {
            this.professorRepository = professorRepository;
            this.catalogService = catalogService;
        }

        @Override
        public AuditTargetType targetType() {
            return AuditTargetType.PROFESSOR;
        }

        @Override
        @Transactional(readOnly = true)
        public Map<UUID, Map<String, Object>> currentValues(Collection<UUID> targetIds) {
            Map<UUID, Map<String, Object>> values = new HashMap<>();
            for (Professor professor : professorRepository.findAllById(targetIds)) {
                Map<String, Object> current = new LinkedHashMap<>();
                current.put("firstName", professor.getFirstName());
                current.put("lastName", professor.getLastName());
                current.put("active", professor.isActive());
                current.put("lectures", professor.getLectures().stream()
                        .map(lecture -> lecture.getId().toString())
                        .toList());
                values.put(professor.getId(), current);
            }
            return values;
        }

        @Override
        public void applyInverse(
                AuthenticatedUser principal,
                UUID targetId,
                Map<String, Object> before
        ) {
            catalogService.updateProfessor(principal, targetId, new ProfessorUpdateRequest(
                    RevertValues.asString(before, "firstName"),
                    RevertValues.asString(before, "lastName"),
                    RevertValues.asBoolean(before, "active"),
                    RevertValues.asIds(before, "lectures")
            ));
        }
    }
}

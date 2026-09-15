package com.pse.moderation.controller;

import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.dto.request.AddLectureRequest;
import com.pse.lecture.dto.response.LecturesResponse;
import com.pse.lecture.service.LectureService;
import com.pse.moderation.dto.request.LectureUpdateRequest;
import com.pse.moderation.dto.request.ProfessorUpdateRequest;
import com.pse.moderation.service.LectureModerationService;
import com.pse.moderation.service.ProfessorModerationService;
import com.pse.professor.dto.request.ProfessorAddRequest;
import com.pse.professor.dto.response.ProfessorsResponse;
import com.pse.professor.service.ProfessorService;
import com.pse.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The catalogue's administrative half. Every route is mapped twice: under {@code /admin},
 * where the whole prefix is admin-only, and under the legacy {@code /data} path the panel
 * still calls, where admin-only is a per-method rule in the security chain. The legacy
 * paths go away once the panel has moved.
 *
 * <p>There is no class-level base path on purpose. A class-level {@code /data} would put
 * {@code POST /admin/data/lectures} and the app's {@code POST /data/lectures} on the same
 * handler set and the context would fail to start on an ambiguous mapping, so each method
 * carries both of its absolute paths instead.
 *
 * <p>{@code /lectures/all} and {@code /professor/all} exist because the public reads filter
 * to {@code active = true}: a deactivated lecture or professor would otherwise be
 * unreachable from the panel to correct or reactivate.
 */
@RestController
public class ModerationCatalogController {

    private final LectureModerationService lectures;
    private final ProfessorModerationService professors;
    private final LectureService lectureService;
    private final ProfessorService professorService;

    /**
     * Creates ModerationCatalogController.
     *
     * @param lectures the lectures
     * @param professors the professors
     * @param lectureService the lectureService
     * @param professorService the professorService
     */
    public ModerationCatalogController(
            LectureModerationService lectures,
            ProfessorModerationService professors,
            LectureService lectureService,
            ProfessorService professorService
    ) {
        this.lectures = lectures;
        this.professors = professors;
        this.lectureService = lectureService;
        this.professorService = professorService;
    }

    /**
     * Returns getAllLectures.
     *
     * @return the result
     */
    @GetMapping({"/admin/data/lectures/all", "/data/lectures/all"})
    public LecturesResponse getAllLectures() {
        return lectureService.getAllLectures();
    }

    /**
     * Returns getAllProfessors.
     *
     * @return the result
     */
    @GetMapping({"/admin/data/professor/all", "/data/professor/all"})
    public ProfessorsResponse getAllProfessors() {
        return professorService.getAllProfessors();
    }

    /**
     * Returns updateLecture.
     *
     * @param principal the principal
     * @param id the id
     * @param request the request
     * @return the result
     */
    @PatchMapping({"/admin/data/lectures/{id}", "/data/lectures/{id}"})
    public BasicResponse updateLecture(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody LectureUpdateRequest request
    ) {
        return lectures.updateLecture(principal, id, request);
    }

    /**
     * Returns deleteLecture.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @DeleteMapping({"/admin/data/lectures/{id}", "/data/lectures/{id}"})
    public BasicResponse deleteLecture(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return lectures.deleteLecture(principal, id);
    }

    /**
     * Returns updateProfessor.
     *
     * @param principal the principal
     * @param id the id
     * @param request the request
     * @return the result
     */
    @PatchMapping({"/admin/data/professor/{id}", "/data/professor/{id}"})
    public BasicResponse updateProfessor(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody ProfessorUpdateRequest request
    ) {
        return professors.updateProfessor(principal, id, request);
    }

    /**
     * Returns deleteProfessor.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @DeleteMapping({"/admin/data/professor/{id}", "/data/professor/{id}"})
    public BasicResponse deleteProfessor(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return professors.deleteProfessor(principal, id);
    }

    /**
     * The admin API's version of {@code POST /data/lectures}. Same service call; the
     * difference is that a non-administrator is turned down by the security chain with a
     * 403 instead of reaching the handler.
     *
     * @param principal the principal
     * @param request the request
     * @return the result
     */
    @PostMapping("/admin/data/lectures")
    public BasicResponse addLecture(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddLectureRequest request
    ) {
        return lectureService.addLecture(principal, request);
    }

    /**
     * The admin API's version of {@code POST /data/professor}, which answers a refused
     * caller with {@code 200 {"success": false}}. Here the chain answers 403 first.
     *
     * @param principal the principal
     * @param request the request
     * @return the result
     */
    @PostMapping("/admin/data/professor")
    public BasicResponse addProfessor(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody ProfessorAddRequest request) {
        return professorService.addProfessor(principal, request);
    }
}

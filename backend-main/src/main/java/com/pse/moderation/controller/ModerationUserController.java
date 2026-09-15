package com.pse.moderation.controller;

import com.pse.shared.dto.BasicResponse;
import com.pse.moderation.dto.request.UserUpdateRequest;
import com.pse.moderation.dto.request.WarningRequest;
import com.pse.moderation.dto.response.UserListResponse;
import com.pse.moderation.dto.response.UserResponse;
import com.pse.moderation.dto.response.WarningHistoryResponse;
import com.pse.moderation.service.user.StudentLifecycleService;
import com.pse.moderation.service.user.StudentProfileService;
import com.pse.moderation.service.user.UserDirectoryService;
import com.pse.moderation.service.user.WarningService;
import com.pse.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Provides ModerationUserController.
 */
@RestController
@RequestMapping({"/admin/users", "/users"})
public class ModerationUserController {

    private final UserDirectoryService directory;
    private final StudentProfileService profiles;
    private final StudentLifecycleService lifecycle;
    private final WarningService warnings;

    /**
     * Creates ModerationUserController.
     *
     * @param directory the directory
     * @param profiles the profiles
     * @param lifecycle the lifecycle
     * @param warnings the warnings
     */
    public ModerationUserController(
            UserDirectoryService directory,
            StudentProfileService profiles,
            StudentLifecycleService lifecycle,
            WarningService warnings
    ) {
        this.directory = directory;
        this.profiles = profiles;
        this.lifecycle = lifecycle;
        this.warnings = warnings;
    }

    /**
     * Every parameter is declared as a String and parsed in the service, the same way
     * {@code AuditLogController} does it, so a bad value answers with a specific message
     * instead of Spring's generic type-mismatch failure.
     *
     * @param limit the limit
     * @param cursor the cursor
     * @param q the q
     * @param status the status
     * @param role the role
     * @param sort the sort
     * @return the result
     */
    @GetMapping
    public UserListResponse getStudents(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String sort
    ) {
        return directory.getStudents(limit, cursor, q, status, role, sort);
    }

    /**
     * Returns getStudent.
     *
     * @param id the id
     * @return the result
     */
    @GetMapping("/{id}")
    public UserResponse getStudent(@PathVariable UUID id) {
        return directory.getStudent(id);
    }

    /**
     * Returns updateStudent.
     *
     * @param principal the principal
     * @param id the id
     * @param request the request
     * @return the result
     */
    @PatchMapping("/{id}")
    public BasicResponse updateStudent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody UserUpdateRequest request
    ) {
        return profiles.updateStudent(principal, id, request);
    }

    /**
     * Returns deleteStudent.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @DeleteMapping("/{id}")
    public BasicResponse deleteStudent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return lifecycle.deleteStudent(principal, id);
    }

    /**
     * Returns blockStudent.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @PatchMapping("/{id}/block")
    public BasicResponse blockStudent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return lifecycle.blockStudent(principal, id);
    }

    /**
     * Returns unblockStudent.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @PatchMapping("/{id}/unblock")
    public BasicResponse unblockStudent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return lifecycle.unblockStudent(principal, id);
    }

    /**
     * Returns warnStudent.
     *
     * @param principal the principal
     * @param id the id
     * @param request the request
     * @return the result
     */
    @PostMapping("/{id}/warnings")
    public BasicResponse warnStudent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody WarningRequest request
    ) {
        return warnings.warnStudent(principal, id, request);
    }

    /**
     * Returns getWarningsForStudent.
     *
     * @param id the id
     * @return the result
     */
    @GetMapping("/{id}/warnings")
    public WarningHistoryResponse getWarningsForStudent(@PathVariable UUID id) {
        return warnings.getWarningsForStudent(id);
    }

    /**
     * Returns updateWarning.
     *
     * @param principal the principal
     * @param id the id
     * @param warningId the warningId
     * @param request the request
     * @return the result
     */
    @PatchMapping("/{id}/warnings/{warningId}")
    public BasicResponse updateWarning(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @PathVariable UUID warningId,
            @RequestBody WarningRequest request
    ) {
        return warnings.updateWarning(principal, id, warningId, request);
    }

    /**
     * Returns deleteWarning.
     *
     * @param principal the principal
     * @param id the id
     * @param warningId the warningId
     * @return the result
     */
    @DeleteMapping("/{id}/warnings/{warningId}")
    public BasicResponse deleteWarning(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @PathVariable UUID warningId
    ) {
        return warnings.deleteWarning(principal, id, warningId);
    }
}

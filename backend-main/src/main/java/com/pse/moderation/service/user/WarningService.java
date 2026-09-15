package com.pse.moderation.service.user;

import com.pse.audit.model.AuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.moderation.dto.request.WarningRequest;
import com.pse.moderation.dto.response.WarningHistoryResponse;
import com.pse.moderation.dto.response.WarningResponse;
import com.pse.moderation.mapper.UserResponseMapper;
import com.pse.moderation.model.Warning;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.NotificationType;
import com.pse.shared.error.ApiException;
import com.pse.social.model.Notification;
import com.pse.social.repository.NotificationRepository;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Warnings: issuing one, listing an account's, correcting the wording, withdrawing it.
 *
 * <p>Its own repository, its own responses and its own notification. The only thing it shares
 * with the rest of user moderation is the guard on who may be warned.
 */
@Service
public class WarningService {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final WarningRepository warningRepository;
    private final NotificationRepository notificationRepository;
    private final AuditWriter auditWriter;
    private final ModeratedStudents students;

    /**
     * Creates WarningService.
     *
     * @param studentRepository the studentRepository
     * @param adminRepository the adminRepository
     * @param warningRepository the warningRepository
     * @param notificationRepository the notificationRepository
     * @param auditWriter the auditWriter
     * @param students the students
     */
    public WarningService(
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            WarningRepository warningRepository,
            NotificationRepository notificationRepository,
            AuditWriter auditWriter,
            ModeratedStudents students
    ) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.warningRepository = warningRepository;
        this.notificationRepository = notificationRepository;
        this.auditWriter = auditWriter;
        this.students = students;
    }

    /**
     * Returns warnStudent.
     *
     * @param principal the principal
     * @param studentId the studentId
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse warnStudent(
            AuthenticatedUser principal,
            UUID studentId,
            WarningRequest request
    ) {
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty() || message.length() > 2_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Warning message is required");
        }

        Student target = students.findMutable(studentId);
        boolean targetIsAdmin = adminRepository.existsByStudent(target);
        students.protectAdministrator(principal, target);
        long before = warningRepository.countByStudent(target);

        Warning warning = new Warning();
        warning.setStudent(target);
        warning.setAdmin(principal.admin());
        warning.setMessage(message);
        warningRepository.save(warning);

        // A warning the student is never told about is not a warning. This is the
        // only student-visible effect: it does not restrict anything, and the
        // account status is deliberately left untouched.
        Notification notification = new Notification();
        notification.setRecipient(target);
        notification.setType(NotificationType.WARNING);
        notification.setMessage(message);
        notificationRepository.save(notification);

        auditWriter.write(
                principal.admin(),
                AuditAction.USER_WARNING_CREATED,
                AuditTargetType.USER,
                target.getId(),
                UserResponseMapper.label(target),
                AuditWriter.change("warnings", before, before + 1),
                warningMetadata(principal, targetIsAdmin, message)
        );
        return new BasicResponse("Student warned successfully", true);
    }

    /**
     * Returns getWarningsForStudent.
     *
     * @param studentId the studentId
     * @return the result
     */
    @Transactional(readOnly = true)
    public WarningHistoryResponse getWarningsForStudent(UUID studentId) {
        // A deleted account keeps its warning history and answers with it: the history records
        // why the account was moderated, and that is what the panel opens the profile to find
        // out at the moment the account itself can no longer say. 404 here means "no such
        // account" and nothing else.
        //
        // Opened together with UserDirectoryService.getStudent -- the profile modal reads both
        // in one pass and treats a 404 from either as "account gone", so these two move as a
        // pair or not at all. Reading only; issuing, editing and deleting a warning still go
        // through ModeratedStudents.findMutable, which refuses a deleted target.
        Student target = studentRepository
                .findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        List<WarningResponse> warnings = warningRepository
                .findByStudentOrderByCreatedAtDesc(target)
                .stream()
                .map(UserResponseMapper::toResponse)
                .toList();
        return new WarningHistoryResponse(
                "Successfully found warnings for student",
                true,
                warnings
        );
    }

    /**
     * Returns updateWarning.
     *
     * @param principal the principal
     * @param studentId the studentId
     * @param warningId the warningId
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse updateWarning(
            AuthenticatedUser principal,
            UUID studentId,
            UUID warningId,
            WarningRequest request
    ) {
        String message = request == null || request.message() == null
                ? ""
                : request.message().trim();
        if (message.isEmpty() || message.length() > 2_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Warning message is required");
        }

        Warning warning = findWarning(studentId, warningId);
        if (message.equals(warning.getMessage())) {
            return new BasicResponse("Updated warning successfully", true);
        }

        String before = warning.getMessage();
        warning.setMessage(message);
        warningRepository.save(warning);
        auditWriter.write(
                principal.admin(),
                AuditAction.USER_WARNING_UPDATED,
                AuditTargetType.WARNING,
                warning.getId(),
                "Warning for " + UserResponseMapper.label(warning.getStudent()),
                AuditWriter.nullableChange("message", before, message),
                Map.of()
        );
        return new BasicResponse("Updated warning successfully", true);
    }

    /**
     * Returns deleteWarning.
     *
     * @param principal the principal
     * @param studentId the studentId
     * @param warningId the warningId
     * @return the result
     */
    @Transactional
    public BasicResponse deleteWarning(
            AuthenticatedUser principal,
            UUID studentId,
            UUID warningId
    ) {
        Warning warning = findWarning(studentId, warningId);
        Student target = warning.getStudent();
        long before = warningRepository.countByStudent(target);
        String message = warning.getMessage();

        warningRepository.delete(warning);
        auditWriter.write(
                principal.admin(),
                AuditAction.USER_WARNING_DELETED,
                AuditTargetType.WARNING,
                warningId,
                "Warning for " + UserResponseMapper.label(target),
                AuditWriter.nullableChange("warnings", before, before - 1),
                Map.of("reason", message)
        );
        return new BasicResponse("Deleted warning successfully", true);
    }

    private Warning findWarning(UUID studentId, UUID warningId) {
        Warning warning = warningRepository.findById(warningId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Warning not found"));
        // The warning is addressed as a sub-resource of the student, so a mismatched
        // pair is a wrong URL rather than a warning that happens to belong elsewhere.
        if (!warning.getStudent().getId().equals(studentId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Warning not found");
        }
        return warning;
    }

    /** The warning reason, plus the elevation marker when one applies. */
    private Map<String, Object> warningMetadata(
            AuthenticatedUser principal,
            boolean targetIsAdmin,
            String message
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reason", message);
        metadata.putAll(students.elevationMetadata(principal, targetIsAdmin));
        return metadata;
    }
}

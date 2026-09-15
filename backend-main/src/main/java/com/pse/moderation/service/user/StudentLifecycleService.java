package com.pse.moderation.service.user;

import com.pse.audit.model.AuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.moderation.mapper.UserResponseMapper;
import com.pse.moderation.repository.AdminRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Removing an account or suspending it: delete, block, unblock.
 *
 * <p>Three variations on one template -- find under a lock, refuse if protected, return early
 * if the account is already in the asked-for state, change it, end its sessions, record it --
 * which is why they are together and apart from the field editing next door.
 */
@Service
public class StudentLifecycleService {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final AuditWriter auditWriter;
    private final Clock clock;
    private final ModeratedStudents students;

    /**
     * Creates StudentLifecycleService.
     *
     * @param studentRepository the studentRepository
     * @param adminRepository the adminRepository
     * @param auditWriter the auditWriter
     * @param clock the clock
     * @param students the students
     */
    public StudentLifecycleService(
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            AuditWriter auditWriter,
            Clock clock,
            ModeratedStudents students
    ) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.students = students;
    }

    /**
     * Returns deleteStudent.
     *
     * @param principal the principal
     * @param studentId the studentId
     * @return the result
     */
    @Transactional
    public BasicResponse deleteStudent(AuthenticatedUser principal, UUID studentId) {
        Student target = students.findMutable(studentId);
        boolean targetIsAdmin = adminRepository.existsByStudent(target);
        students.protectAdministrator(principal, target);

        // The audit label is built before the scrub so the record keeps the real
        // identity of who was deleted. audit_logs has no foreign key to students
        // and snapshots actor/target, so it survives this untouched.
        String label = UserResponseMapper.label(target);
        UserStatus before = target.getStatus();

        // Anonymize rather than delete the row: every comment, answer, vote,
        // rating, warning and report keeps a valid foreign key, so nothing is
        // left pointing at a dangling id and no discussion thread loses its
        // context. Both scrubbed values embed the UUID because username and
        // kit_email are unique columns.
        String shortId = target.getId().toString().substring(0, 8);
        target.setStatus(UserStatus.DELETED);
        target.setUsername("Deleted user " + shortId);
        target.setKitEmail("deleted-" + target.getId() + "@invalid.local");
        target.setBiography("");
        target.setBlockedReason(null);
        target.setDeletedAt(LocalDateTime.now(clock));
        studentRepository.save(target);
        students.revokeTokens(target);

        Map<String, Object> changes = AuditWriter.lifecycle(true, false);
        changes.put("status", Map.of("before", before.name(), "after", UserStatus.DELETED.name()));
        changes.put("anonymized", Map.of("before", false, "after", true));

        auditWriter.write(
                principal.admin(),
                AuditAction.USER_DELETED,
                AuditTargetType.USER,
                target.getId(),
                label,
                changes,
                students.elevationMetadata(principal, targetIsAdmin)
        );
        return new BasicResponse("Deleted user successfully", true);
    }

    /**
     * Returns blockStudent.
     *
     * @param principal the principal
     * @param studentId the studentId
     * @return the result
     */
    @Transactional
    public BasicResponse blockStudent(AuthenticatedUser principal, UUID studentId) {
        Student target = students.findMutable(studentId);
        boolean targetIsAdmin = adminRepository.existsByStudent(target);
        students.protectAdministrator(principal, target);
        if (target.getStatus() == UserStatus.BLOCKED) {
            return new BasicResponse("Student blocked successfully", true);
        }

        UserStatus before = target.getStatus();
        target.setStatus(UserStatus.BLOCKED);
        target.setBlockedAt(LocalDateTime.now(clock));
        studentRepository.save(target);
        students.revokeTokens(target);
        auditWriter.write(
                principal.admin(),
                AuditAction.USER_BLOCKED,
                AuditTargetType.USER,
                target.getId(),
                UserResponseMapper.label(target),
                AuditWriter.change("status", before.name(), UserStatus.BLOCKED.name()),
                students.elevationMetadata(principal, targetIsAdmin)
        );
        return new BasicResponse("Student blocked successfully", true);
    }

    /**
     * Returns unblockStudent.
     *
     * @param principal the principal
     * @param studentId the studentId
     * @return the result
     */
    @Transactional
    public BasicResponse unblockStudent(AuthenticatedUser principal, UUID studentId) {
        Student target = students.findMutable(studentId);
        boolean targetIsAdmin = adminRepository.existsByStudent(target);
        students.protectAdministrator(principal, target);
        if (target.getStatus() == UserStatus.ACTIVE) {
            return new BasicResponse("Student unblocked successfully", true);
        }

        UserStatus before = target.getStatus();
        target.setStatus(UserStatus.ACTIVE);
        target.setBlockedAt(null);
        target.setBlockedReason(null);
        studentRepository.save(target);
        auditWriter.write(
                principal.admin(),
                AuditAction.USER_UNBLOCKED,
                AuditTargetType.USER,
                target.getId(),
                UserResponseMapper.label(target),
                AuditWriter.change("status", before.name(), UserStatus.ACTIVE.name()),
                students.elevationMetadata(principal, targetIsAdmin)
        );
        return new BasicResponse("Student unblocked successfully", true);
    }
}

package com.pse.moderation.service.user;

import com.pse.audit.model.AuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.auth.service.AdminSuperuserPolicy;
import com.pse.moderation.dto.request.UserUpdateRequest;
import com.pse.moderation.mapper.UserResponseMapper;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.shared.util.KitEmail;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * The one endpoint that edits an account's own fields: name, address, biography, standing,
 * status and role.
 *
 * <p>Its own class because it is a class's worth of work -- six independent optional fields,
 * each with its own validation and its own audit entry, plus the promotion and demotion that
 * moving the role between STUDENT and ADMIN implies.
 */
@Service
public class StudentProfileService {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final AuditWriter auditWriter;
    private final AdminSuperuserPolicy superuserPolicy;
    private final Clock clock;
    private final ModeratedStudents students;

    /**
     * Creates StudentProfileService.
     *
     * @param studentRepository the studentRepository
     * @param adminRepository the adminRepository
     * @param auditWriter the auditWriter
     * @param superuserPolicy the superuserPolicy
     * @param clock the clock
     * @param students the students
     */
    public StudentProfileService(
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            AuditWriter auditWriter,
            AdminSuperuserPolicy superuserPolicy,
            Clock clock,
            ModeratedStudents students
    ) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.auditWriter = auditWriter;
        this.superuserPolicy = superuserPolicy;
        this.clock = clock;
        this.students = students;
    }

    /**
     * Returns updateStudent.
     *
     * @param principal the principal
     * @param studentId the studentId
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse updateStudent(
            AuthenticatedUser principal,
            UUID studentId,
            UserUpdateRequest request
    ) {
        if (request == null
                || (request.username() == null
                && request.kitEmail() == null
                && request.biography() == null
                && request.status() == null
                && request.role() == null
                && request.credibilityScore() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No update supplied");
        }

        Student target = students.findMutable(studentId);
        boolean targetIsAdmin = adminRepository.existsByStudent(target);
        boolean targetIsSelf = target.getId().equals(principal.student().getId());
        boolean targetIsElevated = superuserPolicy.isSuperuser(target.getKitEmail());
        boolean callerIsElevated = students.isElevated(principal);
        String label = UserResponseMapper.label(target);
        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.username() != null) {
            String username = request.username().trim();
            if (username.isEmpty() || username.length() > 100) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid username");
            }
            if (!username.equals(target.getUsername())) {
                if (studentRepository.existsByUsername(username)) {
                    throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
                }
                changes.put("username", AuditWriter.value(target.getUsername(), username));
                target.setUsername(username);
            }
        }

        boolean addressChanged = false;
        if (request.kitEmail() != null) {
            String email = KitEmail.requireKitAddress(request.kitEmail());
            if (!email.equals(target.getKitEmail())) {
                // Elevation is keyed on the address, so moving one is how an elevated
                // account would be taken over: free the address with one edit, claim it
                // with the next. Not editable through this endpoint by anyone.
                if (targetIsElevated) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "The address of an elevated administrator cannot be changed"
                    );
                }
                if (targetIsAdmin && !callerIsElevated) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "Administrator accounts cannot be modified"
                    );
                }
                if (studentRepository.findByKitEmail(email).isPresent()) {
                    throw new ApiException(HttpStatus.CONFLICT, "Email is already taken");
                }
                changes.put("kitEmail", AuditWriter.value(target.getKitEmail(), email));
                target.setKitEmail(email);
                addressChanged = true;
            }
        }

        if (request.biography() != null) {
            String biography = request.biography().trim();
            if (biography.length() > 2_000) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Biography is too long");
            }
            String before = target.getBiography() == null ? "" : target.getBiography();
            if (!biography.equals(before)) {
                changes.put("biography", AuditWriter.value(before, biography));
                target.setBiography(biography);
            }
        }

        if (request.credibilityScore() != null
                && request.credibilityScore() != target.getCredibilityScore()) {
            changes.put("credibilityScore",
                    AuditWriter.value(target.getCredibilityScore(), request.credibilityScore()));
            target.setCredibilityScore(request.credibilityScore());
        }

        boolean sessionsRevoked = addressChanged;
        if (request.status() != null && request.status() != target.getStatus()) {
            if (request.status() == UserStatus.DELETED) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Use DELETE /users/{id} to delete a user"
                );
            }
            if (targetIsSelf) {
                throw new ApiException(HttpStatus.CONFLICT, "You cannot change your own status");
            }
            if (targetIsAdmin && !callerIsElevated) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Administrator accounts cannot be modified"
                );
            }
            changes.put("status", AuditWriter.value(target.getStatus().name(), request.status().name()));
            if (request.status() == UserStatus.BLOCKED) {
                target.setBlockedAt(LocalDateTime.now(clock));
                sessionsRevoked = true;
            } else {
                target.setBlockedAt(null);
                target.setBlockedReason(null);
            }
            target.setStatus(request.status());
        }

        UserRole roleBefore = targetIsAdmin ? UserRole.ADMIN : UserRole.STUDENT;
        if (request.role() != null && request.role() != roleBefore) {
            if (targetIsSelf) {
                throw new ApiException(HttpStatus.CONFLICT, "You cannot change your own role");
            }
            // Demoting an administrator has always been open to any administrator, and
            // stays that way. The elevated operator is the exception: demoting it out of
            // admins is a lockout the API has no way back from, which is the same reason
            // the self-guard above exists.
            if (targetIsElevated && !callerIsElevated) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Administrator accounts cannot be modified"
                );
            }
            changes.put("role", AuditWriter.value(roleBefore.name(), request.role().name()));
            if (request.role() == UserRole.ADMIN) {
                Admin admin = new Admin();
                admin.setStudent(target);
                adminRepository.save(admin);
                // Promoting revokes the sessions for the same reason demoting does, plus
                // one of its own: admin membership is resolved per request, so a live
                // student token would silently become an admin session that no ADMIN_LOGIN
                // ever recorded, and its eventual logout would write an ADMIN_LOGOUT with
                // no matching login. A fresh sign-in produces a real pair.
                sessionsRevoked = true;
            } else {
                Admin admin = adminRepository.findByStudent(target).orElseThrow(
                        () -> new ApiException(HttpStatus.CONFLICT, "User is not an administrator")
                );
                // Warnings and reviewed reports point at the admin row, so the database
                // refuses to delete one that has been used. Checked up front: letting the
                // constraint fire answers with an unexplained "State conflict" instead.
                if (adminRepository.hasModerationHistory(admin)) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "Administrator has moderation history and cannot be demoted"
                    );
                }
                // Demoting revokes the sessions too: an admin token outlives a student
                // one, and the panel would keep working until it expired.
                adminRepository.delete(admin);
                sessionsRevoked = true;
            }
        }

        if (changes.isEmpty()) {
            return new BasicResponse("Updated user successfully", true);
        }

        studentRepository.save(target);
        if (sessionsRevoked) {
            students.revokeTokens(target);
        }

        Map<String, Object> metadata = students.elevationMetadata(principal, targetIsAdmin);
        if (sessionsRevoked) {
            metadata.put("sessionsRevoked", true);
        }
        auditWriter.write(
                principal.admin(),
                AuditAction.USER_UPDATED,
                AuditTargetType.USER,
                target.getId(),
                label,
                changes,
                metadata
        );
        return new BasicResponse("Updated user successfully", true);
    }
}

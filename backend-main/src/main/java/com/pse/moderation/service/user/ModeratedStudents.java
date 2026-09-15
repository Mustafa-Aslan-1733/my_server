package com.pse.moderation.service.user;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import com.pse.auth.service.AdminSuperuserPolicy;
import com.pse.moderation.repository.AdminRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The preamble every mutating user endpoint shares: find the account under a lock, refuse if
 * it is one this caller may not touch, and afterwards end its sessions or mark the entry as
 * an elevated action.
 *
 * <p>Shared and named because the four lifecycle endpoints and the profile edit performed the
 * same steps in the same order. A guard that is copied is a guard that eventually gets copied
 * incompletely, and these are the only thing standing between an operator and another
 * operator's account.
 */
@Component
public class ModeratedStudents {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final TokenRepository tokenRepository;
    private final AdminSuperuserPolicy superuserPolicy;

    /**
     * Creates ModeratedStudents.
     *
     * @param studentRepository the studentRepository
     * @param adminRepository the adminRepository
     * @param tokenRepository the tokenRepository
     * @param superuserPolicy the superuserPolicy
     */
    public ModeratedStudents(
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            TokenRepository tokenRepository,
            AdminSuperuserPolicy superuserPolicy
    ) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.tokenRepository = tokenRepository;
        this.superuserPolicy = superuserPolicy;
    }

    /**
     * Returns findMutable.
     *
     * @param id the id
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    public Student findMutable(UUID id) {
        Student target = studentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getStatus() == UserStatus.DELETED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        return target;
    }

    /**
     * The guard behind delete, block, unblock and warn. Two rules that used to be one,
     * and had to be separated: an administrator account is off limits unless the caller
     * is an elevated operator, but nobody moderates their own account at all. Self used
     * to be refused only incidentally, because the caller is necessarily an administrator
     * and so the first rule caught it — lifting that rule for an elevated caller would
     * have taken the only self-protection these four endpoints have with it.
     *
     * <p>Both refusals answer 409, the status these endpoints have always refused with.
     *
     * @param principal the principal
     * @param target the target
     *
     * @throws ApiException if the operation fails
     */
    public void protectAdministrator(AuthenticatedUser principal, Student target) {
        if (target.getId().equals(principal.student().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot moderate your own account");
        }
        if (adminRepository.existsByStudent(target) && !isElevated(principal)) {
            throw new ApiException(HttpStatus.CONFLICT, "Administrator accounts cannot be modified");
        }
    }

    /**
     * Marks an entry as an action only an elevated operator could have taken. Recorded in
     * the metadata on purpose: the audit actor keeps reporting the ADMIN role, because
     * elevation is not one and the panel renders that role.
     *
     * @param principal the principal
     * @param targetIsAdmin the targetIsAdmin
     * @return the result
     */
    public Map<String, Object> elevationMetadata(AuthenticatedUser principal, boolean targetIsAdmin) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (targetIsAdmin && isElevated(principal)) {
            metadata.put("elevated", true);
        }
        return metadata;
    }

    /**
     * An elevated operator: an administrator whose address is configured as a superuser.
     * Elevation is not a role — it lifts the refusals that protect administrator accounts
     * and nothing else, and every response still reports these accounts as ADMIN.
     *
     * @param principal the principal
     * @return the result
     */
    public boolean isElevated(AuthenticatedUser principal) {
        return principal.isAdmin()
                && superuserPolicy.isSuperuser(principal.student().getKitEmail());
    }

    /**
     * Executes revokeTokens.
     *
     * @param target the target
     */
    public void revokeTokens(Student target) {
        List<Token> tokens = tokenRepository.findByStudent(target);
        tokens.forEach(token -> token.setRevoked(true));
        tokenRepository.saveAll(tokens);
    }
}

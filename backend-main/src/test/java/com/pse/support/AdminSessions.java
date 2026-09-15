package com.pse.support;

import com.pse.auth.model.SessionType;
import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.shared.enums.UserStatus;
import com.pse.security.TokenHasher;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mints an administrator session straight into the tables, for tests whose subject is not
 * the login flow.
 *
 * <p>Five integration test classes each carry their own private {@code createSession},
 * because each also wants its own fixtures around it. This exists so that classes which want
 * <em>only</em> a usable admin token do not become the sixth and seventh copy of the same
 * thirty lines -- {@code docs/test-findings.md} records under P-4 what duplicated test setup
 * costs: two places state the same contract and nothing says which to edit when it changes.
 *
 * <p>Writes rows directly rather than driving {@code /admin/auth/login}, deliberately. A
 * test about something else should not fail because the login flow changed, and the login
 * flow has {@code AdminApiPathSplitTests} of its own.
 */
public final class AdminSessions {

    private AdminSessions() {
    }

    /**
     * Empties the three tables and creates one active administrator with a live session.
     *
     * @return the raw bearer token; the hash is what the row stores
     */
    public static String createAdmin(
            String email,
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            TokenRepository tokenRepository
    ) {
        tokenRepository.deleteAll();
        adminRepository.deleteAll();
        studentRepository.deleteAll();

        Student student = new Student();
        student.setKitEmail(email);
        student.setUsername(email.substring(0, email.indexOf('@')));
        student.setStatus(UserStatus.ACTIVE);
        student.setEmailVerifiedAt(LocalDateTime.now());
        student = studentRepository.saveAndFlush(student);

        Admin admin = new Admin();
        admin.setStudent(student);
        adminRepository.saveAndFlush(admin);

        String rawToken = UUID.randomUUID().toString();
        Token token = new Token();
        token.setEmail(email);
        token.setStudent(student);
        token.setHash(TokenHasher.hash(rawToken));
        token.setSessionType(SessionType.ADMIN);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        tokenRepository.saveAndFlush(token);

        return rawToken;
    }
}

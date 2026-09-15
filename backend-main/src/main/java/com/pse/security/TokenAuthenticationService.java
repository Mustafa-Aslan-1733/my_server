package com.pse.security;

import com.pse.auth.model.Token;
import com.pse.auth.model.SessionType;
import com.pse.auth.repository.TokenRepository;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Provides TokenAuthenticationService.
 */
@Service
public class TokenAuthenticationService {

    /**
     * How stale a student's "last seen" is allowed to get before it is written again.
     * Every authenticated request passes through here, so refreshing it unconditionally
     * would add a row update to every single API call. The panel renders the value as a
     * timestamp, where a few minutes of lag is not visible.
     */
    private static final Duration LAST_SEEN_REFRESH_INTERVAL = Duration.ofMinutes(5);

    private final TokenRepository tokenRepository;
    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;
    private final Clock clock;

    /**
     * Creates TokenAuthenticationService.
     *
     * @param tokenRepository the tokenRepository
     * @param adminRepository the adminRepository
     * @param studentRepository the studentRepository
     * @param clock the clock
     */
    public TokenAuthenticationService(
            TokenRepository tokenRepository,
            AdminRepository adminRepository,
            StudentRepository studentRepository,
            Clock clock
    ) {
        this.tokenRepository = tokenRepository;
        this.adminRepository = adminRepository;
        this.studentRepository = studentRepository;
        this.clock = clock;
    }

    /**
     * Returns authenticate.
     *
     * @param rawToken the rawToken
     * @return the result
     */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String hash = TokenHasher.hash(rawToken);
        Token token = tokenRepository
                .findByHashAndRevokedFalseAndExpiresAtAfter(hash, now)
                .orElseGet(() -> upgradeLegacyToken(rawToken, hash, now).orElse(null));

        if (token == null || token.getStudent() == null) {
            return Optional.empty();
        }

        // Enforce the account status on every request rather than relying on the
        // blocking admin having revoked the sessions. Any path that changes the
        // status without revoking (self-service delete, a manual database update)
        // would otherwise leave a student session alive for the full token TTL.
        UserStatus status = token.getStudent().getStatus();
        if (status == UserStatus.BLOCKED || status == UserStatus.DELETED) {
            return Optional.empty();
        }

        touchLastSeen(token.getStudent(), now);

        SessionType sessionType = token.getSessionType();
        Admin admin = sessionType == SessionType.APP
                ? null
                : adminRepository.findByStudent(token.getStudent()).orElse(null);
        if (sessionType == SessionType.ADMIN && admin == null) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(token.getStudent(), token, admin));
    }

    /**
     * Counts this request towards the student's "last seen" so the panel's column shows
     * actual API activity rather than the last login, which for a long-lived token can be
     * weeks stale. Called after the status gate above, so a request that is about to be
     * refused does not register as activity.
     */
    private void touchLastSeen(Student student, LocalDateTime now) {
        LocalDateTime lastSeen = student.getLastSeenAt();
        if (lastSeen != null && lastSeen.isAfter(now.minus(LAST_SEEN_REFRESH_INTERVAL))) {
            return;
        }
        student.setLastSeenAt(now);
        studentRepository.save(student);
    }

    private Optional<Token> upgradeLegacyToken(String rawToken, String hash, LocalDateTime now) {
        Optional<Token> legacy = tokenRepository
                .findByLegacyValueAndRevokedFalseAndExpiresAtAfter(rawToken, now);
        legacy.ifPresent(token -> {
            token.setHash(hash);
            token.setLegacyValue(null);
            tokenRepository.save(token);
        });
        return legacy;
    }
}

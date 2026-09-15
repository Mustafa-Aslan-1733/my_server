package com.pse.security;

import com.pse.auth.model.SessionType;
import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The gate every authenticated request passes through. Until this batch nothing tested it
 * directly — it appears in {@code AuthServiceTests} only as a mock — and every one of its
 * uncovered branches was a **refusal**: no test said that a token should be turned down.
 *
 * <p>That is the wrong way round for this class in particular. A missed refusal here is not a
 * wrong status code, it is a session that keeps working after the account behind it was
 * blocked or deleted, for as long as the token lives — and a student token lives for a year.
 *
 * <p>The clock is fixed, because the "last seen" write is a time comparison and the interesting
 * cases sit exactly on its boundary.
 */
@ExtendWith(MockitoExtension.class)
class TokenAuthenticationServiceTests {

    private static final String RAW_TOKEN = "raw-token-value";
    private static final String HASH = TokenHasher.hash(RAW_TOKEN);
    private static final Instant FIXED = Instant.parse("2026-09-07T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED, ZoneOffset.UTC);

    @Mock private TokenRepository tokenRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private StudentRepository studentRepository;

    private final Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

    private TokenAuthenticationService service() {
        return new TokenAuthenticationService(
                tokenRepository, adminRepository, studentRepository, clock);
    }

    private static Student student(UserStatus status) {
        Student student = new Student();
        student.setStatus(status);
        return student;
    }

    private static Token token(Student student, SessionType sessionType) {
        Token token = new Token();
        token.setStudent(student);
        token.setSessionType(sessionType);
        return token;
    }

    private void tokenIsFoundByHash(Token token) {
        when(tokenRepository.findByHashAndRevokedFalseAndExpiresAtAfter(HASH, NOW))
                .thenReturn(Optional.of(token));
    }

    // ---------------------------------------------------------------- the input gate

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void authenticate_noToken_isRefusedWithoutReachingTheDatabase(String rawToken) {
        assertThat(service().authenticate(rawToken)).isEmpty();

        verifyNoInteractions(tokenRepository, adminRepository, studentRepository);
    }

    @Test
    void authenticate_tokenLongerThanTheLimit_isRefusedWithoutReachingTheDatabase() {
        // Hashing is cheap but not free, and the value is attacker-supplied: the length check
        // is what stops an unbounded body from being hashed on every request.
        assertThat(service().authenticate("t".repeat(513))).isEmpty();

        verifyNoInteractions(tokenRepository, adminRepository, studentRepository);
    }

    @Test
    void authenticate_tokenOfExactlyTheLimit_isLookedUpRatherThanRefused() {
        String atTheLimit = "t".repeat(512);
        when(tokenRepository.findByHashAndRevokedFalseAndExpiresAtAfter(
                TokenHasher.hash(atTheLimit), NOW))
                .thenReturn(Optional.empty());
        when(tokenRepository.findByLegacyValueAndRevokedFalseAndExpiresAtAfter(atTheLimit, NOW))
                .thenReturn(Optional.empty());

        assertThat(service().authenticate(atTheLimit)).isEmpty();

        verify(tokenRepository).findByHashAndRevokedFalseAndExpiresAtAfter(
                TokenHasher.hash(atTheLimit), NOW);
    }

    @Test
    void authenticate_looksTheTokenUpByItsHashAndNeverByItsPlaintext() {
        // The row holds a SHA-256 hash. Querying with the raw value would mean the plaintext
        // is what identifies a session, which is the property the hash column exists to avoid.
        tokenIsFoundByHash(token(student(UserStatus.ACTIVE), SessionType.APP));

        service().authenticate(RAW_TOKEN);

        verify(tokenRepository).findByHashAndRevokedFalseAndExpiresAtAfter(HASH, NOW);
        assertThat(HASH).isNotEqualTo(RAW_TOKEN);
    }

    @Test
    void authenticate_expiredOrRevokedToken_isRefused() {
        // Both conditions live in the query name, so an empty result is how they arrive here.
        when(tokenRepository.findByHashAndRevokedFalseAndExpiresAtAfter(HASH, NOW))
                .thenReturn(Optional.empty());
        when(tokenRepository.findByLegacyValueAndRevokedFalseAndExpiresAtAfter(RAW_TOKEN, NOW))
                .thenReturn(Optional.empty());

        assertThat(service().authenticate(RAW_TOKEN)).isEmpty();

        verify(studentRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- the legacy upgrade

    @Test
    void authenticate_legacyPlaintextToken_isUpgradedToAHashAndThePlaintextIsCleared() {
        Token legacy = token(student(UserStatus.ACTIVE), SessionType.APP);
        legacy.setLegacyValue(RAW_TOKEN);
        when(tokenRepository.findByHashAndRevokedFalseAndExpiresAtAfter(HASH, NOW))
                .thenReturn(Optional.empty());
        when(tokenRepository.findByLegacyValueAndRevokedFalseAndExpiresAtAfter(RAW_TOKEN, NOW))
                .thenReturn(Optional.of(legacy));

        assertThat(service().authenticate(RAW_TOKEN)).isPresent();

        assertThat(legacy.getHash()).isEqualTo(HASH);
        assertThat(legacy.getLegacyValue()).isNull();
        verify(tokenRepository).save(legacy);
    }

    @Test
    void authenticate_tokenFoundByHash_neverGoesLookingForALegacyRow() {
        tokenIsFoundByHash(token(student(UserStatus.ACTIVE), SessionType.APP));

        service().authenticate(RAW_TOKEN);

        verify(tokenRepository, never())
                .findByLegacyValueAndRevokedFalseAndExpiresAtAfter(any(), any());
    }

    // ---------------------------------------------------------------- the account gate

    @Test
    void authenticate_tokenWithNoStudent_isRefused() {
        tokenIsFoundByHash(token(null, SessionType.APP));

        assertThat(service().authenticate(RAW_TOKEN)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"BLOCKED", "DELETED"})
    void authenticate_accountNoLongerAllowedIn_isRefusedOnEveryRequest(UserStatus status) {
        // Deliberately not left to session revocation: any path that changes the status
        // without revoking -- a self-service delete, a manual database update -- would
        // otherwise leave the session alive for the full token lifetime.
        tokenIsFoundByHash(token(student(status), SessionType.APP));

        assertThat(service().authenticate(RAW_TOKEN)).isEmpty();

        verifyNoInteractions(adminRepository);
    }

    @Test
    void authenticate_refusedRequest_doesNotCountAsActivity() {
        Student blocked = student(UserStatus.BLOCKED);
        tokenIsFoundByHash(token(blocked, SessionType.APP));

        service().authenticate(RAW_TOKEN);

        assertThat(blocked.getLastSeenAt()).isNull();
        verify(studentRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "INACTIVE"})
    void authenticate_accountStillAllowedIn_isAdmitted(UserStatus status) {
        tokenIsFoundByHash(token(student(status), SessionType.APP));

        assertThat(service().authenticate(RAW_TOKEN)).isPresent();
    }

    // ---------------------------------------------------------------- last seen

    @Test
    void authenticate_studentNeverSeenBefore_recordsTheActivity() {
        Student student = student(UserStatus.ACTIVE);
        tokenIsFoundByHash(token(student, SessionType.APP));

        service().authenticate(RAW_TOKEN);

        assertThat(student.getLastSeenAt()).isEqualTo(NOW);
        verify(studentRepository).save(student);
    }

    @Test
    void authenticate_studentSeenWithinTheRefreshInterval_isNotWrittenAgain() {
        // Every authenticated request passes through here; refreshing unconditionally would
        // add a row update to every single API call.
        Student student = student(UserStatus.ACTIVE);
        LocalDateTime recent = NOW.minusMinutes(4);
        student.setLastSeenAt(recent);
        tokenIsFoundByHash(token(student, SessionType.APP));

        service().authenticate(RAW_TOKEN);

        assertThat(student.getLastSeenAt()).isEqualTo(recent);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void authenticate_studentSeenExactlyAtTheRefreshInterval_isWrittenAgain() {
        Student student = student(UserStatus.ACTIVE);
        student.setLastSeenAt(NOW.minusMinutes(5));
        tokenIsFoundByHash(token(student, SessionType.APP));

        service().authenticate(RAW_TOKEN);

        assertThat(student.getLastSeenAt()).isEqualTo(NOW);
        verify(studentRepository).save(student);
    }

    // ---------------------------------------------------------------- the session tier

    @Test
    void authenticate_appSession_neverAsksWhetherTheStudentIsAnAdministrator() {
        // An APP session must not carry administrator authority even when the account behind
        // it has an admins row: the tier is a property of the session, not of the person.
        tokenIsFoundByHash(token(student(UserStatus.ACTIVE), SessionType.APP));

        Optional<AuthenticatedUser> authenticated = service().authenticate(RAW_TOKEN);

        assertThat(authenticated).get().satisfies(user -> {
            assertThat(user.admin()).isNull();
            assertThat(user.isAdmin()).isFalse();
        });
        verifyNoInteractions(adminRepository);
    }

    @Test
    void authenticate_adminSessionWithoutAnAdminRow_isRefused() {
        Student student = student(UserStatus.ACTIVE);
        tokenIsFoundByHash(token(student, SessionType.ADMIN));
        when(adminRepository.findByStudent(student)).thenReturn(Optional.empty());

        assertThat(service().authenticate(RAW_TOKEN)).isEmpty();
    }

    @Test
    void authenticate_adminSessionWithAnAdminRow_carriesTheAdministrator() {
        Student student = student(UserStatus.ACTIVE);
        Admin admin = new Admin();
        Token token = token(student, SessionType.ADMIN);
        tokenIsFoundByHash(token);
        when(adminRepository.findByStudent(student)).thenReturn(Optional.of(admin));

        Optional<AuthenticatedUser> authenticated = service().authenticate(RAW_TOKEN);

        assertThat(authenticated).contains(new AuthenticatedUser(student, token, admin));
        assertThat(authenticated).get().extracting(AuthenticatedUser::isAdmin).isEqualTo(true);
    }

    @Test
    void authenticate_tokenWithNoSessionType_stillPicksUpTheAdministratorRow() {
        // Characterization. Tokens minted before the session tier existed carry no type, and
        // they are treated as neither APP nor ADMIN: the admin row is looked up, and a missing
        // one is not a refusal. AdminApiPathSplitTests exists because of exactly this shape.
        Student student = student(UserStatus.ACTIVE);
        Admin admin = new Admin();
        tokenIsFoundByHash(token(student, null));
        when(adminRepository.findByStudent(student)).thenReturn(Optional.of(admin));

        assertThat(service().authenticate(RAW_TOKEN))
                .get()
                .extracting(AuthenticatedUser::admin)
                .isEqualTo(admin);
    }

    @Test
    void authenticate_tokenWithNoSessionTypeAndNoAdminRow_isStillAdmitted() {
        Student student = student(UserStatus.ACTIVE);
        tokenIsFoundByHash(token(student, null));
        when(adminRepository.findByStudent(student)).thenReturn(Optional.empty());

        assertThat(service().authenticate(RAW_TOKEN))
                .get()
                .extracting(AuthenticatedUser::isAdmin)
                .isEqualTo(false);
    }

    @Test
    void authenticate_theRefreshWindowIsTakenFromTheInjectedClock() {
        Student student = student(UserStatus.ACTIVE);
        tokenIsFoundByHash(token(student, SessionType.APP));

        service().authenticate(RAW_TOKEN);

        verify(tokenRepository).findByHashAndRevokedFalseAndExpiresAtAfter(eq(HASH), eq(NOW));
        assertThat(student.getLastSeenAt()).isEqualTo(NOW);
    }
}

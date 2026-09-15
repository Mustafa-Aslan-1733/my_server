package com.pse.auth.service;

import com.pse.config.properties.AuthProperties;
import com.pse.auth.model.OneTimePassword;
import com.pse.auth.repository.OneTimePasswordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The login code is the whole of authentication -- there is no password behind it. What
 * matters here is that a code is stored hashed, that it expires when the configured TTL says
 * it does, and that consuming one is single-use.
 *
 * <p>Expiry filtering itself is done by the repository query, so at this level the testable
 * half is the expiry that gets written and the instant handed to the query. The query is
 * covered by the integration layer.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceTests {

    private static final String EMAIL = "unzhz@student.kit.edu";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 12, 0, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    /** Argon2 is deliberately expensive, so the one real hash is computed once for the class. */
    private static final String KNOWN_CODE = "a1b2c3";
    private static final String KNOWN_HASH = OtpHasher.hash(KNOWN_CODE);

    @Mock
    private OneTimePasswordRepository repository;

    private OtpService serviceWith(Duration ttl) {
        return new OtpService(repository, CLOCK,
                new AuthProperties(Duration.ofDays(1), Duration.ofDays(365), ttl));
    }

    private static OneTimePassword legacyCode(String plaintext) {
        OneTimePassword otp = new OneTimePassword();
        otp.setEmail(EMAIL);
        otp.setLegacyValue(plaintext);
        return otp;
    }

    // ---------- issue ----------

    /** A second outstanding code would mean two valid codes for one address at once. */
    @Test
    void issue_anyEmail_invalidatesOutstandingCodesBeforeStoringTheNewOne() {
        // Given
        OtpService service = serviceWith(Duration.ofMinutes(5));

        // When
        service.issue(EMAIL);

        // Then
        InOrder order = inOrder(repository);
        order.verify(repository).consumeOutstandingForEmail(EMAIL);
        order.verify(repository).save(any(OneTimePassword.class));
    }

    @Test
    void issue_defaultTtl_storesAnExpiryOfNowPlusThatTtl() {
        // Given
        OtpService service = serviceWith(Duration.ofMinutes(5));

        // When
        service.issue(EMAIL);

        // Then
        ArgumentCaptor<OneTimePassword> saved = ArgumentCaptor.forClass(OneTimePassword.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void issue_customTtl_storesAnExpiryOfNowPlusThatTtl() {
        // Given
        OtpService service = serviceWith(Duration.ofSeconds(90));

        // When
        service.issue(EMAIL);

        // Then
        ArgumentCaptor<OneTimePassword> saved = ArgumentCaptor.forClass(OneTimePassword.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(90));
    }

    /** A code readable from the database is a code an operator can log in with. */
    @Test
    void issue_anyEmail_returnsThePlaintextButStoresOnlyAHash() {
        // Given
        OtpService service = serviceWith(Duration.ofMinutes(5));

        // When
        String code = service.issue(EMAIL);

        // Then
        ArgumentCaptor<OneTimePassword> saved = ArgumentCaptor.forClass(OneTimePassword.class);
        verify(repository).save(saved.capture());
        assertThat(code).hasSize(6);
        assertThat(saved.getValue().getHash()).isNotNull().isNotEqualTo(code);
        assertThat(saved.getValue().getLegacyValue()).isNull();
        assertThat(OtpHasher.matches(code, saved.getValue().getHash())).isTrue();
    }

    // ---------- consume ----------

    @Test
    void consume_matchingCode_marksItUsedAndReturnsTrue() {
        // Given
        OneTimePassword candidate = new OneTimePassword();
        candidate.setEmail(EMAIL);
        candidate.setHash(KNOWN_HASH);
        when(repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(List.of(candidate));

        // When
        boolean consumed = serviceWith(Duration.ofMinutes(5)).consume(EMAIL, KNOWN_CODE);

        // Then
        assertThat(consumed).isTrue();
        assertThat(candidate.isUsed()).isTrue();
        verify(repository).save(candidate);
    }

    /** The TTL boundary is enforced by the query, so the instant it is given is the contract. */
    @Test
    void consume_anyEmail_queriesUsingTheInjectedClock() {
        // Given
        when(repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(List.of());

        // When
        serviceWith(Duration.ofMinutes(5)).consume(EMAIL, "123456");

        // Then
        ArgumentCaptor<LocalDateTime> bound = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository)
                .findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), bound.capture());
        assertThat(bound.getValue()).isEqualTo(NOW);
    }

    @Test
    void consume_wrongCode_returnsFalseAndSavesNothing() {
        // Given
        when(repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(List.of(legacyCode("111111")));

        // When
        boolean consumed = serviceWith(Duration.ofMinutes(5)).consume(EMAIL, "999999");

        // Then
        assertThat(consumed).isFalse();
        verify(repository, never()).save(any());
    }

    /**
     * An expired or already-used code is filtered out by the query, so it reaches consume as
     * an empty candidate list -- which is the same answer as a wrong code.
     */
    @Test
    void consume_noOutstandingCandidates_returnsFalse() {
        // Given
        when(repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(List.of());

        // When
        boolean consumed = serviceWith(Duration.ofMinutes(5)).consume(EMAIL, "123456");

        // Then
        assertThat(consumed).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void consume_legacyPlaintextCodeMatches_returnsTrue() {
        // Given -- rows written before hashing existed still have to be accepted
        OneTimePassword candidate = legacyCode("123456");
        when(repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(List.of(candidate));

        // When
        boolean consumed = serviceWith(Duration.ofMinutes(5)).consume(EMAIL, "123456");

        // Then
        assertThat(consumed).isTrue();
        assertThat(candidate.isUsed()).isTrue();
    }

    @Test
    void consume_candidateWithNeitherHashNorLegacyValue_returnsFalse() {
        // Given
        OneTimePassword candidate = new OneTimePassword();
        candidate.setEmail(EMAIL);
        when(repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(List.of(candidate));

        // When
        boolean consumed = serviceWith(Duration.ofMinutes(5)).consume(EMAIL, "123456");

        // Then
        assertThat(consumed).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void consume_severalCandidates_marksOnlyTheMatchingOne() {
        // Given
        OneTimePassword stale = legacyCode("111111");
        OneTimePassword matching = legacyCode("222222");
        when(repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(List.of(stale, matching));

        // When
        boolean consumed = serviceWith(Duration.ofMinutes(5)).consume(EMAIL, "222222");

        // Then
        assertThat(consumed).isTrue();
        assertThat(matching.isUsed()).isTrue();
        assertThat(stale.isUsed()).isFalse();
        verify(repository).save(matching);
        verify(repository, never()).save(stale);
    }

    @Test
    void consume_nullSubmittedCode_throwsNullPointerException() {
        // Given
        when(repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(EMAIL), any()))
                .thenReturn(List.of(legacyCode("123456")));
        OtpService service = serviceWith(Duration.ofMinutes(5));

        // When / Then -- the legacy comparison dereferences it
        assertThatThrownBy(() -> service.consume(EMAIL, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- invalidate ----------

    @Test
    void invalidate_anyEmail_consumesEveryOutstandingCode() {
        // When
        serviceWith(Duration.ofMinutes(5)).invalidate(EMAIL);

        // Then
        verify(repository).consumeOutstandingForEmail(EMAIL);
        verify(repository, never()).save(any());
    }
}

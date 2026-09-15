package com.pse.auth.service;

import com.pse.config.properties.RateLimitProperties;
import com.pse.auth.model.AuthRateLimitBucket;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.shared.error.RateLimitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bucket is what stands between the login endpoint and an unlimited code-request loop,
 * so the interesting cases are all at its edges: the request that fills it, the one after,
 * and the one that arrives once the window has rolled over.
 *
 * <p>The PlatformTransactionManager is mocked rather than the TransactionTemplate, because
 * the service builds the template itself -- with a mocked manager the real template runs the
 * callback, which is what these assertions depend on.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTests {

    private static final String OPERATION = RateLimitService.REQUEST_CODE;
    private static final String SUBJECT = "subject-hash";
    private static final String KEY = "test-rate-limit-hmac-key-with-more-than-32-characters";
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 12, 0, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private AuthRateLimitBucketRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private RateLimitService service() {
        return serviceWith(WINDOW, KEY);
    }

    private RateLimitService serviceWith(Duration window, String hmacKey) {
        return new RateLimitService(repository, CLOCK, transactionManager,
                new RateLimitProperties(window, hmacKey, 3, 20, 10, 30));
    }

    private static AuthRateLimitBucket bucket(int attempts, LocalDateTime windowStartedAt) {
        AuthRateLimitBucket bucket = new AuthRateLimitBucket();
        bucket.setOperation(OPERATION);
        bucket.setSubjectHash(SUBJECT);
        bucket.setAttempts(attempts);
        bucket.setWindowStartedAt(windowStartedAt);
        return bucket;
    }

    private AuthRateLimitBucket savedBucket() {
        ArgumentCaptor<AuthRateLimitBucket> saved =
                ArgumentCaptor.forClass(AuthRateLimitBucket.class);
        verify(repository).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    // ---------- subject hashing ----------

    @Test
    void emailSubject_theSameAddress_producesTheSameHash() {
        // Given
        RateLimitService service = service();

        // When / Then -- the bucket key has to be stable across requests
        assertThat(service.emailSubject("a@kit.edu")).isEqualTo(service.emailSubject("a@kit.edu"));
    }

    @Test
    void emailSubject_differentAddresses_produceDifferentHashes() {
        // Given
        RateLimitService service = service();

        // When / Then
        assertThat(service.emailSubject("a@kit.edu"))
                .isNotEqualTo(service.emailSubject("b@kit.edu"));
    }

    /** Namespacing keeps an address and an IP that happen to read alike in separate buckets. */
    @Test
    void ipSubject_sameLiteralAsAnEmail_producesADifferentHash() {
        // Given
        RateLimitService service = service();

        // When / Then
        assertThat(service.ipSubject("a@kit.edu"))
                .isNotEqualTo(service.emailSubject("a@kit.edu"));
    }

    @Test
    void emailSubject_anyAddress_producesLowercaseHexThatFitsTheColumn() {
        // Given
        RateLimitService service = service();

        // When / Then -- subject_hash is varchar(64)
        assertThat(service.emailSubject("a@kit.edu")).matches("[0-9a-f]{64}");
    }

    @Test
    void ipSubject_null_hashesAsTheLiteralUnknown() {
        // Given
        RateLimitService service = service();

        // When / Then -- a missing remote address still has to land in some bucket
        assertThat(service.ipSubject(null)).isEqualTo(service.ipSubject("unknown"));
    }

    @Test
    void emailSubject_emptyHmacKey_throwsIllegalStateException() {
        // Given -- an empty key is rejected by the Mac, not silently accepted
        RateLimitService service = serviceWith(WINDOW, "");

        // When / Then
        assertThatThrownBy(() -> service.emailSubject("a@kit.edu"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not create rate-limit subject hash");
    }

    // ---------- validateConfiguration ----------

    @Test
    void validateConfiguration_keyShorterThanThirtyTwoCharacters_throwsIllegalStateException() {
        // Given
        RateLimitService service = serviceWith(WINDOW, "a".repeat(31));

        // When / Then
        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AUTH_RATE_LIMIT_HMAC_KEY must contain at least 32 characters");
    }

    @Test
    void validateConfiguration_keyOfExactlyThirtyTwoCharacters_isAccepted() {
        // Given
        RateLimitService service = serviceWith(WINDOW, "a".repeat(32));

        // When / Then
        assertThatCode(service::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void validateConfiguration_nullKey_throwsNullPointerException() {
        // Given
        RateLimitService service = serviceWith(WINDOW, null);

        // When / Then
        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- consume ----------

    @Test
    void consume_noExistingBucket_createsOneWithASingleAttempt() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.empty());

        // When
        service().consume(OPERATION, SUBJECT, 3);

        // Then
        AuthRateLimitBucket saved = savedBucket();
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getWindowStartedAt()).isEqualTo(NOW);
        assertThat(saved.getOperation()).isEqualTo(OPERATION);
        assertThat(saved.getSubjectHash()).isEqualTo(SUBJECT);
    }

    @Test
    void consume_bucketBelowTheLimit_incrementsIt() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(1, NOW)));

        // When
        service().consume(OPERATION, SUBJECT, 3);

        // Then
        assertThat(savedBucket().getAttempts()).isEqualTo(2);
    }

    /** The request that fills the bucket still succeeds; only the next one is refused. */
    @Test
    void consume_bucketOneBelowTheLimit_fillsItAndStillSucceeds() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(2, NOW)));

        // When
        service().consume(OPERATION, SUBJECT, 3);

        // Then
        assertThat(savedBucket().getAttempts()).isEqualTo(3);
    }

    @Test
    void consume_bucketAlreadyAtTheLimit_throwsRateLimitExceptionAndSavesNothing() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(3, NOW)));
        RateLimitService service = service();

        // When / Then
        assertThatThrownBy(() -> service.consume(OPERATION, SUBJECT, 3))
                .isInstanceOf(RateLimitException.class)
                .hasMessage("Too many requests")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(RateLimitException.class))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getRetryAfterSeconds()).isEqualTo(WINDOW.toSeconds());
                });
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void consume_bucketFromAnExpiredWindow_startsAFreshWindow() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(3, NOW.minusMinutes(16))));

        // When
        service().consume(OPERATION, SUBJECT, 3);

        // Then -- a full bucket from a past window must not refuse the request
        AuthRateLimitBucket saved = savedBucket();
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getWindowStartedAt()).isEqualTo(NOW);
    }

    /** The window check is strict, so a bucket whose window ends exactly now is already stale. */
    @Test
    void consume_bucketWhoseWindowEndsExactlyNow_startsAFreshWindow() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(3, NOW.minus(WINDOW))));

        // When
        service().consume(OPERATION, SUBJECT, 3);

        // Then
        assertThat(savedBucket().getAttempts()).isEqualTo(1);
    }

    @Test
    void consume_conflictOnTheFirstTwoAttempts_succeedsOnTheThird() {
        // Given -- two requests racing on the unique (operation, subject_hash) constraint
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenThrow(new DataIntegrityViolationException("conflict"))
                .thenThrow(new DataIntegrityViolationException("conflict"))
                .thenReturn(Optional.of(bucket(0, NOW)));

        // When
        service().consume(OPERATION, SUBJECT, 3);

        // Then
        verify(repository, times(3)).findByOperationAndSubjectHash(OPERATION, SUBJECT);
        assertThat(savedBucket().getAttempts()).isEqualTo(1);
    }

    @Test
    void consume_conflictOnEveryAttempt_rethrowsAfterThreeTries() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenThrow(new DataIntegrityViolationException("conflict"));
        RateLimitService service = service();

        // When / Then
        assertThatThrownBy(() -> service.consume(OPERATION, SUBJECT, 3))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(repository, times(3)).findByOperationAndSubjectHash(OPERATION, SUBJECT);
    }

    /** A refusal is an answer, not a conflict -- retrying it would triple the work. */
    @Test
    void consume_bucketAtTheLimit_isNotRetried() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(3, NOW)));
        RateLimitService service = service();

        // When / Then
        assertThatThrownBy(() -> service.consume(OPERATION, SUBJECT, 3))
                .isInstanceOf(RateLimitException.class);
        verify(repository, times(1)).findByOperationAndSubjectHash(OPERATION, SUBJECT);
    }

    @Test
    void consume_differentSubjects_areTrackedInSeparateBuckets() {
        // Given
        when(repository.findByOperationAndSubjectHash(any(), any())).thenReturn(Optional.empty());
        RateLimitService service = service();

        // When
        service.consume(OPERATION, "subject-a", 3);
        service.consume(RateLimitService.LOGIN_FAILURE, "subject-b", 3);

        // Then -- one bucket is never consulted on behalf of another
        verify(repository).findByOperationAndSubjectHash(OPERATION, "subject-a");
        verify(repository).findByOperationAndSubjectHash(RateLimitService.LOGIN_FAILURE, "subject-b");
    }

    // ---------- ensureAllowed ----------

    @Test
    void ensureAllowed_noBucket_doesNotThrow() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.empty());
        RateLimitService service = service();

        // When / Then
        assertThatCode(() -> service.ensureAllowed(OPERATION, SUBJECT, 3))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureAllowed_bucketBelowTheLimit_doesNotThrow() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(2, NOW)));
        RateLimitService service = service();

        // When / Then
        assertThatCode(() -> service.ensureAllowed(OPERATION, SUBJECT, 3))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureAllowed_bucketAtTheLimit_throwsRateLimitException() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(3, NOW)));
        RateLimitService service = service();

        // When / Then
        assertThatThrownBy(() -> service.ensureAllowed(OPERATION, SUBJECT, 3))
                .isInstanceOf(RateLimitException.class)
                .hasMessage("Too many requests");
    }

    @Test
    void ensureAllowed_fullBucketFromAnExpiredWindow_doesNotThrow() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(99, NOW.minusMinutes(16))));
        RateLimitService service = service();

        // When / Then
        assertThatCode(() -> service.ensureAllowed(OPERATION, SUBJECT, 3))
                .doesNotThrowAnyException();
    }

    /** It is a check, not a consumption -- the failure counter is incremented elsewhere. */
    @Test
    void ensureAllowed_anyBucket_neverWritesToTheBucket() {
        // Given
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(1, NOW)));

        // When
        service().ensureAllowed(OPERATION, SUBJECT, 3);

        // Then
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void ensureAllowed_windowAlmostElapsed_reportsAtLeastOneSecond() {
        // Given -- half a second left, which rounds down to zero seconds
        when(repository.findByOperationAndSubjectHash(OPERATION, SUBJECT))
                .thenReturn(Optional.of(bucket(3, NOW.minus(WINDOW).plusNanos(500_000_000L))));
        RateLimitService service = service();

        // When / Then -- Retry-After: 0 would invite an immediate retry
        assertThatThrownBy(() -> service.ensureAllowed(OPERATION, SUBJECT, 3))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(RateLimitException.class))
                .satisfies(exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(1));
    }

    // ---------- reset ----------

    @Test
    void reset_anyOperationAndSubject_deletesThatBucketOnly() {
        // When
        service().reset(RateLimitService.LOGIN_FAILURE, SUBJECT);

        // Then
        verify(repository).deleteByOperationAndSubjectHash(RateLimitService.LOGIN_FAILURE, SUBJECT);
        verify(repository, never()).saveAndFlush(any());
    }
}

package com.pse.auth.service;

import com.pse.config.properties.RateLimitProperties;
import com.pse.auth.model.AuthRateLimitBucket;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.shared.error.RateLimitException;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Provides RateLimitService.
 */
@Service
public class RateLimitService {

    /**
     * The REQUEST_CODE.
     */
    public static final String REQUEST_CODE = "REQUEST_CODE";
    /**
     * The LOGIN_FAILURE.
     */
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";

    private final AuthRateLimitBucketRepository repository;
    private final Clock clock;
    private final Duration window;
    private final String hmacKey;
    private final TransactionTemplate requiresNew;

    /**
     * Creates RateLimitService.
     *
     * @param repository the repository
     * @param clock the clock
     * @param transactionManager the transactionManager
     * @param properties the properties
     */
    public RateLimitService(
            AuthRateLimitBucketRepository repository,
            Clock clock,
            PlatformTransactionManager transactionManager,
            RateLimitProperties properties
    ) {
        this.repository = repository;
        this.clock = clock;
        this.window = properties.window();
        this.hmacKey = properties.hmacKey();
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PostConstruct
    void validateConfiguration() {
        if (hmacKey.length() < 32) {
            throw new IllegalStateException("AUTH_RATE_LIMIT_HMAC_KEY must contain at least 32 characters");
        }
    }

    /**
     * Returns emailSubject.
     *
     * @param normalizedEmail the normalizedEmail
     * @return the result
     */
    public String emailSubject(String normalizedEmail) {
        return subjectHash("email:" + normalizedEmail);
    }

    /**
     * Returns ipSubject.
     *
     * @param remoteAddress the remoteAddress
     * @return the result
     */
    public String ipSubject(String remoteAddress) {
        return subjectHash("ip:" + (remoteAddress == null ? "unknown" : remoteAddress));
    }

    /**
     * Executes consume.
     *
     * @param operation the operation
     * @param subjectHash the subjectHash
     * @param limit the limit
     */
    public void consume(String operation, String subjectHash, int limit) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                requiresNew.executeWithoutResult(status -> increment(operation, subjectHash, limit));
                return;
            } catch (DataIntegrityViolationException exception) {
                if (attempt == 2) {
                    throw exception;
                }
            }
        }
    }

    /**
     * Executes ensureAllowed.
     *
     * @param operation the operation
     * @param subjectHash the subjectHash
     * @param limit the limit
     *
     * @throws RateLimitException if the operation fails
     */
    public void ensureAllowed(String operation, String subjectHash, int limit) {
        requiresNew.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            repository.findByOperationAndSubjectHash(operation, subjectHash)
                    .filter(bucket -> isCurrentWindow(bucket, now))
                    .filter(bucket -> bucket.getAttempts() >= limit)
                    .ifPresent(bucket -> {
                        throw new RateLimitException(retryAfter(bucket, now));
                    });
        });
    }

    /**
     * Executes reset.
     *
     * @param operation the operation
     * @param subjectHash the subjectHash
     */
    public void reset(String operation, String subjectHash) {
        requiresNew.executeWithoutResult(status ->
                repository.deleteByOperationAndSubjectHash(operation, subjectHash));
    }

    private void increment(String operation, String subjectHash, int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        AuthRateLimitBucket bucket = repository
                .findByOperationAndSubjectHash(operation, subjectHash)
                .orElseGet(() -> newBucket(operation, subjectHash, now));

        if (!isCurrentWindow(bucket, now)) {
            bucket.setWindowStartedAt(now);
            bucket.setAttempts(0);
        }
        if (bucket.getAttempts() >= limit) {
            throw new RateLimitException(retryAfter(bucket, now));
        }
        bucket.setAttempts(bucket.getAttempts() + 1);
        repository.saveAndFlush(bucket);
    }

    private AuthRateLimitBucket newBucket(String operation, String subjectHash, LocalDateTime now) {
        AuthRateLimitBucket bucket = new AuthRateLimitBucket();
        bucket.setOperation(operation);
        bucket.setSubjectHash(subjectHash);
        bucket.setWindowStartedAt(now);
        return bucket;
    }

    /**
     * Wall-clock arithmetic on a {@code LocalDateTime}, which is safe here for one reason and
     * one only: the {@code Clock} bean is {@code Clock.systemUTC()} ({@code TimeConfig}), and
     * UTC has no daylight-saving transitions.
     *
     * <p>Change that bean to {@code systemDefaultZone()} and this silently acquires a bug that
     * appears twice a year: at the autumn transition the clock goes back an hour, so this
     * comparison stays true for an extra hour and everyone rate-limited at that moment stays
     * locked out for an hour longer. {@code retryAfter} below has the same dependency and
     * would report an hour of nonsense.
     *
     * <p>Written down because static analysis flags both lines and the answer is not in either
     * of them -- it is in a different file, in a bean nobody editing a rate limiter would think
     * to look at.
     */
    private boolean isCurrentWindow(AuthRateLimitBucket bucket, LocalDateTime now) {
        return bucket.getWindowStartedAt().plus(window).isAfter(now);
    }

    private long retryAfter(AuthRateLimitBucket bucket, LocalDateTime now) {
        return Math.max(1, Duration.between(now, bucket.getWindowStartedAt().plus(window)).toSeconds());
    }

    private String subjectHash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create rate-limit subject hash", exception);
        }
    }
}

package com.pse.auth.service;

import com.pse.config.properties.AuthProperties;
import com.pse.auth.model.OneTimePassword;
import com.pse.auth.repository.OneTimePasswordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Provides OtpService.
 */
@Service
public class OtpService {

    private final OneTimePasswordRepository repository;
    private final Clock clock;
    private final Duration ttl;

    /**
     * Creates OtpService.
     *
     * @param repository the repository
     * @param clock the clock
     * @param properties the properties
     */
    public OtpService(
            OneTimePasswordRepository repository,
            Clock clock,
            AuthProperties properties
    ) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = properties.otpTtl();
    }

    /**
     * Returns issue.
     *
     * @param normalizedEmail the normalizedEmail
     * @return the result
     */
    @Transactional
    public String issue(String normalizedEmail) {
        repository.consumeOutstandingForEmail(normalizedEmail);

        String code = TokenGenerator.generateOneTimePassword();
        OneTimePassword otp = new OneTimePassword();
        otp.setEmail(normalizedEmail);
        otp.setHash(OtpHasher.hash(code));
        otp.setExpiresAt(LocalDateTime.now(clock).plus(ttl));
        repository.save(otp);
        return code;
    }

    /**
     * Executes invalidate.
     *
     * @param normalizedEmail the normalizedEmail
     */
    @Transactional
    public void invalidate(String normalizedEmail) {
        repository.consumeOutstandingForEmail(normalizedEmail);
    }

    /**
     * Checks consume.
     *
     * @param normalizedEmail the normalizedEmail
     * @param submittedCode the submittedCode
     * @return the result
     */
    @Transactional
    public boolean consume(String normalizedEmail, String submittedCode) {
        List<OneTimePassword> candidates =
                repository.findByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        normalizedEmail,
                        LocalDateTime.now(clock)
                );
        for (OneTimePassword candidate : candidates) {
            if (matches(submittedCode, candidate)) {
                candidate.setUsed(true);
                repository.save(candidate);
                return true;
            }
        }
        return false;
    }

    private boolean matches(String submittedCode, OneTimePassword candidate) {
        if (candidate.getHash() != null
                && OtpHasher.matches(submittedCode, candidate.getHash())) {
            return true;
        }
        if (candidate.getLegacyValue() == null) {
            return false;
        }
        return MessageDigest.isEqual(
                submittedCode.getBytes(StandardCharsets.UTF_8),
                candidate.getLegacyValue().getBytes(StandardCharsets.UTF_8)
        );
    }
}

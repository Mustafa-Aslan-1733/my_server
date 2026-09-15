package com.pse.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 over a session token, so the {@code tokens} table stores a digest and not the
 * bearer value itself.
 *
 * <p>Fast and unsalted on purpose, and that is exactly why it is not the same class as
 * {@link com.pse.auth.service.OtpHasher}: a token is 32 bytes of {@code SecureRandom} output,
 * so there is nothing to brute-force and nothing a rainbow table could precompute, and every
 * authenticated request pays this cost. An OTP is six characters a person could guess, and
 * hashing it this way would be a real weakness.
 *
 * <p>The two used to be {@code HashGenerator.hashToken} and
 * {@code HashGenerator.hashPassword}, side by side under one name. Reaching for the wrong one
 * compiles.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    /**
     * Returns hash.
     *
     * @param rawToken the rawToken
     * @return the result
     *
     * @throws IllegalStateException if the operation fails
     */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available.", exception);
        }
    }
}

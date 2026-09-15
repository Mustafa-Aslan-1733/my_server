package com.pse.auth.service;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Argon2 over a one-time password, so the {@code one_time_passwords} table stores a hash and
 * not the code that was mailed.
 *
 * <p>Deliberately slow and salted, unlike {@link com.pse.security.TokenHasher}: the code is
 * six characters from a 53-character alphabet, which is guessable, and the cost per attempt
 * is the defence. It is paid once per login rather than once per request.
 *
 * <p>Named for what it hashes. Its predecessor called this {@code hashPassword}, which read
 * as though the application had passwords; it does not, and never did -- a login is a mailed
 * code and nothing else.
 */
public final class OtpHasher {

    /**
     * Higher values mean more security and more compute time.
     *
     * <ul>
     *   <li>saltLength: 16 bytes
     *   <li>hashLength: 32 bytes
     *   <li>parallelism: 1 thread
     *   <li>memory: 32768 KiB = 32 MiB
     *   <li>iterations: 3
     * </ul>
     *
     * <p>The encoded string carries the algorithm, the version, the parameters and the random
     * salt alongside the hash, so no separate salt column is needed.
     */
    private static final Argon2PasswordEncoder ENCODER =
            new Argon2PasswordEncoder(16, 32, 1, 32768, 3);

    private OtpHasher() {
    }

    /**
     * Returns hash.
     *
     * @param code the code
     * @return the result
     */
    public static String hash(String code) {
        return ENCODER.encode(code);
    }

    /**
     * Checks matches.
     *
     * @param code the code
     * @param hash the hash
     * @return the result
     */
    public static boolean matches(String code, String hash) {
        return ENCODER.matches(code, hash);
    }
}

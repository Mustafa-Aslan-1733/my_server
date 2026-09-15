package com.pse.auth.service;

import java.security.SecureRandom;
import java.util.Base64;


/**
 * Generates a secure Token.
 */
public final class TokenGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();

    //Human friendly alphabet (some fonts make e.g. l and I look the same)
    private static final String CHARACTERS = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";

    private TokenGenerator() {
    }

    /**
     * 32 Byte long Token => ~ 43 Chars (Base64)
     *
     * @return the result
     */
    public static String generateToken() {

        byte[] bytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(bytes);
        

        //.withoutPadding() used, so that String is filled with "=" at end
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Create 6 Char long One Time Password.
     *
     * @return the result
     */
    public static String generateOneTimePassword() {

        StringBuilder otp = new StringBuilder(6);

        for (int i = 0; i < 6; i++) {
            otp.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }

        return otp.toString();
    }

   
}

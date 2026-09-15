package com.pse.shared.util;

import java.util.Locale;
import java.util.regex.Pattern;

import com.pse.shared.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The address a login is keyed on.
 *
 * <p>Two operations, kept apart because the callers genuinely want different ones:
 * {@link #normalize} is what makes {@code Firstname.Lastname@Student.KIT.edu} and
 * {@code firstname.lastname@student.kit.edu} the same account, and {@link #requireKitAddress}
 * additionally refuses anything that is not a KIT student address. {@code validate} only ever
 * wanted the first, and did it inline.
 */
public final class KitEmail {

    //Exception for for Thomas and Erik mail adresses
    private static final Pattern KIT_EMAIL =
            Pattern.compile(
                    "^(?:[A-Z0-9._%+-]+@student\\.kit\\.edu|thomas\\.weber@kit\\.edu|burger@kit\\.edu)$",
                    Pattern.CASE_INSENSITIVE
            );


    private KitEmail() {
    }

    /**
     * Trimmed and lowercased. Null in, null out.
     *
     * @param email the email
     * @return the result
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Normalized, or a 400 if it is not a KIT student address. A null address is treated as
     * the empty string and fails the pattern like any other non-address, which is what the
     * caller this was lifted from did.
     *
     * @param email the email
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    public static String requireKitAddress(String email) {
        String normalized = email == null ? "" : normalize(email);
        if (!KIT_EMAIL.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid KIT email");
        }
        return normalized;
    }
}

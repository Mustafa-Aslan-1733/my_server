package com.pse.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The accounts that are administrators.
 *
 * <p>Entries are comma separated and take the form {@code email} or
 * {@code email:Display Name}. The name is optional and is only used when the
 * account has to be created; it never affects whether an email is an admin.
 */
@Component
public class AdminBootstrapPolicy {

    private final Map<String, String> bootstrapAccounts;

    /**
     * Creates AdminBootstrapPolicy.
     *
     * @param configuredEmails the configuredEmails
     */
    public AdminBootstrapPolicy(@Value("${app.auth.admin-bootstrap-emails:}") String configuredEmails) {
        Map<String, String> accounts = new LinkedHashMap<>();
        Arrays.stream(configuredEmails.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(entry -> {
                    int separator = entry.indexOf(':');
                    String email = (separator < 0 ? entry : entry.substring(0, separator))
                            .trim()
                            .toLowerCase(Locale.ROOT);
                    String name = separator < 0 ? "" : entry.substring(separator + 1).trim();
                    if (!email.isBlank()) {
                        accounts.put(email, name);
                    }
                });
        this.bootstrapAccounts = Map.copyOf(accounts);
    }

    /**
     * Checks shouldBootstrap.
     *
     * @param normalizedEmail the normalizedEmail
     * @return the result
     */
    public boolean shouldBootstrap(String normalizedEmail) {
        return bootstrapAccounts.containsKey(normalizedEmail);
    }

    /**
     * Returns bootstrapEmails.
     *
     * @return the result
     */
    public Set<String> bootstrapEmails() {
        return bootstrapAccounts.keySet();
    }



    /**
     * The configured display name for an email, or empty when none was given.
     *
     * @param normalizedEmail email (normalized)
     * @return displayName
     */
    public String displayName(String normalizedEmail) {
        String name = bootstrapAccounts.get(normalizedEmail);
        return name == null ? "" : name;
    }
}

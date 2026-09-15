package com.pse.moderation.dto.response;

import java.util.UUID;

/**
 * The admin a warning is attributed to. {@code id} is the issuing admin's <em>student</em>
 * id — the same value {@code GET /admin/auth/me} returns as {@code user.id} — so a client can tell
 * whether it issued a warning itself without a second lookup.
 *
 * @param name the issuing admin's display name; a moderation record that cannot say who
 *             warned someone is most of the point of the record missing
 *
 * @param createdAt the moment the warning was issued, not when the admin account was created
 * @param id the id
 */
public record WarningIssuerResponse(
        UUID id,
        String name,
        String createdAt
) {
}

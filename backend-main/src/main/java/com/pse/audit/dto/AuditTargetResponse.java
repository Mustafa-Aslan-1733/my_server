package com.pse.audit.dto;

import com.pse.audit.model.AuditTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * What an audited event was done to.
 *
 * @param id absent for a target that is not a row: a refusal names the endpoint it refused
 *           through {@link AuditTargetType#ENDPOINT} and has no id to carry.
 *
 * @param type the type
 * @param label the label
 */
public record AuditTargetResponse(
        AuditTargetType type,
        @Schema(nullable = true) UUID id,
        // label is deliberately not annotated. Every writer seen so far supplies one, and a
        // refusal supplies "VERB /path"; declaring it nullable would claim an observation
        // nothing here makes. If a null one ever reaches the sweep, that is the moment.
        String label
) {
}

package com.pse.audit.dto;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditTargetType;

import java.util.List;

/**
 * Filter vocabulary for one log, served by both {@code /audit-logs/meta} and
 * {@code /activity-logs/meta}. {@code actions} holds only the actions of the log that
 * answered, so the two endpoints return different vocabularies.
 *
 * <p>{@code actors} is capped and therefore not exhaustive: every account that ever signed
 * in is an actor in the activity log. Beyond the cap the {@code q} search is what finds an
 * actor.
 *
 * @param message the message
 * @param success the success
 * @param actors the actors
 * @param actions the actions
 * @param targetTypes the targetTypes
 * @param actorTypes the actorTypes
 */
public record AuditMetaResponse(
        String message,
        boolean success,
        List<AuditActorMetaResponse> actors,
        List<AuditAction> actions,
        List<AuditTargetType> targetTypes,
        List<AuditActorType> actorTypes
) {
}

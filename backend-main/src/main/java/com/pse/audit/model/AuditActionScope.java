package com.pse.audit.model;

/**
 * Which of the two log pages an action belongs to.
 *
 * <p>The split is by action rather than by actor type on purpose: a refused admin login
 * is recorded with an {@code ADMIN} actor but belongs to the activity log, next to every
 * other refusal.
 */
public enum AuditActionScope {

    /** What admins did through the admin panel. */
    ADMINISTRATIVE,

    /** What users did in the student app, plus every refused request. */
    ACTIVITY
}

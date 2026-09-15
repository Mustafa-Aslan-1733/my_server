package com.pse.audit.model;

import java.util.Arrays;
import java.util.List;

/**
 * Lists AuditAction.
 */
public enum AuditAction {

    // Administrative actions. The actor is always an admin account.
    /**
     * The ADMIN_LOGIN.
     */
    ADMIN_LOGIN(AuditActionScope.ADMINISTRATIVE),
    /**
     * The ADMIN_LOGOUT.
     */
    ADMIN_LOGOUT(AuditActionScope.ADMINISTRATIVE),
    /**
     * The USER_UPDATED.
     */
    USER_UPDATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The USER_WARNING_CREATED.
     */
    USER_WARNING_CREATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The USER_WARNING_UPDATED.
     */
    USER_WARNING_UPDATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The USER_WARNING_DELETED.
     */
    USER_WARNING_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The USER_BLOCKED.
     */
    USER_BLOCKED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The USER_UNBLOCKED.
     */
    USER_UNBLOCKED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The USER_DELETED.
     */
    USER_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * An account deleted by its own owner through {@code PATCH /account/deleteAccount}.
     *
     * <p><b>Administrative although the actor is a student</b>, and deliberately so. The scope
     * decides which of the two logs an action appears in, and it is chosen by the log a reader
     * needs rather than by who acted -- the class note on {@link AuditActionScope} states that
     * rule, and a refused <em>admin</em> login sitting in the activity log is the same rule
     * pointing the other way. The reader here is an operator asking where an account went, and
     * that question is asked on the administrative log.
     *
     * <p><b>Separate from {@link #USER_DELETED} rather than reusing it.</b> The two are not the
     * same event: an admin deletion anonymises the row in the same transaction and this one does
     * not, so an account deleted here keeps its real username. Merging them would also break a
     * rule the admin panel has been given in writing -- that a {@code DELETED} row carrying a
     * real username has no matching {@code USER_DELETED} entry -- which is how an operator tells
     * the two kinds of deletion apart today.
     *
     * <p>Not revertible, and twice over: it is outside {@code AuditRevertService}'s revertible
     * set, and the entry carries no field diff for the machinery to invert.
     */
    USER_SELF_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * A deleted account brought back to {@code ACTIVE} by a request for a login code.
     *
     * <p>Administrative for the same reason as {@link #USER_SELF_DELETED}: it is the other half
     * of the answer to "where did this account go", and an operator reading one without the
     * other is reading half a lifecycle.
     *
     * <p><b>The entry records the event and does not authorise it.</b> The reactivation itself is
     * an open defect (F-48): {@code POST /auth/request-login} is public and the flip happens when
     * the code is requested rather than when it is entered, so the caller who triggers this need
     * not be the account's owner and need not identify themselves at all. The actor recorded here
     * is therefore the account, which is the closest true statement available -- there is no
     * identified actor to name.
     *
     * <p>Not revertible, for the reason {@code AuditRevertService} refuses lifecycle events:
     * reverting it would be a deletion, which is its own action with its own guards.
     * <b>Recording it as a {@code USER_UPDATED} field diff instead would have made it revertible</b>
     * through the generic machinery, which would have put a restore route into the product
     * through the back door -- refused by
     * <a href="../../../../../../docs/adr/0015-deletion-not-split-from-anonymisation.md">ADR-0015</a>.
     */
    USER_SELF_REACTIVATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The COMMENT_UPDATED.
     */
    COMMENT_UPDATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The COMMENT_DELETED.
     */
    COMMENT_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The ANSWER_UPDATED.
     */
    ANSWER_UPDATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The ANSWER_DELETED.
     */
    ANSWER_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The RATING_DELETED.
     */
    RATING_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The COMMENT_REPORT_STATUS_CHANGED.
     */
    COMMENT_REPORT_STATUS_CHANGED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The COMMENT_REPORT_DELETED.
     */
    COMMENT_REPORT_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The ANSWER_REPORT_STATUS_CHANGED.
     */
    ANSWER_REPORT_STATUS_CHANGED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The ANSWER_REPORT_DELETED.
     */
    ANSWER_REPORT_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The BUG_REPORT_UPDATED.
     */
    BUG_REPORT_UPDATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The BUG_REPORT_ISSUE_CREATED.
     */
    BUG_REPORT_ISSUE_CREATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The BUG_REPORT_DELETED.
     */
    BUG_REPORT_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The LECTURE_CREATED.
     */
    LECTURE_CREATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The LECTURE_UPDATED.
     */
    LECTURE_UPDATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The LECTURE_DELETED.
     */
    LECTURE_DELETED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The PROFESSOR_CREATED.
     */
    PROFESSOR_CREATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The PROFESSOR_UPDATED.
     */
    PROFESSOR_UPDATED(AuditActionScope.ADMINISTRATIVE),
    /**
     * The PROFESSOR_DELETED.
     */
    PROFESSOR_DELETED(AuditActionScope.ADMINISTRATIVE),

    // Student activity. The actor is the account that performed the action in the
    // student app, so the activity log can show who signed in and what they posted.
    /**
     * The USER_LOGIN.
     */
    USER_LOGIN(AuditActionScope.ACTIVITY),
    /**
     * The USER_LOGOUT.
     */
    USER_LOGOUT(AuditActionScope.ACTIVITY),
    /**
     * The COMMENT_CREATED.
     */
    COMMENT_CREATED(AuditActionScope.ACTIVITY),
    /**
     * The ANSWER_CREATED.
     */
    ANSWER_CREATED(AuditActionScope.ACTIVITY),
    /**
     * The COMMENT_VOTED.
     */
    COMMENT_VOTED(AuditActionScope.ACTIVITY),
    /**
     * The ANSWER_VOTED.
     */
    ANSWER_VOTED(AuditActionScope.ACTIVITY),
    /**
     * The RATING_SUBMITTED.
     */
    RATING_SUBMITTED(AuditActionScope.ACTIVITY),
    /**
     * The COMMENT_REPORT_CREATED.
     */
    COMMENT_REPORT_CREATED(AuditActionScope.ACTIVITY),
    /**
     * The ANSWER_REPORT_CREATED.
     */
    ANSWER_REPORT_CREATED(AuditActionScope.ACTIVITY),
    /**
     * The BUG_REPORT_CREATED.
     */
    BUG_REPORT_CREATED(AuditActionScope.ACTIVITY),

    // Refused requests. Only refusals that can be attributed to a known account are
    // recorded; an anonymous 401 has no actor and would let anyone fill the table.
    /**
     * The LOGIN_REFUSED.
     */
    LOGIN_REFUSED(AuditActionScope.ACTIVITY),
    /**
     * The ACCESS_REFUSED.
     */
    ACCESS_REFUSED(AuditActionScope.ACTIVITY);

    private final AuditActionScope scope;

    AuditAction(AuditActionScope scope) {
        this.scope = scope;
    }

    /**
     * Returns getScope.
     *
     * @return the result
     */
    public AuditActionScope getScope() {
        return scope;
    }

    /**
     * Returns of.
     *
     * @param scope the scope
     * @return the result
     */
    public static List<AuditAction> of(AuditActionScope scope) {
        return Arrays.stream(values()).filter(action -> action.scope == scope).toList();
    }
}

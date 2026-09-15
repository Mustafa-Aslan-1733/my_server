package com.pse.audit.service;

import java.util.UUID;

/**
 * Carries "the write happening right now is a reversal of audit entry X" from the revert
 * service down to {@link AuditWriter}, which stamps it onto the row it is about to insert.
 *
 * <p>A thread-local rather than an extra parameter because a revert applies through the
 * ordinary service methods — {@code updateStudent}, {@code updateComment} and the rest — and
 * threading a revert id through every one of their signatures would put the audit log's
 * bookkeeping into the domain API for a case none of them care about. The whole revert runs
 * on one request thread inside one transaction, and the value is always cleared in a finally.
 */
public final class AuditRevertContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private AuditRevertContext() {
    }

    /**
     * Executes set.
     *
     * @param revertedAuditId the revertedAuditId
     */
    public static void set(UUID revertedAuditId) {
        CURRENT.set(revertedAuditId);
    }

    /**
     * Returns get.
     *
     * @return the result
     */
    public static UUID get() {
        return CURRENT.get();
    }

    /**
     * Executes clear.
     */
    public static void clear() {
        CURRENT.remove();
    }
}

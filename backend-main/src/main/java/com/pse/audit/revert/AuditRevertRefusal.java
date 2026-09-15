package com.pse.audit.revert;

/**
 * Why one audit entry cannot be reversed. Separated by cause on purpose: a button rendered
 * on an open tab can go stale between the page load and the click, and "it did not work"
 * without a reason leaves the operator guessing which of several very different things
 * happened.
 */
public enum AuditRevertRefusal {

    /** The action is not a field edit. Deletions and creations are in this category. */
    ACTION_NOT_REVERTIBLE,

    /** The entry is older than the configured revert window. */
    WINDOW_EXPIRED,

    /** Something changed the field after this entry, so reverting would clobber it. */
    VALUE_CHANGED,

    /** A later entry already reversed this one. */
    ALREADY_REVERTED,

    /** The record the entry is about no longer exists. */
    TARGET_MISSING
}

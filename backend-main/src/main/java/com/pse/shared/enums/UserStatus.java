package com.pse.shared.enums;

/**
 * Lists UserStatus.
 */
public enum UserStatus {
    /**
     * The ACTIVE.
     */
    ACTIVE,
    /**
     * The INACTIVE.
     */
    INACTIVE,
    /**
     * The BLOCKED.
     */
    BLOCKED,
    /**
     * The DELETED.
     */
    DELETED;

    /**
     * Whether an account in this state may complete a login.
     *
     * <p>Asked in two places -- before a code is mailed, and again before one is consumed --
     * and they have to agree: the first keeps a blocked account from being sent a code, the
     * second keeps a code that was already in flight from being spent. They were two copies
     * of the same {@code != BLOCKED && != DELETED} test, and a status added to this enum
     * would have had to be remembered in both.
     *
     * @return the result
     */
    public boolean canLogIn() {
        return this != BLOCKED && this != DELETED;
    }
}

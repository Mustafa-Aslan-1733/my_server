package com.pse.shared.enums;

/** Whether a bug report has a tracker issue behind it. */
public enum IssueState {

    /** No issue has been created for this report. */
    NONE,

    /** An issue exists; {@code issueUrl} points at it. */
    CREATED,

    /** Creation was attempted and the tracker refused or could not be reached. Retryable. */
    FAILED
}

package com.pse.moderation.service;

/**
 * Creates issues in the project's tracker.
 *
 * <p>An interface rather than a concrete client for one reason that matters and one that is
 * convenient: the credential must never leave the backend, so nothing about this may be
 * reachable from a browser; and tests substitute a double, so the suite never depends on a
 * tracker being up or on network access.
 */
public interface GitLabClient {

    /**
     * Whether the integration is configured. False disables the endpoint that uses it.
     *
     * @return the result
     */
    boolean isEnabled();

    /**
     * Opens an issue and returns where it landed.
     *
     * @throws com.pse.shared.error.ApiException if the tracker refuses or cannot be reached
     * @param title the title
     * @param description the description
     * @return the result
     */
    CreatedIssue createIssue(String title, String description);


    /**
     * Creates CreatedIssue.
     *
     * @param url the url
     * @param iid the iid
     */
    record CreatedIssue(String url, Integer iid) {
    }
}

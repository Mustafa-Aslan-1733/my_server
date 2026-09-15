package com.pse.moderation.dto.response;

import com.pse.shared.enums.IssueState;

/**
 * The result of asking for a tracker issue. Returned for a freshly created issue and for one
 * that already existed alike, so a caller that retries gets the same answer rather than an
 * error it has to special-case.
 *
 * @param message the message
 * @param success the success
 * @param issueUrl the issueUrl
 * @param issueIid the issueIid
 * @param issueState the issueState
 */
public record GitLabIssueResponse(
        String message,
        boolean success,
        String issueUrl,
        Integer issueIid,
        IssueState issueState
) {
}

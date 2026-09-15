package com.pse.moderation.dto.request;

/**
 * Body of {@code POST /users/{id}/warnings} and {@code PATCH /users/{id}/warnings/{warningId}}.
 *
 * <p>Only the message is supplied by the client. The target is the path id and the issuing
 * admin is the token principal — a client does not get to choose who a warning is attributed
 * to. This record used to carry {@code userID} and {@code createdFrom} as well; both were
 * read nowhere, and a caller that trusted them could believe it had attributed a warning to
 * an admin the backend never recorded.
 *
 * @param message the message
 */
public record WarningRequest(
    String message
) {
}

package com.pse.system.dto;


/**
 * Answer of {@code GET /system/status}.
 *
 * <p>The endpoint always answers {@code 200} as long as the API itself is serving, and
 * reports a failing database in {@code status} instead. A page that has to show "the API
 * is up but the database is not" cannot do that if the request itself fails.
 *
 * @param status         {@code OK}, {@code DEGRADED} (database up, a read failed), or
 *                       {@code DOWN} (database unreachable)
 *
 * @param checkedAt      UTC ISO 8601 timestamp of this check
 * @param startedAt      UTC ISO 8601 timestamp of the last backend start
 * @param uptimeSeconds  seconds since {@code startedAt}
 * @param counts         table counts, {@code null} when they could not be read
 * @param lastWrite      newest audit event, {@code null} when there is none
 *
 * <p>Those two are the schema's two known gaps. {@code @Schema(nullable = true)} works on a
 * scalar -- the cursors and {@code database.error} carry it -- but on a property that is a
 * {@code $ref} springdoc emits {@code {"type":"null","$ref":…}}, which claims the value can
 * only ever be null. Both forms are wrong, so neither is used and
 * {@code OpenApiResponseValidationTests} names the two pointers instead. The fix is one
 * annotation each when springdoc emits the 3.1 {@code anyOf} form.
 *
 * @param gitlabEnabled  whether the GitLab issue integration is configured. The panel
 *                       hides the "create issue" action when it is not, rather than
 *                       discovering it by pressing the button and getting a 503.
 *
 * @param locationLookupEnabled whether login locations can be resolved -- F-8. Without it
 *                       an unset or revoked IPINFO_TOKEN shows up only as every login mail
 *                       saying "Unknown", which is what made the original report
 *                       ("approximate location stopped working") undiagnosable.
 *
 * @param message the message
 * @param success the success
 * @param database the database
 */
public record SystemStatusResponse(
        String message,
        boolean success,
        String status,
        String checkedAt,
        String startedAt,
        long uptimeSeconds,
        SystemDatabaseResponse database,
        SystemCountsResponse counts,
        SystemLastWriteResponse lastWrite,
        boolean gitlabEnabled,
        boolean locationLookupEnabled
) {
}

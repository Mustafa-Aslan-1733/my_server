package com.pse.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The tracker a bug report can be forwarded to.
 *
 * <p>Unset means disabled, and that is a supported state rather than a misconfiguration:
 * {@code POST /reports/{id}/gitlab-issue} answers 503 and {@code GET /system/status} reports
 * {@code gitlabEnabled=false}. The token is read from the environment and is never returned
 * by any endpoint.
 *
 * @param baseUrl the baseUrl
 * @param projectId the projectId
 * @param token the token
 * @param timeout the timeout
 */
@ConfigurationProperties(prefix = "app.gitlab")
public record GitLabProperties(
        @DefaultValue("") String baseUrl,
        @DefaultValue("") String projectId,
        @DefaultValue("") String token,
        @DefaultValue("PT10S") Duration timeout
) {
}

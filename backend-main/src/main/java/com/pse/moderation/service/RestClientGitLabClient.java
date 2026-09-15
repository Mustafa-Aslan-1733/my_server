package com.pse.moderation.service;

import com.pse.shared.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pse.config.properties.GitLabProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Talks to the GitLab issues API over HTTP.
 *
 * <p>The token is read from the environment and used only as a request header. It is never
 * logged, never returned in a response and never sent anywhere else — a token that reaches
 * the browser is a leaked token, which is the whole reason issue creation lives in the
 * backend rather than in the admin panel.
 */
@Component
public class RestClientGitLabClient implements GitLabClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientGitLabClient.class);

    private final RestClient restClient;
    private final String projectId;
    private final String token;

    // Explicit, because the class now has a second constructor for the tests: with two
    // candidates and neither marked, the container looks for a default constructor and the
    // bean fails to build.
    /**
     * Creates RestClientGitLabClient.
     *
     * @param properties the properties
     */
    @Autowired
    public RestClientGitLabClient(GitLabProperties properties) {
        this(
                restClientFor(properties.baseUrl(), properties.timeout()),
                properties.projectId(),
                properties.token()
        );
    }

    /**
     * The seam the unit tests use: a client assembled elsewhere, so the HTTP conversation can
     * be driven by {@code MockRestServiceServer} without a tracker being reachable.
     *
     * @param restClient the client to talk through, or {@code null} when no base URL is
     *                   configured — which is what turns the integration off
     *
     * @param projectId the projectId
     * @param token the token
     */
    RestClientGitLabClient(RestClient restClient, String projectId, String token) {
        this.restClient = restClient;
        this.projectId = projectId == null ? "" : projectId.trim();
        this.token = token == null ? "" : token.trim();
    }

    private static RestClient restClientFor(String baseUrl, Duration timeout) {
        String trimmedBase = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return trimmedBase.isEmpty()
                ? null
                : RestClient.builder()
                        .baseUrl(trimmedBase)
                        // Explicit timeouts: an unreachable tracker must fail the one request
                        // that asked for an issue, not hold a request thread indefinitely.
                        .requestFactory(timeoutRequestFactory(timeout))
                        .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory timeoutRequestFactory(
            Duration timeout
    ) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }

    @Override
    public boolean isEnabled() {
        return restClient != null && !projectId.isEmpty() && !token.isEmpty();
    }

    @Override
    public CreatedIssue createIssue(String title, String description) {
        if (!isEnabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "GitLab integration is not configured");
        }
        try {
            Map<?, ?> body = restClient.post()
                    // The project id goes in as a URI variable rather than as text spliced
                    // into the path: RestClient encodes variables itself, so a path-shaped id
                    // ("group/project") becomes one segment. Encoding it here as well handed
                    // GitLab a percent sign it encoded again -- see F-18.
                    .uri("/api/v4/projects/{projectId}/issues", projectId)
                    .header("PRIVATE-TOKEN", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", title, "description", description))
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("web_url") == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "GitLab did not return an issue");
            }
            Object iid = body.get("iid");
            return new CreatedIssue(
                    String.valueOf(body.get("web_url")),
                    iid instanceof Number number ? number.intValue() : null
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // The message may quote the request; log the class and let the caller answer with
            // something that cannot echo a credential back.
            LOGGER.error("GitLab issue creation failed: {}", exception.getClass().getName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach GitLab");
        }
    }
}

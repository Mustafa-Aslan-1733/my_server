package com.pse.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * An HTTP client for the end-to-end journeys.
 *
 * <p><b>Why {@link RestClient} and not something more obvious.</b> {@code TestRestTemplate} is
 * the usual answer and is <em>not on this classpath</em>: Spring Boot 4 reorganised the test
 * starters, and this project pulls {@code spring-boot-starter-webmvc-test}.
 * {@code WebTestClient} is present in {@code spring-test} but is the reactive one and would drag
 * WebFlux into a servlet application for the sake of a test. {@code RestClient} needs no new
 * dependency, and it is the same client the application itself uses in
 * {@code RestClientGitLabClient}.
 *
 * <p><b>Why a wrapper at all.</b> {@code RestClient} throws on 4xx and 5xx, and half of these
 * journeys are about a refusal -- an unauthorised call, an expired session, a student reaching
 * for an admin route. Every one of those would otherwise need its own try/catch. The status
 * handler here is a no-op, so a response is a value rather than an exception, and a journey reads
 * as a sequence of requests and assertions.
 */
public final class E2EClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient http;
    private final String baseUrl;
    private final String bearerToken;

    private E2EClient(String baseUrl, String bearerToken) {
        this.baseUrl = baseUrl;
        this.bearerToken = bearerToken;
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                // Every status is "handled", so none of them throws.
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    /** A client with no credentials, for the journeys that start signed out. */
    public static E2EClient at(int port) {
        return new E2EClient("http://localhost:" + port, null);
    }

    /** The same server, now carrying a session. Returns a new client; this one is unchanged. */
    public E2EClient withToken(String token) {
        return new E2EClient(baseUrl, token);
    }

    public Response get(String path) {
        return send(http.get().uri(path));
    }

    public Response post(String path, String json) {
        return send(http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json));
    }

    public Response post(String path) {
        return send(http.post().uri(path));
    }

    public Response patch(String path, String json) {
        return send(http.patch().uri(path).contentType(MediaType.APPLICATION_JSON).body(json));
    }

    private Response send(RestClient.RequestHeadersSpec<?> spec) {
        if (bearerToken != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        ResponseEntity<String> entity = spec.retrieve().toEntity(String.class);
        return new Response(entity.getStatusCode().value(), entity.getBody());
    }

    /** A response as a value: the status, and the body parsed on demand. */
    public record Response(int status, String rawBody) {

        public JsonNode body() {
            try {
                return rawBody == null || rawBody.isBlank()
                        ? MAPPER.createObjectNode()
                        : MAPPER.readTree(rawBody);
            } catch (Exception exception) {
                throw new IllegalStateException("Not JSON: " + rawBody, exception);
            }
        }

        /** {@code text("authToken")} or {@code text("/auditLogs/0/action")} for a pointer. */
        public String text(String field) {
            JsonNode node = field.startsWith("/") ? body().at(field) : body().get(field);
            return node == null || node.isNull() ? null : node.asText();
        }
    }
}

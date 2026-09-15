package com.pse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.pse.auth.service.LoginCodeDelivery;
import com.pse.support.DatabaseReset;
import com.pse.support.MappedRoutes;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fifth question, after "does it work", "who may call it", "what status does it answer" and
 * "does it describe itself correctly": <b>what does it say when it refuses?</b>
 *
 * <p>The messages are a contract. The admin panel puts them in front of an operator, and the
 * Android client parses the object they arrive in. Nothing audited either until now, and the
 * body shape is the half a client actually breaks on: an error that is not
 * {@code {message, success}} is one the client cannot read at all.
 *
 * <p>Like the other sweeps, the route list comes from Spring's own mapping through
 * {@link MappedRoutes} rather than from a list somebody maintains. What is provoked here is
 * only what can be provoked on <em>any</em> route without knowing its fixtures -- an anonymous
 * call, a verb the path does not map, a body in a media type nothing reads, and malformed JSON
 * in the right one. That is four of the eight statuses in {@code admin-api.md}'s preamble, and
 * it is deliberately not all of them: a 409 on a duplicate name needs that route's fixture, and
 * a sweep that pretended otherwise would be asserting against its own setup.
 *
 * <p>The style rules are mechanical on purpose. "Is it English" cannot be tested; "is it ASCII,
 * does it start with a capital, does it avoid a trailing full stop, does it still contain a
 * placeholder" can be, and those are what actually go wrong when a message is written in a
 * hurry.
 */
@ApiIntegrationTest
class ApiErrorMessageContractTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ADMIN_EMAIL = "admin@student.kit.edu";

    /**
     * The only keys an error body may carry. {@code reason} is {@code ApiErrorResponse}'s
     * optional machine-readable code, omitted when null; anything else means a route has
     * grown a bespoke error shape that clients were never told about.
     */
    private static final List<String> ALLOWED_KEYS = List.of("message", "success", "reason");

    @Autowired MockMvc mockMvc;
    @Autowired RequestMappingHandlerMapping handlerMapping;
    @Autowired DatabaseReset databaseReset;
    @Autowired TestDeliveryConfig.CapturingLoginCodeDelivery codeDelivery;

    @BeforeEach
    void reset() {
        databaseReset.all();
        codeDelivery.clear();
    }

    /** One provoked error response: where it came from, so a failure names it. */
    private record Refusal(String origin, int status, String body) { }

    @Test
    void everyRefusalCarriesTheProjectErrorBodyAndNothingElse() throws Exception {
        List<Refusal> refusals = provokeRefusals();

        assertThat(refusals.size() >= 100)
                .as("a sweep that provoked almost nothing would pass every rule below it; "
                        + "only " + refusals.size() + " refusals were collected")
                .isTrue();

        List<String> wrong = new ArrayList<>();
        for (Refusal refusal : refusals) {
            JsonNode body;
            try {
                body = MAPPER.readTree(refusal.body());
            } catch (Exception notJson) {
                wrong.add(refusal.origin() + " -> " + refusal.status()
                        + ": body is not JSON: " + preview(refusal.body()));
                continue;
            }
            if (!body.isObject()) {
                wrong.add(refusal.origin() + " -> " + refusal.status() + ": body is not an object");
                continue;
            }
            body.fieldNames().forEachRemaining(field -> {
                if (!ALLOWED_KEYS.contains(field)) {
                    wrong.add(refusal.origin() + " -> " + refusal.status()
                            + ": unexpected field '" + field + "'");
                }
            });
            if (!body.has("message") || !body.has("success")) {
                wrong.add(refusal.origin() + " -> " + refusal.status()
                        + ": missing message or success");
                continue;
            }
            if (body.get("success").asBoolean()) {
                wrong.add(refusal.origin() + " -> " + refusal.status() + ": success is true");
            }
        }

        assertThat(wrong.isEmpty()).as("every error body must be {message, success} with an "
                + "optional reason, and success must be false. These were not: " + wrong).isTrue();
    }

    @Test
    void everyRefusalMessageFollowsTheHouseStyle() throws Exception {
        Map<String, String> offenders = new LinkedHashMap<>();

        for (Refusal refusal : provokeRefusals()) {
            String message;
            try {
                message = JsonPath.read(refusal.body(), "$.message");
            } catch (Exception unreadable) {
                continue; // the shape test above owns that failure
            }
            String complaint = styleComplaint(message);
            if (complaint != null) {
                offenders.put(message, complaint + "  (" + refusal.origin() + ")");
            }
        }

        assertThat(offenders.isEmpty())
                .as("error messages the panel shows an operator, in this project's style: "
                        + "one sentence, no trailing full stop, opening capital, ASCII, no "
                        + "unfilled placeholder. These break it: " + offenders)
                .isTrue();
    }

    /**
     * Mechanical rules only, and each one is a mistake that has actually been made in a
     * message somewhere: a leftover {@code {placeholder}}, a stray full stop that reads wrong
     * next to its neighbours, a lowercase opening after a capitalised set, a non-ASCII
     * character that survives a copy-paste but not every client's font.
     *
     * @return what is wrong with it, or null when nothing is
     */
    private static String styleComplaint(String message) {
        if (message == null || message.isBlank()) {
            return "blank";
        }
        if (!message.equals(message.strip())) {
            return "leading or trailing whitespace";
        }
        if (!Character.isUpperCase(message.charAt(0))) {
            return "does not start with a capital";
        }
        if (message.endsWith(".")) {
            return "ends with a full stop";
        }
        if (message.chars().anyMatch(c -> c > 127)) {
            return "contains a non-ASCII character";
        }
        if (message.contains("{") || message.contains("}")) {
            return "contains an unfilled placeholder";
        }
        if (message.length() > 200) {
            return "longer than 200 characters";
        }
        return null;
    }

    /**
     * Four provocations that need no per-route knowledge, over every mapped route.
     */
    private List<Refusal> provokeRefusals() throws Exception {
        List<Refusal> refusals = new ArrayList<>();
        List<MappedRoutes.Route> routes = MappedRoutes.application(handlerMapping);

        for (MappedRoutes.Route route : routes) {
            // A route that declares no verb answers all of them; probed as a GET, the reading
            // ApiAuthorizationMatrixTests takes for the same case.
            String verb = route.verb() == null ? "GET" : route.verb();
            String path = concrete(route.pattern());
            MockHttpServletRequestBuilder anonymous = requestFor(verb, path);
            if (anonymous == null) {
                continue;
            }
            collect(refusals, "anonymous " + verb + " " + route.pattern(), anonymous);
        }

        String adminToken = adminSession();

        for (MappedRoutes.Route route : routes) {
            if (route.verb() == null || !readsBody(route)) {
                continue;
            }
            String path = concrete(route.pattern());

            MockHttpServletRequestBuilder wrongType = requestFor(route.verb(), path);
            if (wrongType != null) {
                collect(refusals, "text/plain " + route.verbAndPattern(),
                        wrongType.header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("this is not json"));
            }

            MockHttpServletRequestBuilder badJson = requestFor(route.verb(), path);
            if (badJson != null) {
                collect(refusals, "malformed json " + route.verbAndPattern(),
                        badJson.header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ this is not json"));
            }
        }

        // A path that exists called with a verb it does not map: 405 from one handler, on a
        // route chosen because every application maps GET on it and nothing else.
        collect(refusals, "wrong verb DELETE /health", delete("/health"));

        return refusals;
    }

    private void collect(List<Refusal> into, String origin, MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        int status = result.getResponse().getStatus();
        if (status < 400) {
            return;
        }
        into.add(new Refusal(origin, status, result.getResponse().getContentAsString()));
    }

    private static boolean readsBody(MappedRoutes.Route route) {
        return java.util.Arrays.stream(route.handler().getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(
                        org.springframework.web.bind.annotation.RequestBody.class));
    }

    private static String concrete(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
    }

    private static MockHttpServletRequestBuilder requestFor(String verb, String path) {
        return switch (verb) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            case "PUT" -> put(path);
            default -> null;
        };
    }

    private static String preview(String body) {
        String flat = body == null ? "" : body.replaceAll("\\s+", " ").strip();
        return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
    }

    private String adminSession() throws Exception {
        mockMvc.perform(post("/admin/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(ADMIN_EMAIL)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"loginToken\":\"%s\"}"
                                .formatted(ADMIN_EMAIL, codeDelivery.codeFor(ADMIN_EMAIL))))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(login.getResponse().getContentAsString(), "$.authToken");
    }
}

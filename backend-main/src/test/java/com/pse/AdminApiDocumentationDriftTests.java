package com.pse;

import com.jayway.jsonpath.JsonPath;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;
import com.pse.support.MappedRoutes;
import com.pse.support.TestDeliveryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code docs/admin-api.md} against the application it describes.
 *
 * <p><b>Why this exists.</b> That document has fallen behind the code four times — F-5 (a
 * status that had changed), F-16 (a 404 that had become a 405), F-22 (a body that no longer
 * existed), F-26 (a field that had never been right). Every one was corrected by hand, and
 * nothing stopped the next. This repository's own rule is that a defect fixed once is worth
 * grepping for; a *document* that has drifted four times is worth a test.
 *
 * <p><b>What it reads.</b> The table between the {@code contract-table} markers at the end of
 * the document — a machine-readable mirror of claims the prose makes in sentences. The prose
 * is not generated from anything and is not touched: it carries the *reasons*, which are the
 * half of that document worth having, and a schema cannot produce them. The generated OpenAPI
 * schema is no help here either — the controllers declare no {@code @ApiResponse}, so it knows
 * only about 200 and could never check a status claim.
 *
 * <p><b>Both directions, because one is half a test.</b> A route claimed and not mapped
 * shortchanges the panel; a route mapped and not claimed is a surface nobody documented. F-16
 * was the first kind and the legacy-path split could easily produce the second.
 *
 * <p><b>What it does not catch, found by trying to make it fail.</b> The {@code 404} probe
 * needs an id to invent, so a {@code 404} claimed on a route with no path variable is carried
 * in the table and never checked — adding one to {@code GET /admin/comments} passes. The
 * probes are chosen to be the ones that can run on any route without knowing its fixtures, and
 * that is the price. What the table still buys in those cells is the third direction: the
 * claim is written where a person editing the prose has to edit it too.
 */
@ApiIntegrationTest
class AdminApiDocumentationDriftTests {

    private static final Path DOCUMENT = Path.of("docs/admin-api.md");
    private static final Pattern ROW =
            Pattern.compile("^\\|\\s*`([A-Z]+) ([^`]+)`\\s*\\|\\s*([0-9,\\s]+)\\|\\s*$");

    private static final String ADMIN_EMAIL = "admin@student.kit.edu";
    private static final String STUDENT_EMAIL = "drift-student@student.kit.edu";

    /**
     * Mapped, and deliberately not in the table. Legacy, undocumented in the prose too, and
     * being retired in favour of {@code GET /admin/auth/me} — so documenting it now would ask
     * the panel to read about a route it is being moved off. Named here rather than silently
     * skipped, the way {@code PUBLIC_ROUTES} names its own.
     */
    private static final Set<String> UNDOCUMENTED_ON_PURPOSE = Set.of("GET /admins/validate");

    /**
     * Routes whose {@code 401} / {@code 403} are about the <em>credentials in the body</em>,
     * not about the caller's session — so an anonymous probe reaches the handler and is
     * answered {@code 400} for the missing body instead. The document's own preamble draws
     * exactly this line: "Except for the two login operations and health, the Admin Web
     * endpoints require Authorization".
     *
     * <p>Their refusals are real and are covered by {@code AuthApiIntegrationTests} and
     * {@code AdminApiPathSplitTests}, which can mint the accounts each case needs. Named here
     * so the exemption is a decision rather than a gap.
     */
    private static final Set<String> REFUSALS_ARE_ABOUT_THE_BODY = Set.of(
            "POST /admin/auth/login",
            "POST /admin/auth/request-login");

    /**
     * Statuses no probe can produce without a route's own fixture: a duplicate name, an
     * unreachable tracker, a spent rate limit, a body that is valid for exactly one endpoint.
     * Listed rather than skipped silently — a sweep that quietly ignores what it cannot check
     * reads the same as one that checked it.
     */
    private static final Set<Integer> NOT_PROVOKED_HERE = Set.of(200, 400, 409, 429, 502, 503);

    @Autowired MockMvc mockMvc;
    @Autowired RequestMappingHandlerMapping handlerMapping;
    @Autowired DatabaseReset databaseReset;
    @Autowired TestDeliveryConfig.CapturingLoginCodeDelivery codeDelivery;

    @BeforeEach
    void reset() {
        databaseReset.all();
        codeDelivery.clear();
    }

    // ---------- the two directions ----------

    @Test
    void everyRouteTheDocumentClaimsIsMappedByTheApplication() throws IOException {
        Map<String, Set<Integer>> documented = documentedContract();

        assertThat(documented.size() >= 40)
                .as("the contract table parsed to only " + documented.size() + " rows, which "
                        + "means the markers or the row format moved and this sweep is looking "
                        + "at almost nothing")
                .isTrue();

        Set<String> mapped = mappedRoutes();
        List<String> missing = documented.keySet().stream()
                .filter(route -> !mapped.contains(route))
                .sorted()
                .toList();

        assertThat(missing.isEmpty())
                .as("docs/admin-api.md claims routes the application does not map. Either the "
                        + "route was removed and the document was not, or the table has a typo: "
                        + missing)
                .isTrue();
    }

    @Test
    void everyAdministrativeRouteTheApplicationMapsIsInTheDocument() throws IOException {
        Set<String> documented = documentedContract().keySet();

        List<String> undocumented = mappedRoutes().stream()
                .filter(route -> route.contains(" /admin"))
                .filter(route -> !documented.contains(route))
                .filter(route -> !UNDOCUMENTED_ON_PURPOSE.contains(route))
                .sorted()
                .toList();

        assertThat(undocumented.isEmpty())
                .as("the application maps administrative routes docs/admin-api.md does not "
                        + "describe. Add them to the prose and to the contract table, or name "
                        + "them in UNDOCUMENTED_ON_PURPOSE with the reason: " + undocumented)
                .isTrue();
    }

    // ---------- the claims, against real responses ----------

    @Test
    void everyClaimedRefusalIsTheStatusTheApplicationActuallyAnswers() throws Exception {
        Map<String, Set<Integer>> documented = documentedContract();
        String adminToken = adminSession();
        String studentToken = studentSession();

        List<String> wrong = new ArrayList<>();
        int checked = 0;

        for (Map.Entry<String, Set<Integer>> entry : documented.entrySet()) {
            String route = entry.getKey();
            Set<Integer> claimed = entry.getValue();
            String verb = route.substring(0, route.indexOf(' '));
            String pattern = route.substring(route.indexOf(' ') + 1);
            String path = concrete(pattern);

            boolean sessionScoped = !REFUSALS_ARE_ABOUT_THE_BODY.contains(route);

            if (claimed.contains(401) && sessionScoped) {
                checked++;
                int actual = statusOf(request(verb, path));
                if (actual != 401) {
                    wrong.add(route + " claims 401 for an anonymous caller, answered " + actual);
                }
            }
            if (claimed.contains(403) && sessionScoped) {
                checked++;
                int actual = statusOf(request(verb, path)
                        .header("Authorization", "Bearer " + studentToken));
                if (actual != 403) {
                    wrong.add(route + " claims 403 for a non-administrator, answered " + actual);
                }
            }
            if (claimed.contains(415) && readsBody(route)) {
                checked++;
                int actual = statusOf(request(verb, path)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("this is not json"));
                if (actual != 415) {
                    wrong.add(route + " claims 415 for an unreadable media type, answered " + actual);
                }
            }
            // 404 is only provoked where an id can be made up and no body is needed. A PATCH
            // or POST without a body answers 400 before it ever looks the id up, so probing
            // those would test the validator rather than the claim.
            if (claimed.contains(404) && pattern.contains("{")
                    && (verb.equals("GET") || verb.equals("DELETE"))) {
                checked++;
                int actual = statusOf(request(verb, path)
                        .header("Authorization", "Bearer " + adminToken));
                if (actual != 404) {
                    wrong.add(route + " claims 404 for an id that names nothing, answered " + actual);
                }
            }
        }

        assertThat(checked >= 100)
                .as("only " + checked + " claims were probed; a sweep that checks almost "
                        + "nothing passes for the wrong reason")
                .isTrue();

        assertThat(wrong.isEmpty())
                .as("docs/admin-api.md claims statuses the application does not answer: " + wrong)
                .isTrue();
    }

    /**
     * The claims this sweep does <b>not</b> verify, stated as a number rather than left
     * implicit. If this count grows, either the document gained claims nothing checks or a
     * probe stopped running — both worth noticing.
     */
    @Test
    void theClaimsThisSweepCannotProvokeAreKnownAndCounted() throws IOException {
        Map<String, Set<Integer>> documented = documentedContract();

        long unprovoked = documented.values().stream()
                .flatMap(Set::stream)
                .filter(NOT_PROVOKED_HERE::contains)
                .count();

        assertThat(unprovoked)
                .as("claims that need a route's own fixture -- a duplicate name, an unreachable "
                        + "tracker, a spent limiter, a body valid for one endpoint. They are "
                        + "covered by that route's own tests, not here.")
                .isEqualTo(88);
    }

    // ---------- reading the document ----------

    /** The table between the markers, as {@code "GET /admin/users" -> {200, 400, 401, 403}}. */
    private static Map<String, Set<Integer>> documentedContract() throws IOException {
        List<String> lines = Files.readAllLines(DOCUMENT);
        Map<String, Set<Integer>> contract = new LinkedHashMap<>();
        boolean inside = false;

        for (String line : lines) {
            if (line.contains("contract-table:start")) {
                inside = true;
                continue;
            }
            if (line.contains("contract-table:end")) {
                inside = false;
                continue;
            }
            if (!inside) {
                continue;
            }
            Matcher matcher = ROW.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            Set<Integer> statuses = new TreeSet<>();
            for (String status : matcher.group(3).trim().split("\\s*,\\s*")) {
                statuses.add(Integer.parseInt(status.trim()));
            }
            contract.put(matcher.group(1) + " " + matcher.group(2), statuses);
        }
        return contract;
    }

    private Set<String> mappedRoutes() {
        Set<String> routes = new TreeSet<>();
        for (MappedRoutes.Route route : MappedRoutes.application(handlerMapping)) {
            routes.add((route.verb() == null ? "GET" : route.verb()) + " " + route.pattern());
        }
        return routes;
    }

    private boolean readsBody(String route) {
        for (MappedRoutes.Route mapped : MappedRoutes.application(handlerMapping)) {
            String name = (mapped.verb() == null ? "GET" : mapped.verb()) + " " + mapped.pattern();
            if (!name.equals(route)) {
                continue;
            }
            return java.util.Arrays.stream(mapped.handler().getMethodParameters())
                    .anyMatch(parameter -> parameter.hasParameterAnnotation(
                            org.springframework.web.bind.annotation.RequestBody.class));
        }
        return false;
    }

    // ---------- plumbing ----------

    private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static String concrete(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
    }

    private static MockHttpServletRequestBuilder request(String verb, String path) {
        return switch (verb) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            case "PUT" -> put(path);
            default -> throw new IllegalArgumentException("unmapped verb in the table: " + verb);
        };
    }

    private String adminSession() throws Exception {
        return sessionFor("/admin/auth", ADMIN_EMAIL);
    }

    private String studentSession() throws Exception {
        return sessionFor("/auth", STUDENT_EMAIL);
    }

    private String sessionFor(String prefix, String email) throws Exception {
        mockMvc.perform(post(prefix + "/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post(prefix + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"loginToken\":\"%s\"}"
                                .formatted(email, codeDelivery.codeFor(email))))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(login.getResponse().getContentAsString(), "$.authToken");
    }
}

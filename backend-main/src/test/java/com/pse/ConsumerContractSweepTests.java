package com.pse;

import com.pse.support.ApiIntegrationTest;
import com.pse.support.MappedRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two consumer repositories' contracts, against the application they describe.
 *
 * <p><b>The question this answers, and the one it does not.</b> Every other sweep here asks
 * whether a route behaves correctly. None of them asks whether that behaviour is what the
 * clients actually read, and the two are not the same question: the suite was fully green
 * while {@code GET /auth/me} was unmapped and the admin panel could not sign anybody in, and
 * while {@code POST /answers/report} answered 405 to every report the Android app has ever
 * filed. Both were found by reading the consumer audits in {@code docs/}, not by a test. This
 * class is that reading, mechanised. It does <b>not</b> check that the client code is itself
 * correct -- that is the consumers' repositories' business, and their audits say so.
 *
 * <p><b>What it reads.</b> The {@code consumer-contract} block at the end of
 * {@code docs/adminweb-consumer-contract.md} and
 * {@code docs/frontend-consumer-contract.md} -- one row per route, with the fields that
 * client reads written out in full. The blocks are delimited and hand-written for the same
 * reason {@code admin-api.md}'s contract table is (ADR 0012): the prose carries the reasons,
 * a machine cannot produce those, and a claim a test can read has to live where the person
 * editing the prose has to edit it too.
 *
 * <p><b>Why the field check is the part worth having.</b> Status agreement is already covered
 * three times over. Field-name agreement is covered nowhere, and it is the failure both
 * consumers describe as silent: the admin panel's {@code expectArray} verifies arrayness and
 * casts the element, so a renamed field inside a list renders as an empty cell; Gson leaves an
 * unmatched field {@code null} rather than failing. A rename that no test catches reaches a
 * user as a blank screen.
 *
 * <p><b>Resolved against the response type, not against a live response.</b> Reflection over
 * the handler's return type reaches every field of every route without needing a fixture per
 * endpoint, including nested ones no probe would populate -- a lecture with professors, an
 * audit entry with a target. What it cannot see is a field whose <em>value</em> is wrong,
 * which is what the endpoint's own tests are for.
 *
 * <p><b>Both directions.</b> A consumer route the application does not map is red. A route the
 * application maps that no consumer claims is counted rather than failed, because most of them
 * are legitimately unused -- but the count moving means either a new surface nobody documented
 * or a consumer list that has gone stale.
 */
@ApiIntegrationTest
class ConsumerContractSweepTests {

    /**
     * The consumer documents, by the name their repository goes by. The label travels with
     * every row and into every failure message: 42 routes belong to one client and 24 to the
     * other, and a decision to retire a route needs to know which.
     */
    private static final Map<String, Path> CONSUMERS = Map.of(
            "admin-web", Path.of("docs/adminweb-consumer-contract.md"),
            "android", Path.of("docs/frontend-consumer-contract.md"));

    /**
     * Application routes no consumer claims, as a number rather than a silence.
     *
     * <p>Most are legitimate: the {@code /admin} twins of paths the panel still calls
     * unprefixed, the student endpoints the panel has no use for, {@code /health}. Stating the
     * count means a route added without a consumer shows up as a deliberate edit here rather
     * than as nothing at all -- the same guard {@code AdminApiDocumentationDriftTests} puts on
     * the claims it cannot provoke.
     */
    private static final int UNCLAIMED_BY_EITHER_CONSUMER = 60;

    /**
     * Every field a consumer reads that this API does not serve, with what it costs.
     *
     * <p>Asserted as an exact set rather than skipped, so it moves in both directions: a new
     * mismatch is red, and one of these being fixed is <em>also</em> red, because the entry
     * then describes something that is no longer true. A list that can only grow is the
     * hand-kept route list again.
     *
     * <p>Two kinds are in here and the distinction is the point. Four are <b>fallbacks</b> --
     * the panel reads {@code username} or {@code kitEmail} first, both of which exist, and
     * names a second key for an older shape that never arrives. Nothing is wrong and nothing
     * needs doing. Two are <b>live defects in the panel</b>: it reads fields this API has
     * never promised, and its own null-guards turn that into an author column reading
     * "Unknown" on every row and a scores column that is always empty. They are silent by
     * construction -- exactly what this sweep exists to find, and they were found by its
     * first run.
     */
    private static final Map<String, String> READ_BUT_NOT_SERVED = Map.ofEntries(
            Map.entry("admin-web GET /auth/me reads 'user.name'",
                    "fallback: the panel prefers user.username, which is served"),
            Map.entry("admin-web GET /auth/me reads 'user.email'",
                    "fallback: the panel prefers user.kitEmail, which is served"),
            Map.entry("admin-web GET /users reads 'users[].email'",
                    "fallback: the panel prefers users[].kitEmail, which is served"),
            Map.entry("admin-web GET /users/{id} reads 'email'",
                    "fallback: the panel prefers kitEmail, which is served"),
            Map.entry("admin-web POST /audit-logs/{id}/revert reads 'reason'",
                    "read off the 409 body, not the 200 body. ApiErrorResponse carries reason; "
                            + "BasicResponse is the success shape and never did"),
            Map.entry("admin-web GET /ratings reads 'ratings[].author.name'",
                    "LIVE PANEL DEFECT. The field is student, and docs/admin-api.md has "
                            + "documented it as student with a worked example since the "
                            + "endpoint existed. The panel's mapper reads author?.name with a "
                            + "fallback, so every row of the ratings table shows 'Unknown' "
                            + "instead of failing. Reported in docs/adminweb-tasks.md"),
            Map.entry("admin-web GET /ratings reads 'ratings[].scores'",
                    "LIVE PANEL DEFECT, the same row. The field is topics, a list of "
                            + "{category, value}, not an open map called scores. The panel "
                            + "reads scores ?? {}, so the scores column is empty on every row. "
                            + "Reported in docs/adminweb-tasks.md"));

    @Autowired RequestMappingHandlerMapping handlerMapping;

    // ---------- the two directions ----------

    @Test
    void everyRouteAConsumerCallsIsMappedWithTheVerbItUses() throws IOException {
        List<ConsumerRoute> consumerRoutes = consumerRoutes();

        // Per consumer, not a total: a block that loses its rows to a formatting slip still
        // clears a combined floor on the strength of the other document.
        Map<String, Long> parsed = new java.util.TreeMap<>();
        for (ConsumerRoute route : consumerRoutes) {
            parsed.merge(route.consumer(), 1L, Long::sum);
        }
        assertThat(parsed)
                .as("the consumer-contract blocks did not parse to the number of routes the "
                        + "two documents inventory. A marker or a row format moved, and this "
                        + "sweep is looking at less than it thinks.")
                .containsExactly(
                        Map.entry("admin-web", 42L),
                        Map.entry("android", 24L));

        Set<String> mapped = mappedKeys();

        List<String> missing = consumerRoutes.stream()
                .filter(route -> !mapped.contains(route.key()))
                .map(route -> route.consumer() + " " + route.verb() + " " + route.path())
                .sorted()
                .toList();

        assertThat(missing.isEmpty())
                .as("these consumer repositories call routes this application does not map. "
                        + "Each one is a call that fails in a shipped client: " + missing)
                .isTrue();
    }

    @Test
    void theRoutesNoConsumerClaimsAreKnownAndCounted() throws IOException {
        Set<String> claimed = new TreeSet<>();
        for (ConsumerRoute route : consumerRoutes()) {
            claimed.add(route.key());
        }

        List<String> unclaimed = MappedRoutes.application(handlerMapping).stream()
                .map(this::keyOf)
                .filter(key -> !claimed.contains(key))
                .distinct()
                .sorted()
                .toList();

        assertThat(unclaimed.size())
                .as("routes no consumer document claims. Most are legitimate -- the /admin "
                        + "twins of paths the panel still calls unprefixed, the student "
                        + "endpoints the panel does not use, /health. A change here is either "
                        + "a new surface nobody documented or a consumer list gone stale: "
                        + unclaimed)
                .isEqualTo(UNCLAIMED_BY_EITHER_CONSUMER);
    }

    // ---------- the field check ----------

    @Test
    void everyFieldAConsumerReadsExistsInTheResponseTheRouteReturns() throws IOException {
        Map<String, Type> returnTypes = returnTypesByKey();

        List<String> broken = new ArrayList<>();

        for (ConsumerRoute route : consumerRoutes()) {
            String row = route.consumer() + " " + route.verb() + " " + route.path();
            Type response = returnTypes.get(route.key());
            if (response == null) {
                // Unmapped, which the other direction already reports as its own failure.
                continue;
            }

            for (String field : route.fields()) {
                if (firstUnresolvedSegment(response, field) != null) {
                    broken.add(row + " reads '" + field + "'");
                }
            }
        }

        assertThat(new TreeSet<>(broken))
                .as("a field a shipped client reads is not in the response it reads it from. "
                        + "Neither client fails loudly on one -- the panel casts list elements "
                        + "without checking them and Gson leaves an unmatched field null -- so "
                        + "this is the sweep that has to say it. Add the row to "
                        + "READ_BUT_NOT_SERVED with what it costs, or remove it from there if "
                        + "it now resolves.")
                .isEqualTo(new TreeSet<>(READ_BUT_NOT_SERVED.keySet()));
    }

    /**
     * The exemptions describe rows that exist.
     *
     * <p>An exemption list that names a row nobody writes any more is the hand-kept route
     * list's failure in miniature: it passes by covering less than it claims.
     */
    @Test
    void everyNamedExemptionStillMatchesARow() throws IOException {
        Set<String> rows = new TreeSet<>();
        for (ConsumerRoute route : consumerRoutes()) {
            rows.add(route.consumer() + " " + route.verb() + " " + route.path());
        }

        List<String> stale = READ_BUT_NOT_SERVED.keySet().stream()
                .map(entry -> entry.substring(0, entry.indexOf(" reads '")))
                .distinct()
                .filter(row -> !rows.contains(row))
                .sorted()
                .toList();

        assertThat(stale.isEmpty())
                .as("READ_BUT_NOT_SERVED names rows no consumer document has any more, so it "
                        + "is describing a client that has moved on: " + stale)
                .isTrue();
    }

    // ---------- resolving a field path against a response type ----------

    /**
     * Walks a dotted field path through record components.
     *
     * @param response the handler's return type
     * @param field    a path such as {@code auditLogs[].actor.name}
     * @return the first segment that does not exist, or {@code null} when the whole path
     *         resolves or reaches a point past which there is nothing to check
     */
    private static String firstUnresolvedSegment(Type response, String field) {
        Type current = response;

        for (String segment : field.split("\\.")) {
            // An open map's keys are data, not schema. There is nothing on the Java side to
            // match "*" against, and the client knows it is enumerating rather than reading.
            if (segment.equals("*")) {
                return null;
            }

            boolean element = segment.endsWith("]");
            String name = element ? segment.substring(0, segment.indexOf('[')) : segment;

            Class<?> raw = rawOf(current);
            if (raw == null || Map.class.isAssignableFrom(raw) || !raw.isRecord()) {
                return null;
            }

            RecordComponent component = componentNamed(raw, name);
            if (component == null) {
                return name;
            }

            current = component.getGenericType();
            if (element) {
                current = elementOf(current);
            }
        }

        return null;
    }

    private static RecordComponent componentNamed(Class<?> record, String name) {
        for (RecordComponent component : record.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        return null;
    }

    private static Class<?> rawOf(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return rawOf(parameterized.getRawType());
        }
        return null;
    }

    /** The element type of a {@code List<X>} or {@code Set<X>}, or the type itself. */
    private static Type elementOf(Type type) {
        Class<?> raw = rawOf(type);
        if (raw != null && Collection.class.isAssignableFrom(raw)
                && type instanceof ParameterizedType parameterized) {
            return parameterized.getActualTypeArguments()[0];
        }
        return type;
    }

    // ---------- reading the documents and the mapping ----------

    private Set<String> mappedKeys() {
        Set<String> keys = new TreeSet<>();
        for (MappedRoutes.Route route : MappedRoutes.application(handlerMapping)) {
            keys.add(keyOf(route));
        }
        return keys;
    }

    private Map<String, Type> returnTypesByKey() {
        Map<String, Type> types = new LinkedHashMap<>();
        for (MappedRoutes.Route route : MappedRoutes.application(handlerMapping)) {
            types.putIfAbsent(keyOf(route), route.handler().getMethod().getGenericReturnType());
        }
        return types;
    }

    /**
     * {@code "GET /users/{}"} -- placeholders collapsed, because a consumer spells them from
     * its own source ({@code {id}}) and this application from its handler
     * ({@code {lectureId}}, {@code {notification_id}}), and the name of a placeholder is not
     * part of the contract.
     */
    private String keyOf(MappedRoutes.Route route) {
        // A mapping with no verb answers all of them; none exists today, and one appearing
        // should be looked at rather than silently probed as a GET.
        String verb = route.verb() == null ? "ANY" : route.verb();
        return verb + " " + route.shape();
    }

    private static List<ConsumerRoute> consumerRoutes() throws IOException {
        List<ConsumerRoute> routes = new ArrayList<>();

        for (Map.Entry<String, Path> consumer : new TreeMapOf(CONSUMERS)) {
            boolean inside = false;

            for (String line : Files.readAllLines(consumer.getValue())) {
                if (line.contains("consumer-contract:start")) {
                    inside = true;
                    continue;
                }
                if (line.contains("consumer-contract:end")) {
                    break;
                }
                if (!inside || !line.startsWith("|") || line.contains("---")
                        || line.contains("| Verb |")) {
                    continue;
                }

                String[] cells = line.split("\\|");
                String verb = cells[1].trim();
                String path = cells[2].trim();
                String auth = cells[3].trim();
                String fields = cells[4].trim();

                List<String> read = fields.equals("-")
                        ? List.of()
                        : List.of(fields.split("\\s*,\\s*"));

                routes.add(new ConsumerRoute(consumer.getKey(), verb, path, auth, read));
            }
        }

        return routes;
    }

    /** One route a consumer repository calls, and the fields it reads out of the answer. */
    private record ConsumerRoute(
            String consumer, String verb, String path, String auth, List<String> fields) {

        /** Whether the client puts a session token on this call. */
        boolean authenticated() {
            return auth.equals("bearer");
        }

        /** The same key {@link #keyOf} builds, so the two sides compare on one shape. */
        String key() {
            return verb + " " + path.replaceAll("\\{[^}]+}", "{}");
        }
    }

    /** Iteration order that does not depend on {@code Map.of}'s hash order. */
    private record TreeMapOf(Map<String, Path> map)
            implements Iterable<Map.Entry<String, Path>> {

        @Override
        public java.util.Iterator<Map.Entry<String, Path>> iterator() {
            return new java.util.TreeMap<>(map).entrySet().iterator();
        }
    }
}

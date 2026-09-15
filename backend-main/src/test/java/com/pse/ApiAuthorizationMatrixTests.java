package com.pse;

import com.jayway.jsonpath.JsonPath;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.support.MappedRoutes;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.auth.repository.OneTimePasswordRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.repository.RatingRepository;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestDeliveryConfig.CapturingLoginCodeDelivery;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;

/**
 * The authorization matrix, asserted against the routes the application actually
 * publishes rather than against a list kept by hand.
 *
 * <p>Why it is shaped this way: the security chain ends in
 * {@code anyRequest().permitAll()}, so an endpoint is anonymous unless some matcher names
 * it. That makes "no rule matched" indistinguishable from "deliberately public" by
 * reading {@code SecurityConfig} alone, and a new controller method inherits the open
 * default silently. {@link #everyRouteIsEitherDeclaredPublicOrRefusesAnonymousCallers()}
 * closes that: it walks Spring's own handler mappings, so a route added later has to be
 * either refused for anonymous callers or written into {@link #PUBLIC_ROUTES} by someone
 * who thought about it.
 *
 * <p>{@link AdminApiPathSplitTests} covers the admin tier by role; this class covers the
 * anonymous boundary across every tier and the {@code authenticated()} tier, which had no
 * role coverage at all.
 */
@ApiIntegrationTest
class ApiAuthorizationMatrixTests {

    /**
     * Every route reachable without an Authorization header, as {@code "VERB /path"} using
     * the mapping's own pattern. Anything not listed here must answer 401 anonymously.
     *
     * <p>Deliberately absent, and worth saying why, because all three look public:
     * {@code POST /auth/validate} and {@code POST /reports} are matched by
     * {@code permitAll} but read the header by hand and answer 401 without one;
     * {@code POST /data/professor} was open by accident until the chain was given a rule
     * for it.
     */
    /**
     * The Android client's route inventory, extracted from its own call sites and held to
     * this application by {@code ConsumerContractSweepTests}.
     */
    private static final Path ANDROID_CONTRACT = Path.of("docs/frontend-consumer-contract.md");

    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "POST /auth/request-login",
            "POST /auth/login",
            "POST /admin/auth/request-login",
            "POST /admin/auth/login",
            "GET /health",
            "GET /data/lectures",
            "GET /data/lectures/{lecture_id}",
            "GET /data/professor",
            "GET /data/professor/{professor_id}",
            "GET /data/professor/id",
            "GET /ratings/{lectureId}",
            "GET /ratings/{lectureId}/categories",
            "GET /social/comments/{lecture_id}",
            "GET /social/sync/comments"
    );

    /**
     * Routes registered by a dependency that are deliberately reachable without a token.
     *
     * <p>One entry, and it is not really an endpoint: {@code /error} is where the servlet
     * container dispatches an error it is about to render, and Spring Boot's
     * {@code BasicErrorController} answers it. Requiring a token there would mean an
     * anonymous request that fails could not be told what went wrong -- the refusal itself
     * would fail to render. Called directly with no error in flight it answers 500, which is
     * what the sweep sees.
     */
    private static final Set<String> PUBLIC_THIRD_PARTY_ROUTES = Set.of("GET /error");

    private static final String STUDENT_EMAIL = "matrix-student@student.kit.edu";

    @Autowired DatabaseReset databaseReset;

    @Autowired MockMvc mockMvc;
    @Autowired CapturingLoginCodeDelivery codeDelivery;
    @Autowired RequestMappingHandlerMapping handlerMapping;

    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;
    @Autowired OneTimePasswordRepository otpRepository;
    @Autowired AuthRateLimitBucketRepository rateLimitRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired BugReportRepository bugReportRepository;
    @Autowired WarningRepository warningRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired CommentReportRepository commentReportRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AnswerRepository answerRepository;
    @Autowired AnswerReportRepository answerReportRepository;
    @Autowired AnswerVoteRepository answerVoteRepository;
    @Autowired RatingRepository ratingRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired ProfessorRepository professorRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseReset.all();
        codeDelivery.clear();
    }

    /**
     * The load-bearing test. Walks every route the application publishes and requires each
     * one to be either declared public or refused with a 401 for an anonymous caller.
     *
     * <p>A failure here is not necessarily a bug in the new route -- it is a question
     * nobody has answered yet: is this endpoint meant to be public? Answer it by adding a
     * matcher to {@code SecurityConfig} or a line to {@link #PUBLIC_ROUTES}.
     */
    @Test
    void everyRouteIsEitherDeclaredPublicOrRefusesAnonymousCallers() throws Exception {
        List<String> open = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();

        Set<String> routes = applicationRoutes();

        // A sweep that enumerated nothing would pass every assertion below it. 122 routes
        // are mapped today; the floor is here so this test cannot go quiet if the handler
        // mapping stops resolving.
        assertThat(routes.size() >= 100)
                .as("expected the whole API surface, found only " + routes.size() + " routes")
                .isTrue();

        for (String route : new TreeSet<>(routes)) {
            if (PUBLIC_ROUTES.contains(route)) {
                continue;
            }
            int status = anonymousStatusOf(route);
            if (status != 401) {
                (status == 403 ? unexpected : open).add(route + " -> " + status);
            }
        }

        // 403 is the authenticated-but-unauthorized answer; an anonymous caller should not
        // be able to provoke it, and if one can, the entry point is being bypassed.
        assertThat(unexpected.isEmpty())
                .as("anonymous callers were answered 403, not 401: " + unexpected)
                .isTrue();
        assertThat(open.isEmpty()).as("these routes answered an anonymous caller without refusing it. Either give "
                        + "SecurityConfig a matcher for them or add them to PUBLIC_ROUTES: " + open).isTrue();
    }

    /**
     * The same question asked of routes this project did not write.
     *
     * <p>{@link #applicationRoutes()} filters to {@code com.pse}, which is right for the sweep
     * above -- it is about endpoints somebody here declared. But the filter also meant a
     * dependency could register routes and no test would look at them, and the chain ends in
     * {@code anyRequest().permitAll()}, so whatever it registered would be anonymous. That is
     * not hypothetical: adding springdoc published {@code /v3/api-docs}, a complete
     * description of every route and request body including the administrative ones, to
     * anyone who asked. It is admin-tier now.
     *
     * <p>So third-party routes get the treatment first-party ones already had: every one must
     * either refuse an anonymous caller or be named in {@link #PUBLIC_THIRD_PARTY_ROUTES}.
     * A dependency that starts serving something new has to be classified by whoever adds it.
     */
    @Test
    void everyRouteADependencyRegisteredIsEitherDeclaredPublicOrRefusesAnonymousCallers()
            throws Exception {
        Set<String> open = new TreeSet<>();

        for (String route : thirdPartyRoutes()) {
            if (PUBLIC_THIRD_PARTY_ROUTES.contains(route)) {
                continue;
            }
            int status = anonymousStatusOf(route);
            if (status != 401 && status != 403) {
                open.add(route + " -> " + status);
            }
        }

        assertThat(open.isEmpty()).as("a dependency is serving these to anonymous callers. Give SecurityConfig a "
                        + "matcher for them, or add them to PUBLIC_THIRD_PARTY_ROUTES: " + open).isTrue();
    }

    /** The other direction: the routes declared public have to stay reachable. */
    @Test
    void declaredPublicRoutesAreReachableWithoutAToken() throws Exception {
        Set<String> routes = applicationRoutes();

        for (String route : new TreeSet<>(PUBLIC_ROUTES)) {
            assertThat(routes.contains(route)).as(route + " is declared public but is not a route any more -- remove it from "
                            + "PUBLIC_ROUTES, or fix the pattern").isTrue();

            int status = anonymousStatusOf(route);
            assertThat(status != 401 && status != 403)
                    .as(route + " is declared public but answered " + status + " without a token")
                    .isTrue();

            // No exemptions left. This list held two entries until F-14 and F-15 were
            // fixed -- GET /data/professor/id with no query parameters, and
            // GET /ratings/{lectureId}/categories for an unknown lecture -- both reachable
            // by an anonymous caller with no fixture at all. A public route answering 5xx
            // is now a plain failure.
            assertThat(status < 500).as(route + " is public but answered " + status).isTrue();
        }
    }

    /**
     * {@code POST /reports} is the student's bug submission and stays open in the chain, so
     * it reads the header itself. With no header at all it used to answer 500: the
     * {@code InvalidAuthTokenException} thrown by {@code AuthService.verifyUser} had no
     * handler and fell through to the catch-all. The documented contract is 401.
     */
    @Test
    void bugSubmissionWithoutAnAuthorizationHeaderIsUnauthorizedRatherThanAServerError()
            throws Exception {
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No token","description":"Filed with no header","severity":"LOW"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Not logged in"));

        assertThat(bugReportRepository.count()).isEqualTo(0);
    }

    /**
     * The legacy catalogue create used to answer a non-admin {@code 200 {"success":false}}
     * from inside the handler, which is a refusal the panel could not tell from a
     * validation failure. It is the security chain's now, like its {@code /admin} twin.
     */
    @Test
    void legacyProfessorCreateRefusesAStudentWithForbidden() throws Exception {
        String studentToken = studentSession();

        mockMvc.perform(post("/data/professor")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Stefan","lastName":"Kuehnlein","lectureIDs":[]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Could not authenticate Admin"));

        assertThat(professorRepository.count()).isEqualTo(0);
    }

    /**
     * The authenticated tier from the other side: a student session is let through.
     *
     * <p>It asserts {@code 200} rather than "was not refused" -- P-6. The older form,
     * {@code status != 401 && status != 403}, is also satisfied by a {@code 404}, so a route
     * deleted from this list's subject matter would keep the loop green.
     *
     * <p><b>The list used to be hand-written, and the javadoc here used to argue that it had
     * to be:</b> the question is which routes the app depends on, and Spring cannot answer
     * that. The first half is still true and the conclusion no longer is. The app's routes are
     * now inventoried in {@code docs/frontend-consumer-contract.md}, extracted from that
     * client's own call sites, and {@code ConsumerContractSweepTests} holds that document to
     * the application. Reading it here instead means a route the app starts calling arrives in
     * this loop rather than waiting for somebody to remember this file -- which is the same
     * argument the rest of the suite makes for reading the route list from Spring.
     *
     * <p>Restricted to what a bare session can reach: authenticated {@code GET}s with no path
     * variable. A placeholder would need that route's fixture, and this class has no
     * business inventing one -- the ownership and integration suites cover those. The three
     * session-ending calls are excluded by the same filter, since all three are writes; they
     * are named anyway, because "no writes" is a choice and not an accident.
     */
    @Test
    void studentSessionReachesTheEndpointsTheAppNeeds() throws Exception {
        String token = studentSession();

        List<String> routes = appRoutesAStudentSessionShouldReach();

        // Reads only. POST /auth/logout, POST /auth/logout-all and PATCH
        // /account/deleteAccount are app routes too, and each would end the session under the
        // rest of the loop.
        assertThat(routes)
                .as("the app's contract stopped naming any authenticated read this loop can "
                        + "reach without a fixture, which would leave it asserting nothing")
                .isNotEmpty();

        for (String route : routes) {
            assertThat(statusOf(route, token))
                    .as(route + " did not answer a student session with 200")
                    .isEqualTo(200);
        }
    }

    /**
     * The app's authenticated reads, from the Android client's own contract document.
     *
     * @return {@code "GET /account/information"} and the like, placeholder-free
     * @throws IOException if the contract document cannot be read
     */
    private static List<String> appRoutesAStudentSessionShouldReach() throws IOException {
        List<String> routes = new ArrayList<>();
        boolean inside = false;

        for (String line : Files.readAllLines(ANDROID_CONTRACT)) {
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

            if (verb.equals("GET") && auth.equals("bearer") && !path.contains("{")) {
                routes.add(verb + " " + path);
            }
        }

        return routes;
    }

    /**
     * There is no {@code @EnableMethodSecurity} in this application, so {@code @PreAuthorize}
     * and friends are inert: an endpoint carrying one would read as guarded and be open.
     * Authorization lives in {@code SecurityConfig}. This fails if one is added anyway.
     */
    @Test
    void noHandlerReliesOnInertMethodLevelSecurityAnnotations() {
        List<Class<? extends Annotation>> annotations =
                List.of(PreAuthorize.class, PostAuthorize.class, Secured.class);
        // A set, because one handler method reaches this loop once per pattern and verb it is
        // mapped at, and naming it four times would not make the failure any clearer.
        Set<String> offenders = new TreeSet<>();

        for (MappedRoutes.Route route : MappedRoutes.application(handlerMapping)) {
            HandlerMethod handler = route.handler();
            for (Class<? extends Annotation> annotation : annotations) {
                if (handler.getMethod().isAnnotationPresent(annotation)
                        || handler.getBeanType().isAnnotationPresent(annotation)) {
                    offenders.add(route.describe() + " carries @" + annotation.getSimpleName());
                }
            }
        }

        assertThat(offenders.isEmpty()).as("method security is not enabled, so these annotations do nothing. Move the rule "
                        + "into SecurityConfig, or enable method security deliberately: " + offenders).isTrue();
    }

    /** Every {@code "VERB /pattern"} this application publishes, from Spring's own mappings. */
    private Set<String> applicationRoutes() {
        return routesFrom(handler ->
                handler.getBeanType().getPackageName().startsWith("com.pse"));
    }

    /** Routes a dependency registered, not this project. See the test that classifies them. */
    private Set<String> thirdPartyRoutes() {
        return routesFrom(handler ->
                !handler.getBeanType().getPackageName().startsWith("com.pse"));
    }

    private Set<String> routesFrom(java.util.function.Predicate<HandlerMethod> include) {
        Set<String> routes = new TreeSet<>();

        for (MappedRoutes.Route route : MappedRoutes.of(handlerMapping)) {
            if (!include.test(route.handler())) {
                continue;
            }
            // A mapping that names no verb answers all of them, and MappedRoutes reports that
            // as a null verb rather than choosing. Probing GET is enough to decide whether it
            // is anonymous, which is all this sweep asks.
            routes.add(route.verb() == null ? "GET " + route.pattern() : route.verbAndPattern());
        }

        return routes;
    }

    private int anonymousStatusOf(String route) throws Exception {
        return statusOf(route, null);
    }

    private int statusOf(String route, String rawToken) throws Exception {
        String verb = route.substring(0, route.indexOf(' '));
        String path = route.substring(route.indexOf(' ') + 1)
                // Any id will do: authorization is decided before the handler looks one up.
                .replaceAll("\\{[^}]+}", UUID.randomUUID().toString());

        MockHttpServletRequestBuilder request = switch (verb) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("unhandled verb in " + route);
        };

        if (!"GET".equals(verb)) {
            request = request.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (rawToken != null) {
            request = request.header("Authorization", "Bearer " + rawToken);
        }

        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    /** A real app session through the real login endpoints, so the session type is right. */
    private String studentSession() throws Exception {
        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(STUDENT_EMAIL)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"loginToken\":\"%s\"}"
                                .formatted(STUDENT_EMAIL, codeDelivery.codeFor(STUDENT_EMAIL))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.authToken");
    }
}

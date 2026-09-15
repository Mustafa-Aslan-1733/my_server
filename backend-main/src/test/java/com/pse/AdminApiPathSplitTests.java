package com.pse;

import com.jayway.jsonpath.JsonPath;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditLog;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.auth.model.SessionType;
import com.pse.auth.model.Token;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.auth.repository.OneTimePasswordRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.repository.RatingRepository;
import com.pse.security.TokenHasher;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.support.ApiContract;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestDeliveryConfig.CapturingLoginCodeDelivery;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.json.JsonCompareMode;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;

/**
 * The admin panel and the app are two APIs. {@code /admin/auth/login} mints an
 * administrator session on the admin schedule and serves administrators only;
 * {@code /auth/login} mints an app session that lasts a year for everyone, an
 * administrator logging in from their phone included. Which endpoint was called decides
 * both, not which account called it -- deriving it from the account is what used to put an
 * administrator's app on the panel's expiry schedule.
 *
 * <p>Sessions here are always minted through the real login endpoints. The helper in
 * {@link AdminApiIntegrationTests} writes tokens with no session type at all, so tests
 * built on it exercise the legacy branch and would stay green with the admin session path
 * broken.
 */
@ApiIntegrationTest
class AdminApiPathSplitTests {

    /** Matches app.auth.admin-bootstrap-emails in application-test.properties. */
    private static final String ADMIN_EMAIL = "admin@student.kit.edu";
    private static final String STUDENT_EMAIL = "split-student@student.kit.edu";

    /** Every path the panel reaches through the new prefix, all of them plain listings. */
    private static final List<String> ADMIN_PATHS = List.of(
            "/admin/users",
            "/admin/audit-logs",
            "/admin/audit-logs/meta",
            "/admin/activity-logs",
            "/admin/activity-logs/meta",
            "/admin/system/status",
            "/admin/comments",
            "/admin/comments/reported",
            "/admin/answers",
            "/admin/answers/reported",
            "/admin/ratings",
            "/admin/reports",
            "/admin/data/lectures/all",
            "/admin/data/professor/all"
    );

    @Autowired DatabaseReset databaseReset;

    @Autowired MockMvc mockMvc;
    @Autowired CapturingLoginCodeDelivery codeDelivery;
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

    @Test
    void adminApiLoginIssuesAdminSessionWithTheAdminTtl() throws Exception {
        String response = loginThrough("/admin/auth", ADMIN_EMAIL, "");
        String rawToken = JsonPath.read(response, "$.authToken");

        Token token = tokenOf(rawToken);
        assertThat(token.getSessionType()).isEqualTo(SessionType.ADMIN);
        assertLifetimeIsAbout(token, Duration.ofDays(1));
        assertThat((Object) JsonPath.read(response, "$.expiresAt")).isNotNull();

        mockMvc.perform(get("/admin/auth/me").header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.kitEmail").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                // The panel renders this one, and nothing asserted it until the panel
                // started showing "Signed in as: Unknown User". That symptom is not this
                // endpoint -- /admins/validate, the legacy call the panel has not migrated
                // off, answers a bare BasicResponse with no identity in it at all -- but a
                // route whose whole job is to say who is signed in should have its name
                // field under test either way.
                .andExpect(jsonPath("$.user.username").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNotEmpty());
        mockMvc.perform(get("/admin/users").header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk());
        assertThat(countAudits(AuditAction.ADMIN_LOGIN)).isEqualTo(1);
    }

    @Test
    void adminApiLoginRefusesNonAdminAccount() throws Exception {
        // The account has to exist first. A refusal rolls the whole login back, the account
        // creation included, and an attempt by an address the server has never seen is
        // deliberately left unaudited.
        loginThrough("/auth", STUDENT_EMAIL, "");
        requestCode("/admin/auth/request-login", STUDENT_EMAIL);

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(STUDENT_EMAIL, "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin access required"))
                .andExpect(jsonPath("$.success").value(false));

        assertThat(tokenRepository.findByEmail(STUDENT_EMAIL).size())
                .as("no admin session was minted")
                .isEqualTo(1);
        AuditLog refusal = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.LOGIN_REFUSED)
                .findFirst()
                .orElseThrow();
        assertThat(refusal.getMetadata().get("reason")).isEqualTo("NOT_AN_ADMIN");
    }

    @Test
    void adminApiLoginIgnoresRequestedSessionType() throws Exception {
        String response = loginThrough("/admin/auth", ADMIN_EMAIL, ",\"sessionType\":\"APP\"");

        Token token = tokenOf(JsonPath.read(response, "$.authToken"));
        assertThat(token.getSessionType()).isEqualTo(SessionType.ADMIN);
        assertLifetimeIsAbout(token, Duration.ofDays(1));
    }

    @Test
    void adminApiRequestLoginDoesNotRevealAdminMembership() throws Exception {
        requestCode("/admin/auth/request-login", STUDENT_EMAIL);
        requestCode("/admin/auth/request-login", ADMIN_EMAIL);
    }

    @Test
    void appLoginKeepsYearLongSessionForAnAdminAccount() throws Exception {
        // The regression this split exists for: an administrator logging in from the app
        // used to inherit the admin lifetime, which is now one day.
        String response = loginThrough("/auth", ADMIN_EMAIL, "");

        Token token = tokenOf(JsonPath.read(response, "$.authToken"));
        assertLifetimeIsAbout(token, Duration.ofDays(365));
    }

    @Test
    void legacyAppLoginStillGivesThePanelAnAdminSession() throws Exception {
        // Until the panel moves to /admin/auth/login it logs in here and sends no session
        // type. It has to keep getting an admin session out of it.
        String rawToken = JsonPath.read(loginThrough("/auth", ADMIN_EMAIL, ""), "$.authToken");

        assertThat(tokenOf(rawToken).getSessionType()).isEqualTo(SessionType.ADMIN);
        mockMvc.perform(get("/users").header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk());
    }

    @Test
    void adminPrefixedPathsMatchLegacyAuthorization() throws Exception {
        String adminToken = JsonPath.read(loginThrough("/admin/auth", ADMIN_EMAIL, ""), "$.authToken");
        String studentToken = JsonPath.read(loginThrough("/auth", STUDENT_EMAIL, ""), "$.authToken");

        for (String path : ADMIN_PATHS) {
            assertThat(statusOf(path, adminToken))
                    .as(path + " with an admin session")
                    .isEqualTo(200);
            assertThat(statusOf(path, studentToken))
                    .as(path + " with a student session")
                    .isEqualTo(403);
            assertThat(statusOf(path, null)).as(path + " without a token").isEqualTo(401);
        }
    }

    /**
     * The same matrix for the writes, which the listing matrix above cannot reach.
     *
     * <p>It matters more here than for the reads. The {@code /admin} prefix is guarded by
     * one blanket rule, but every legacy twin is an individual per-verb matcher, and the
     * chain ends in {@code anyRequest().permitAll()} -- so a verb missing from one of those
     * dozen rules does not deny, it opens. {@code POST /data/professor} is the precedent:
     * it had no matcher at all and was answered inside the handler instead.
     *
     * <p>The admin expectation is "not refused" rather than 200 deliberately. These ids do
     * not exist, so a permitted call lands on a 400 or a 404; that it got that far is the
     * assertion.
     */
    @Test
    void adminWriteVerbsAreRefusedByRoleOnBothPrefixes() throws Exception {
        String adminToken = JsonPath.read(loginThrough("/admin/auth", ADMIN_EMAIL, ""), "$.authToken");
        String studentToken = JsonPath.read(loginThrough("/auth", STUDENT_EMAIL, ""), "$.authToken");

        for (Route route : writeRoutes()) {
            String label = route.method() + " " + route.path();

            assertThat(statusOf(route, null)).as(label + " without a token").isEqualTo(401);
            assertThat(statusOf(route, studentToken))
                    .as(label + " with a student session")
                    .isEqualTo(403);

            int admin = statusOf(route, adminToken);
            assertThat(admin != 401 && admin != 403)
                    .as(label + " refused an admin session with " + admin)
                    .isTrue();
        }
    }

    private record Route(String method, String path) {
    }

    /** Every administrative write, under the /admin prefix and under its legacy twin. */
    private List<Route> writeRoutes() {
        String id = UUID.randomUUID().toString();
        List<Route> routes = new ArrayList<>();

        for (String prefix : List.of("/admin", "")) {
            routes.add(new Route("PATCH", prefix + "/users/" + id));
            routes.add(new Route("DELETE", prefix + "/users/" + id));
            routes.add(new Route("PATCH", prefix + "/users/" + id + "/block"));
            routes.add(new Route("PATCH", prefix + "/users/" + id + "/unblock"));
            routes.add(new Route("POST", prefix + "/users/" + id + "/warnings"));
            routes.add(new Route("PATCH", prefix + "/users/" + id + "/warnings/" + id));
            routes.add(new Route("DELETE", prefix + "/users/" + id + "/warnings/" + id));
            routes.add(new Route("PATCH", prefix + "/comments/" + id));
            routes.add(new Route("DELETE", prefix + "/comments/" + id));
            routes.add(new Route("PATCH", prefix + "/comments/reported/" + id));
            routes.add(new Route("DELETE", prefix + "/comments/reported/" + id));
            routes.add(new Route("PATCH", prefix + "/answers/" + id));
            routes.add(new Route("DELETE", prefix + "/answers/" + id));
            routes.add(new Route("PATCH", prefix + "/answers/reported/" + id));
            routes.add(new Route("DELETE", prefix + "/answers/reported/" + id));
            routes.add(new Route("DELETE", prefix + "/ratings/" + id));
            routes.add(new Route("POST", prefix + "/data/lectures"));
            routes.add(new Route("PATCH", prefix + "/data/lectures/" + id));
            routes.add(new Route("DELETE", prefix + "/data/lectures/" + id));
            routes.add(new Route("POST", prefix + "/data/professor"));
            routes.add(new Route("PATCH", prefix + "/data/professor/" + id));
            routes.add(new Route("DELETE", prefix + "/data/professor/" + id));
            routes.add(new Route("PATCH", prefix + "/reports/" + id));
            routes.add(new Route("DELETE", prefix + "/reports/" + id));
            routes.add(new Route("POST", prefix + "/reports/" + id + "/gitlab-issue"));
            routes.add(new Route("POST", prefix + "/audit-logs/" + id + "/revert"));
        }

        return routes;
    }

    private int statusOf(Route route, String rawToken) throws Exception {
        MockHttpServletRequestBuilder request = switch (route.method()) {
            case "POST" -> post(route.path());
            case "PATCH" -> patch(route.path());
            case "DELETE" -> delete(route.path());
            default -> throw new IllegalArgumentException("unhandled verb " + route.method());
        };

        request = request.contentType(MediaType.APPLICATION_JSON).content("{}");
        if (rawToken != null) {
            request = request.header("Authorization", bearer(rawToken));
        }

        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void adminPrefixDoesNotCaptureAdminsValidate() throws Exception {
        // /admin/** matches whole segments, so the older /admins/** route is untouched by it.
        String rawToken = JsonPath.read(loginThrough("/admin/auth", ADMIN_EMAIL, ""), "$.authToken");

        mockMvc.perform(get("/admins/validate").header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Is Admin"));
    }

    /**
     * A shape-5 candidate that turned out to be **unreachable**, which is worth a test
     * precisely because reading the service alone says otherwise.
     *
     * <p>{@code AdminService.validateAdmin} ends in
     * {@code new BasicResponse("Is Not Admin", false)}, which reads as another instance of
     * F-5 — a failure reported with 200. It cannot be reached over HTTP:
     * {@code SecurityConfig} matches {@code /admins/**} with {@code hasRole("ADMIN")}, so a
     * caller without the role is refused **403** by the chain before the controller runs, and
     * a caller with an invalid token is refused 401 by
     * {@code BearerTokenAuthenticationFilter} before that.
     *
     * <p>Which makes it the same dead second answer {@code LectureService.addLecture} already
     * has a comment about: "a second, weaker answer to a question already settled". Left in
     * place rather than deleted — the route is being retired, and this test is what says the
     * 403 is the real contract.
     */
    /**
     * The legacy identity path the admin panel is still on.
     *
     * <p>{@code GET /auth/me} answers the same payload as {@code GET /admin/auth/me} and is
     * guarded the same way. It exists only because the panel calls it: the panel's audit
     * (docs/adminweb-consumer-findings.md, C-01) reports that a failure here is bare-caught
     * after login, turned into a null identity and raised as an error that fails the whole
     * sign-in -- so while this path was unmapped the panel could not sign anybody in at all,
     * and nothing on either side said so.
     *
     * <p>Asserted against the admin path rather than against a literal, so the two cannot
     * drift into answering different things. When the panel reports it has migrated, this
     * test and the mapping it covers are what get deleted.
     */
    @Test
    void theLegacyIdentityPathAnswersWhatTheAdminPathAnswers() throws Exception {
        String rawToken = JsonPath.read(
                loginThrough("/admin/auth", ADMIN_EMAIL, ""), "$.authToken");

        String onAdminPath = mockMvc.perform(
                        get("/admin/auth/me").header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/auth/me").header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk())
                .andExpect(content().json(onAdminPath, JsonCompareMode.STRICT));
    }

    /**
     * The legacy path carries the admin guard, not the surrounding {@code permitAll()}.
     *
     * <p>Worth its own test because the mapping lives on {@link com.pse.auth.controller.AuthController}
     * -- outside the {@code /admin/**} matcher that covers its twin -- and
     * {@code SecurityConfig} ends in {@code anyRequest().permitAll()}. A new route there is
     * anonymous unless something says otherwise, which would publish the signed-in
     * administrator's name, address and role to anyone who asked.
     */
    @Test
    void theLegacyIdentityPathRefusesAnAnonymousCallerAndAStudent() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        String studentToken = JsonPath.read(
                loginThrough("/auth", STUDENT_EMAIL, ""), "$.authToken");

        mockMvc.perform(get("/auth/me").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsValidateIsRefusedByTheChainBeforeItCanReportNotAnAdmin() throws Exception {
        String rawToken = JsonPath.read(
                loginThrough("/auth", STUDENT_EMAIL, ""), "$.authToken");

        mockMvc.perform(get("/admins/validate").header("Authorization", bearer(rawToken)))
                .andExpect(status().isForbidden());
    }

    /**
     * The student's bug submission is the one app route with no administrative twin.
     *
     * <p>The refusal it asserts is not the mechanism the test name suggests. Two different
     * things can refuse a request here:
     *
     * <ul>
     *   <li>{@code NoResourceFoundException}, meaning no such path, from
     *       {@code handleNotFound} -- 404; and</li>
     *   <li>{@code HttpRequestMethodNotSupportedException}, meaning the path exists but not
     *       for this verb, from {@code handleWrongMethod} -- 405, F-16 in
     *       docs/test-findings.md.</li>
     * </ul>
     *
     * <p>This route's refusal is the second one: {@code /admin/reports} very much exists, it
     * just only maps GET. Until F-16 was fixed both answered 404 with <b>byte-identical
     * bodies</b>, so the response alone could not tell them apart and this test read as
     * "there is no /admin/reports at all", which is false. The status now distinguishes
     * them; the GET assertion below is kept anyway, because it is what stops the test from
     * silently passing for the wrong reason if the path were ever removed outright.
     */
    @Test
    void studentBugSubmissionHasNoAdminTwin() throws Exception {
        String studentToken = JsonPath.read(loginThrough("/auth", STUDENT_EMAIL, ""), "$.authToken");
        String adminToken = JsonPath.read(loginThrough("/admin/auth", ADMIN_EMAIL, ""), "$.authToken");
        String report = """
                {"title":"Broken filter","description":"The listing does not filter","severity":"LOW"}
                """;

        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/reports")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report))
                .andExpect(status().is(ApiContract.WRONG_VERB_STATUS));

        // The path exists -- so the refusal above is about the verb, not the path. Without
        // this the test would read as "there is no /admin/reports at all", which is false.
        mockMvc.perform(get("/admin/reports").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bugReports").isArray());

        assertThat(bugReportRepository.count()).isEqualTo(1);
    }

    @Test
    void adminApiLogoutRevokesAndWritesAdminLogout() throws Exception {
        String rawToken = JsonPath.read(loginThrough("/admin/auth", ADMIN_EMAIL, ""), "$.authToken");

        mockMvc.perform(post("/admin/auth/logout").header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/users").header("Authorization", bearer(rawToken)))
                .andExpect(status().isUnauthorized());
        assertThat(countAudits(AuditAction.ADMIN_LOGOUT)).isEqualTo(1);
    }

    @Test
    void loginRateLimitBucketsAreSharedBetweenBothApis() throws Exception {
        // One address, one mailbox, one budget. Two buckets would hand out six codes.
        requestCode("/auth/request-login", ADMIN_EMAIL);
        requestCode("/auth/request-login", ADMIN_EMAIL);
        requestCode("/auth/request-login", ADMIN_EMAIL);

        mockMvc.perform(post("/admin/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(ADMIN_EMAIL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private int statusOf(String path, String rawToken) throws Exception {
        var request = get(path);
        if (rawToken != null) {
            request = request.header("Authorization", bearer(rawToken));
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private String loginThrough(String api, String email, String extraFields) throws Exception {
        requestCode(api + "/request-login", email);
        return mockMvc.perform(post(api + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, extraFields)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
    }

    private void requestCode(String path, String email) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login code sent"));
    }

    private String loginBody(String email, String extraFields) {
        return "{\"email\":\"%s\",\"loginToken\":\"%s\"%s}"
                .formatted(email, codeDelivery.codeFor(email), extraFields);
    }

    private Token tokenOf(String rawToken) {
        return tokenRepository
                .findByHashAndRevokedFalseAndExpiresAtAfter(
                        TokenHasher.hash(rawToken),
                        LocalDateTime.now(Clock.systemUTC())
                )
                .orElseThrow();
    }

    private void assertLifetimeIsAbout(Token token, Duration expected) {
        long remaining = Duration
                .between(LocalDateTime.now(Clock.systemUTC()), token.getExpiresAt())
                .toSeconds();
        assertThat(remaining <= expected.toSeconds() && remaining >= expected.minusSeconds(30).toSeconds()).as("expected about " + expected + " of lifetime, " + remaining + " seconds remain").isTrue();
    }

    private long countAudits(AuditAction action) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == action)
                .count();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

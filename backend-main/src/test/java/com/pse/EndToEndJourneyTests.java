package com.pse;

import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.shared.enums.SemesterSeason;
import com.pse.support.DatabaseReset;
import com.pse.support.E2EClient;
import com.pse.support.PostgresE2ETest;
import com.pse.support.TestDeliveryConfig.CapturingLoginCodeDelivery;
import com.pse.support.TestGitLabConfig.RecordingGitLabClient;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Six journeys, driven over real HTTP against the database the application actually runs on.
 *
 * <p>The layer above this one — {@code *ApiIntegrationTests} — calls handlers through MockMvc,
 * which is Spring's dispatcher without a server. That is the right tool for checking a response
 * field by field, and it is where that checking stays. What it cannot say is whether the pieces
 * work <em>together</em>: whether a session minted by one endpoint is accepted by another,
 * whether a student's comment comes back on the lecture page they wrote it on, whether a revert
 * really restores the row. Each test below is one such story, start to finish, and asserts the
 * outcome rather than the shape of every response on the way.
 *
 * <p>Everything a journey needs, it does through the API. The two seams are creating a lecture
 * to have something to rate, and promoting an account to administrator — neither has a
 * self-service endpoint, and inventing one for a test would be inventing product.
 *
 * <p>See {@link PostgresE2ETest} for why this is its own Spring context and its own CI
 * invocation, and {@link E2EClient} for why the HTTP client is {@code RestClient}.
 */
@PostgresE2ETest
class EndToEndJourneyTests {

    @LocalServerPort int port;

    @Autowired DatabaseReset databaseReset;
    @Autowired CapturingLoginCodeDelivery codeDelivery;
    @Autowired RecordingGitLabClient gitLab;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired LectureRepository lectureRepository;

    private E2EClient anonymous;

    @BeforeEach
    void startFromAnEmptyServer() {
        databaseReset.all();
        codeDelivery.clear();
        gitLab.reset();
        anonymous = E2EClient.at(port);
    }

    // ------------------------------------------------------------------------- 1. signing up

    /**
     * There is no separate registration: redeeming a login code for an unknown KIT address is
     * what creates the account. Note <em>redeeming</em>, not requesting — asking for a code
     * creates nothing, which is what stops anyone minting accounts for addresses they do not
     * own. The journey is worth pinning end to end because three mechanisms have to agree: the
     * code that was mailed, the session that comes back, and the logout that must actually stop
     * it working.
     */
    @Test
    void aNewStudentCanSignUpSignInAndSignOut() {
        String email = "journey-signup@student.kit.edu";

        assertThat(anonymous.post("/auth/request-login", email(email)).status()).isEqualTo(200);
        assertThat(studentRepository.findByKitEmail(email))
                .as("requesting a code must not create an account for an address nobody proved")
                .isEmpty();

        E2EClient.Response login = anonymous.post("/auth/login",
                """
                {"email":"%s","loginToken":"%s"}""".formatted(email, codeFor(email)));
        assertThat(login.status()).isEqualTo(200);
        assertThat(studentRepository.findByKitEmail(email))
                .as("redeeming the code is what creates it")
                .isPresent();

        E2EClient student = anonymous.withToken(login.text("authToken"));
        assertThat(student.get("/account/information").status()).isEqualTo(200);

        assertThat(student.post("/auth/logout").status()).isEqualTo(200);
        assertThat(student.get("/account/information").status())
                .as("the token has to stop working, not merely be forgotten by the client")
                .isEqualTo(401);
    }

    // ------------------------------------------------------------------- 2. the student's day

    /**
     * The path an app user actually walks: find a lecture, rate it, ask something, answer, vote.
     * Each step is checked by reading it back through the endpoint the app reads it through,
     * because "the write returned 200" and "the reader sees it" are different claims.
     */
    @Test
    void aStudentCanRateALectureCommentAnswerAndVote() {
        UUID lectureId = seedLecture("Algorithmen 1", "IN0001");
        E2EClient student = signIn("journey-student@student.kit.edu");

        assertThat(student.get("/data/lectures").body().get("lectures")).hasSize(1);

        assertThat(student.post("/ratings/rate", """
                {"lectureId":"%s","topics":[{"category":"OVERALL","value":4.0}]}"""
                .formatted(lectureId)).status()).isEqualTo(200);
        assertThat(student.get("/ratings/own/" + lectureId).body().get("ratings"))
                .as("a rating the student just submitted must come back on their own view")
                .isNotEmpty();

        assertThat(student.post("/social/comments", """
                {"lectureID":"%s","content":"Is the exam open book?"}"""
                .formatted(lectureId)).status()).isEqualTo(200);

        String commentId = student.get("/social/comments/" + lectureId)
                .text("/comments/0/commentID");
        assertThat(commentId).isNotNull();

        assertThat(student.post("/social/answers", """
                {"commentID":"%s","content":"Yes, one A4 sheet."}""".formatted(commentId))
                .status()).isEqualTo(200);
        assertThat(student.post("/social/comments/vote/comment/" + commentId, """
                {"voteType":"UP"}""").status()).isEqualTo(200);

        E2EClient.Response thread = student.get("/social/comments/" + lectureId);
        assertThat(thread.body().at("/comments/0/answers")).hasSize(1);
        assertThat(thread.body().at("/comments/0/upVotes").asInt()).isEqualTo(1);
        assertThat(thread.text("/comments/0/userVote")).isEqualTo("UP");
    }

    // ---------------------------------------------------------------- 3. the administrator's

    /**
     * Create, edit, and undo the edit — with the record agreeing at every step.
     *
     * <p>The creation entry is the one this repository only just started writing, and it is
     * deliberately not revertible: undoing a creation is a deletion, which is its own action.
     * The reversal of the edit, by contrast, is its own audit entry rather than a rewrite of the
     * one it reverses.
     */
    @Test
    void anAdministratorCanEditTheCatalogueAndUndoTheEdit() {
        E2EClient admin = signInAsAdmin("journey-admin@student.kit.edu");

        assertThat(admin.post("/admin/data/lectures", """
                {"name":"Analysis 1","code":"ANA1","semesterYear":2026,
                 "semesterSeason":"WS","active":true,"professorIds":[]}""")
                .status()).isEqualTo(200);
        UUID lectureId = lectureRepository.findByName("Analysis 1").orElseThrow().getId();

        assertThat(admin.patch("/admin/data/lectures/" + lectureId, """
                {"name":"Analysis 1 (Bachelor)"}""").status()).isEqualTo(200);

        // Newest first, and the administrator's own sign-in is an administrative event too:
        // LECTURE_UPDATED, LECTURE_CREATED, ADMIN_LOGIN.
        E2EClient.Response log = admin.get("/admin/audit-logs");
        assertThat(log.body().get("auditLogs")).hasSize(3);
        assertThat(log.text("/auditLogs/0/action")).isEqualTo("LECTURE_UPDATED");
        assertThat(log.text("/auditLogs/1/action")).isEqualTo("LECTURE_CREATED");
        assertThat(log.text("/auditLogs/2/action")).isEqualTo("ADMIN_LOGIN");
        assertThat(log.body().at("/auditLogs/1/revertible").asBoolean())
                .as("a creation is undone by deleting, not by reverting a field diff")
                .isFalse();

        String updateId = log.text("/auditLogs/0/id");
        assertThat(admin.post("/admin/audit-logs/" + updateId + "/revert").status()).isEqualTo(200);

        assertThat(lectureRepository.findById(lectureId).orElseThrow().getName())
                .as("the revert has to restore the row, not just record that it tried")
                .isEqualTo("Analysis 1");
        assertThat(admin.get("/admin/audit-logs").body().get("auditLogs"))
                .as("the reversal is its own event; the entry it reversed is never rewritten")
                .hasSize(4);
    }

    // ------------------------------------------------------------------- 4. being turned away

    /**
     * Two different refusals, and the difference matters: an anonymous caller has not said who
     * they are, a signed-in student has and is still not allowed. Only the second is worth
     * recording, and {@code AccessRefusalAuditor} records exactly that one.
     */
    @Test
    void aStudentReachingForAnAdminRouteIsRefusedAndRecorded() {
        E2EClient admin = signInAsAdmin("journey-guard-admin@student.kit.edu");
        E2EClient student = signIn("journey-guard-student@student.kit.edu");

        assertThat(student.get("/admin/users").status()).isEqualTo(403);
        assertThat(anonymous.get("/admin/users").status()).isEqualTo(401);

        E2EClient.Response activity = admin.get("/admin/activity-logs");
        assertThat(activity.status()).isEqualTo(200);
        assertThat(activity.text("/activityLogs/0/action")).isEqualTo("ACCESS_REFUSED");
        assertThat(activity.text("/activityLogs/0/target/type")).isEqualTo("ENDPOINT");
    }

    // -------------------------------------------------------- 5. which login decides the clock

    /**
     * The property a two-minute deployment experiment was once run to demonstrate: the session
     * lifetime follows the endpoint, not the account. The same administrator signing in through
     * the app API gets a year, and through the admin API a day.
     *
     * <p>Held here rather than in a setting somebody has to remember to change back.
     */
    @Test
    void theSessionLifetimeFollowsTheEndpointAndNotTheAccount() {
        String email = "journey-both@student.kit.edu";
        signInAsAdmin(email);

        // Read off the login response, which is the field the panel reads, rather than out of
        // the token table -- the client only ever sees this one.
        Instant appExpiry = expiryOf(signInResponse("/auth/login", email));
        Instant adminExpiry = expiryOf(signInResponse("/admin/auth/login", email));

        assertThat(Duration.between(Instant.now(), appExpiry))
                .as("the app session is a year, for an administrator too")
                .isGreaterThan(Duration.ofDays(300));
        assertThat(Duration.between(Instant.now(), adminExpiry))
                .as("the admin session is a day, for the same account at the same moment")
                .isLessThan(Duration.ofDays(2));
    }

    // ------------------------------------------------------------------ 6. reporting a defect

    /**
     * A bug report is the one place this backend talks to another system. The tracker is
     * substituted, but everything on this side of it is real, and the assertion that matters is
     * that one report produces exactly one issue.
     */
    @Test
    void aBugReportReachesTheAdminQueueAndThenTheTracker() {
        E2EClient admin = signInAsAdmin("journey-report-admin@student.kit.edu");
        E2EClient student = signIn("journey-reporter@student.kit.edu");
        gitLab.enable();

        assertThat(student.post("/reports", """
                {"title":"Cannot open a lecture","description":"It closes at once.",
                 "severity":"HIGH"}""").status()).isEqualTo(200);

        E2EClient.Response queue = admin.get("/admin/reports");
        assertThat(queue.status()).isEqualTo(200);
        String reportId = queue.text("/bugReports/0/id");
        assertThat(reportId).isNotNull();

        assertThat(admin.post("/admin/reports/" + reportId + "/gitlab-issue").status())
                .isEqualTo(200);
        assertThat(gitLab.calls())
                .as("one report, one issue")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------------------- fixtures

    private E2EClient signIn(String email) {
        return anonymous.withToken(login("/auth/login", email));
    }

    /**
     * Signs the account up through the API, then promotes it. There is no endpoint that makes an
     * administrator — deliberately, since that is how administrators would be made by anyone —
     * so the promotion is written directly and everything after it goes back through HTTP.
     */
    private E2EClient signInAsAdmin(String email) {
        anonymous.post("/auth/request-login", email(email));
        anonymous.post("/auth/login",
                """
                {"email":"%s","loginToken":"%s"}""".formatted(email, codeFor(email)));

        Student student = studentRepository.findByKitEmail(email).orElseThrow();
        Admin admin = new Admin();
        admin.setStudent(student);
        adminRepository.saveAndFlush(admin);

        return anonymous.withToken(login("/admin/auth/login", email));
    }

    /** Drives the two-step login and returns the bearer token. */
    private String login(String loginPath, String email) {
        return signInResponse(loginPath, email).text("authToken");
    }

    /** The whole login response, for the journey that reads {@code expiresAt} off it. */
    private E2EClient.Response signInResponse(String loginPath, String email) {
        String requestPath = loginPath.startsWith("/admin")
                ? "/admin/auth/request-login"
                : "/auth/request-login";
        assertThat(anonymous.post(requestPath, email(email)).status()).isEqualTo(200);

        E2EClient.Response response = anonymous.post(loginPath,
                """
                {"email":"%s","loginToken":"%s"}""".formatted(email, codeFor(email)));
        assertThat(response.status()).as("login at %s", loginPath).isEqualTo(200);
        return response;
    }

    private static Instant expiryOf(E2EClient.Response login) {
        String expiresAt = login.text("expiresAt");
        assertThat(expiresAt).as("the login response has to say when the session ends").isNotNull();
        return OffsetDateTime.parse(expiresAt).toInstant();
    }

    private String codeFor(String email) {
        String code = codeDelivery.codeFor(email);
        assertThat(code).as("no login code was delivered for %s", email).isNotNull();
        return code;
    }

    private static String email(String address) {
        return """
                {"email":"%s"}""".formatted(address);
    }

    private UUID seedLecture(String name, String code) {
        Lecture lecture = new Lecture();
        lecture.setName(name);
        lecture.setCode(code);
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        return lectureRepository.saveAndFlush(lecture).getId();
    }
}

package com.pse;

import com.jayway.jsonpath.JsonPath;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActionScope;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.audit.service.AuditWriter;
import com.pse.auth.model.OneTimePassword;
import com.pse.auth.model.SessionType;
import com.pse.auth.model.Token;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.auth.repository.OneTimePasswordRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.auth.service.TokenGenerator;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.model.Admin;
import com.pse.moderation.model.BugReport;
import com.pse.moderation.model.Warning;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.repository.RatingRepository;
import com.pse.shared.enums.BugSeverity;
import com.pse.shared.enums.ReportReason;
import com.pse.shared.enums.ReportStatus;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.UserStatus;
import com.pse.security.TokenHasher;
import com.pse.shared.enums.ContentStatus;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentReport;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestDeliveryConfig.CapturingLoginCodeDelivery;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.model.RatingTopic;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerReport;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;

import org.springframework.test.context.TestPropertySource;

@ApiIntegrationTest
@TestPropertySource(properties = {
        "ipinfo.token=",
        "geoapify.api.key="
})
class AdminApiIntegrationTests {

    /** Matches app.auth.admin-superuser-emails in application-test.properties. It is
     * deliberately not the bootstrap admin, so the plain-administrator refusals stay
     * under test alongside the elevated ones. */
    private static final String SUPERUSER_EMAIL = "super@student.kit.edu";

    @Autowired DatabaseReset databaseReset;

    @Autowired MockMvc mockMvc;
    @Autowired CapturingLoginCodeDelivery codeDelivery;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;
    @Autowired OneTimePasswordRepository otpRepository;
    @Autowired AuthRateLimitBucketRepository rateLimitRepository;
    @Autowired WarningRepository warningRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired CommentReportRepository commentReportRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired BugReportRepository bugReportRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AnswerRepository answerRepository;
    @Autowired AnswerReportRepository answerReportRepository;
    @Autowired AnswerVoteRepository answerVoteRepository;
    @Autowired RatingRepository ratingRepository;
    @MockitoSpyBean AuditWriter auditWriter;
    @Autowired com.pse.support.TestGitLabConfig.RecordingGitLabClient gitLabClient;

    @BeforeEach
    void cleanDatabase() {
        databaseReset.all();
        codeDelivery.clear();
        gitLabClient.reset();
        reset(auditWriter);
    }

    @AfterEach
    void resetSpy() {
        reset(auditWriter);
    }

    @Test
    void adminLoginMeLogoutAndRevocationFollowContract() throws Exception {
        requestCode("  ADMIN@student.kit.edu ");
        String code = codeDelivery.codeFor("admin@student.kit.edu");

        String loginBody = """
            {"email":"ADMIN@student.kit.edu","loginToken":"%s"}
            """.formatted(code);

        String token = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.message").value("Login successful"))
                        .andExpect(jsonPath("$.success").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.authToken"
        );

        Token issuedToken = tokenRepository
                .findByHashAndRevokedFalseAndExpiresAtAfter(
                        TokenHasher.hash(token),
                        LocalDateTime.now(java.time.Clock.systemUTC())
                )
                .orElseThrow();

        assertThat(issuedToken.getSessionType()).isEqualTo(SessionType.ADMIN);

        long remainingSeconds = java.time.Duration.between(
                LocalDateTime.now(java.time.Clock.systemUTC()),
                issuedToken.getExpiresAt()
        ).toSeconds();

        assertThat(remainingSeconds >= java.time.Duration.ofDays(365).minusSeconds(5).toSeconds()
                        && remainingSeconds <= java.time.Duration.ofDays(365).toSeconds()).isTrue();

        // The issued token is an active admin session.
        mockMvc.perform(get("/users")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // The same token can no longer access a protected endpoint.
        mockMvc.perform(get("/users")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.ADMIN_LOGOUT)
                        .count()).isEqualTo(1);

        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.ADMIN_LOGIN)
                        .count()).isEqualTo(1);
    }


    @Test
    void appLoginGivesAdminAccountYearLongStudentOnlySession() throws Exception {
        requestCode("admin@student.kit.edu");

        String rawToken = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email":"admin@student.kit.edu",
                                      "loginToken":"%s",
                                      "sessionType":"APP"
                                    }
                                    """.formatted(codeDelivery.codeFor("admin@student.kit.edu"))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.authToken"
        );

        Token issuedToken = tokenRepository
                .findByHashAndRevokedFalseAndExpiresAtAfter(
                        TokenHasher.hash(rawToken),
                        LocalDateTime.now(java.time.Clock.systemUTC())
                )
                .orElseThrow();

        assertThat(issuedToken.getSessionType()).isEqualTo(SessionType.APP);

        long remainingSeconds = java.time.Duration.between(
                LocalDateTime.now(java.time.Clock.systemUTC()),
                issuedToken.getExpiresAt()
        ).toSeconds();

        assertThat(remainingSeconds >= java.time.Duration.ofDays(365).minusSeconds(5).toSeconds()
                        && remainingSeconds <= java.time.Duration.ofDays(365).toSeconds()).isTrue();

        // An APP session is a valid student session.
        mockMvc.perform(get("/social/notifications")
                        .header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk());

        // Even though the account is also an admin, APP sessions must not gain admin access.
        mockMvc.perform(get("/users")
                        .header("Authorization", bearer(rawToken)))
                .andExpect(status().isForbidden());

        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.USER_LOGIN)
                        .count()).isEqualTo(1);

        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.ADMIN_LOGIN)
                        .count()).isEqualTo(0);
    }


    @Test
    void appLoginGivesStudentAccountYearLongSession() throws Exception {
        requestCode("mobile-student@student.kit.edu");
        String rawToken = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"mobile-student@student.kit.edu","loginToken":"%s","sessionType":"APP"}
                                        """.formatted(codeDelivery.codeFor("mobile-student@student.kit.edu"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.authToken"
        );

        Token issuedToken = tokenRepository
                .findByHashAndRevokedFalseAndExpiresAtAfter(
                        TokenHasher.hash(rawToken),
                        LocalDateTime.now(java.time.Clock.systemUTC())
                )
                .orElseThrow();
        assertThat(issuedToken.getSessionType()).isEqualTo(SessionType.APP);
        long remainingSeconds = java.time.Duration.between(
                LocalDateTime.now(java.time.Clock.systemUTC()),
                issuedToken.getExpiresAt()
        ).toSeconds();
        assertThat(remainingSeconds >= java.time.Duration.ofDays(365).minusSeconds(5).toSeconds()
                        && remainingSeconds <= java.time.Duration.ofDays(365).toSeconds()).isTrue();
    }

    @Test
    void legacyAdminTokenWithoutSessionTypeKeepsAdminAccessUntilExpiry() throws Exception {
        Session legacyAdmin = createSession("legacy-admin@student.kit.edu", true);

        assertThat(legacyAdmin.token().getSessionType()).isNull();
        mockMvc.perform(get("/users").header("Authorization", bearer(legacyAdmin.rawToken())))
                .andExpect(status().isOk());
    }

    @Test
    void invalidUsedAndRateLimitedLoginCodesAreRejected() throws Exception {
        requestCode("student@student.kit.edu");
        String code = codeDelivery.codeFor("student@student.kit.edu");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student@student.kit.edu","loginToken":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student@student.kit.edu","loginToken":"%s"}
                                """.formatted(code)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student@student.kit.edu","loginToken":"%s"}
                                """.formatted(code)))
                .andExpect(status().isUnauthorized());

        requestCode("expired@student.kit.edu");
        String expiredCode = codeDelivery.codeFor("expired@student.kit.edu");
        OneTimePassword expired = otpRepository.findAll().stream()
                .filter(otp -> otp.getEmail().equals("expired@student.kit.edu"))
                .findFirst()
                .orElseThrow();
        expired.setExpiresAt(LocalDateTime.now(java.time.Clock.systemUTC()).minusMinutes(1));
        otpRepository.saveAndFlush(expired);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"expired@student.kit.edu","loginToken":"%s"}
                                """.formatted(expiredCode)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\"}"))
                .andExpect(status().isBadRequest());

        requestCode("limited@student.kit.edu");
        requestCode("limited@student.kit.edu");
        requestCode("limited@student.kit.edu");
        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"limited@student.kit.edu\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void protectedEndpointsDifferentiateAnonymousStudentAndAdmin() throws Exception {
        // Anonymous users are not authenticated.
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());

        // Students are authenticated, but do not have admin permissions.
        Session student = createSession("student@student.kit.edu", false);

        mockMvc.perform(get("/users")
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());

        // Normal admin sessions may access the admin API.
        Session admin = createSession("admin@student.kit.edu", true);

        mockMvc.perform(get("/users")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        // Superadmins are admins as well, but elevation is expressed through
        // additional permissions rather than through a separate public role field.
        Session elevated = createSession(SUPERUSER_EMAIL, true);
        Session otherAdmin = createSession("other-admin@student.kit.edu", true);

        mockMvc.perform(get("/users")
                        .header("Authorization", bearer(elevated.rawToken())))
                .andExpect(status().isOk());

        // A normal admin may not moderate another admin.
        mockMvc.perform(patch("/users/{id}/block", otherAdmin.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isConflict());

        // An elevated admin may moderate another admin.
        mockMvc.perform(patch("/users/{id}/block", otherAdmin.student().getId())
                        .header("Authorization", bearer(elevated.rawToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/missing-api-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }



    @Test
    void usersResponsesWarningsAndLifecycleUseServerSideActor() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("target@student.kit.edu", false);

        mockMvc.perform(get("/users")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(2)))
                .andExpect(jsonPath("$.users[?(@.kitEmail == 'admin@student.kit.edu')].role")
                        .value(hasItem("ADMIN")))
                .andExpect(jsonPath("$.users[?(@.kitEmail == 'target@student.kit.edu')].biography")
                        .value(hasItem("")))
                .andExpect(jsonPath("$.users[0].warnings").isNumber())
                .andExpect(jsonPath("$.users[0].reports").isNumber());

        mockMvc.perform(get("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.student().getId().toString()))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.joined", endsWith("Z")))
                .andExpect(jsonPath("$.lastOnline", endsWith("Z")));

        mockMvc.perform(get("/users/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userID":"00000000-0000-0000-0000-000000000001",
                                  "message":"  Repeated harassment  ",
                                  "createdFrom":{"id":"00000000-0000-0000-0000-000000000002"}
                                }
                                """))
                .andExpect(status().isOk());

        Warning saved = warningRepository.findAll().getFirst();
        assertThat(saved.getAdmin().getId()).isEqualTo(admin.admin().getId());

        mockMvc.perform(get("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0].createdFrom.id")
                        .value(admin.student().getId().toString()))
                .andExpect(jsonPath("$.warnings[0].createdAt", endsWith("Z")));

        mockMvc.perform(patch("/users/{id}/block", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/account/information")
                        .header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isUnauthorized());
        long auditCount = auditLogRepository.count();
        mockMvc.perform(patch("/users/{id}/block", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        assertThat(auditLogRepository.count()).isEqualTo(auditCount);

        mockMvc.perform(patch("/users/{id}/unblock", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        // Inverted, not deleted: this pinned the 404 a deleted account used to read as. It
        // exists -- anonymised -- so it reads now, and 404 on this route is left meaning only
        // "no such account". Pinned in full, both halves, by
        // aDeletedAccountIsReadableWithItsWarningsAndAnUnknownOneIsStillNotFound.
        mockMvc.perform(get("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));
    }

    @Test
    void lastSeenFollowsApiActivityAndIsNotRewrittenOnEveryRequest() throws Exception {
        Session target = createSession("target@student.kit.edu", false);

        assertThat(target.student().getLastSeenAt())
                .as("seeded student has never been seen")
                .isNull();

        // Any successful authenticated API request counts as activity.
        mockMvc.perform(get("/social/notifications")
                        .header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isOk());

        LocalDateTime firstSeen = reloadStudent(target).getLastSeenAt();

        assertThat(firstSeen).as("the first authenticated request records last seen").isNotNull();

        // Inside the refresh window the value is left alone, so a busy client does not
        // turn every one of its calls into a row update.
        mockMvc.perform(get("/social/notifications")
                        .header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isOk());

        assertThat(reloadStudent(target).getLastSeenAt())
                .as("a second request inside the refresh window does not rewrite last seen")
                .isEqualTo(firstSeen);

        // Once the stored value falls outside the window, the next request moves it on.
        LocalDateTime stale = firstSeen.minusHours(1);

        Student student = reloadStudent(target);
        student.setLastSeenAt(stale);
        studentRepository.saveAndFlush(student);

        mockMvc.perform(get("/social/notifications")
                        .header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isOk());

        assertThat(reloadStudent(target).getLastSeenAt().isAfter(stale))
                .as("a request outside the refresh window advances last seen")
                .isTrue();

        // A refused request is not activity.
        student = reloadStudent(target);
        student.setLastSeenAt(stale);
        student.setStatus(UserStatus.BLOCKED);
        studentRepository.saveAndFlush(student);

        mockMvc.perform(get("/social/notifications")
                        .header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isUnauthorized());

        assertThat(reloadStudent(target).getLastSeenAt())
                .as("a refused request does not register as activity")
                .isEqualTo(stale);
    }


    /**
     * The plain-administrator case: elevation is what lifts these refusals, and this
     * account is not configured as an elevated operator, so every one of them stands.
     */
    @Test
    void adminAccountsAndEmptyWarningsAreProtected() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session otherAdmin = createSession("other-admin@student.kit.edu", true);

        mockMvc.perform(delete("/users/{id}", otherAdmin.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}/block", otherAdmin.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}/unblock", otherAdmin.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/users/{id}/warnings", otherAdmin.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Not allowed\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/users/{id}/warnings", otherAdmin.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elevatedAdminModeratesOtherAdminAccounts() throws Exception {
        Session elevated = createSession(SUPERUSER_EMAIL, true);
        Session otherAdmin = createSession("other-admin@student.kit.edu", true);
        UUID targetId = otherAdmin.student().getId();

        mockMvc.perform(patch("/users/{id}/block", targetId)
                        .header("Authorization", bearer(elevated.rawToken())))
                .andExpect(status().isOk());
        assertThat(reloadStudent(otherAdmin).getStatus()).isEqualTo(UserStatus.BLOCKED);

        mockMvc.perform(patch("/users/{id}/unblock", targetId)
                        .header("Authorization", bearer(elevated.rawToken())))
                .andExpect(status().isOk());
        assertThat(reloadStudent(otherAdmin).getStatus()).isEqualTo(UserStatus.ACTIVE);

        mockMvc.perform(post("/users/{id}/warnings", targetId)
                        .header("Authorization", bearer(elevated.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Reviewed a report they filed\"}"))
                .andExpect(status().isOk());
        assertThat(warningRepository.countByStudent(reloadStudent(otherAdmin))).isEqualTo(1);

        // Field edits an ordinary administrator is refused on an administrator target.
        mockMvc.perform(patch("/users/{id}", targetId)
                        .header("Authorization", bearer(elevated.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\",\"credibilityScore\":42}"))
                .andExpect(status().isOk());
        Student edited = reloadStudent(otherAdmin);
        assertThat(edited.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(edited.getCredibilityScore()).isEqualTo(42);

        // The audit entry the panel renders: the actor role stays ADMIN, and the fact
        // that elevation was what allowed this travels in the metadata instead.
        AuditLog blocked = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.USER_BLOCKED)
                .findFirst()
                .orElseThrow();
        assertThat(blocked.getActorRole()).isEqualTo("ADMIN");
        assertThat(blocked.getActorType()).isEqualTo(AuditActorType.ADMIN);
        assertThat(blocked.getActorId()).isEqualTo(elevated.student().getId());
        assertThat(blocked.getMetadata().get("elevated")).isEqualTo(Boolean.TRUE);

        mockMvc.perform(delete("/users/{id}", targetId)
                        .header("Authorization", bearer(elevated.rawToken())))
                .andExpect(status().isOk());
        assertThat(reloadStudent(otherAdmin).getStatus()).isEqualTo(UserStatus.DELETED);
    }

    /**
     * Elevation lifts the protection of other administrator accounts, never of the
     * caller's own. Before elevation existed these four endpoints refused self only
     * because the caller is necessarily an administrator and the admin-target guard
     * caught it; this is the regression that guards the explicit self-check.
     */
    @Test
    void elevatedAdminStillCannotModerateItself() throws Exception {
        Session elevated = createSession(SUPERUSER_EMAIL, true);
        UUID selfId = elevated.student().getId();
        String token = bearer(elevated.rawToken());

        mockMvc.perform(delete("/users/{id}", selfId).header("Authorization", token))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}/block", selfId).header("Authorization", token))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}/unblock", selfId).header("Authorization", token))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/users/{id}/warnings", selfId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Not allowed\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}", selfId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}", selfId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isConflict());

        assertThat(reloadStudent(elevated).getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(adminRepository.findByStudent(reloadStudent(elevated)).isPresent())
                .as("an elevated operator cannot demote itself out of admins")
                .isTrue();
    }

    /**
     * Elevation is keyed on the address, so an ordinary administrator must not be able
     * to move it or to demote the account that holds it — either would hand elevation
     * over or take it away entirely.
     */
    @Test
    void elevatedOperatorIdentityIsProtected() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session elevated = createSession(SUPERUSER_EMAIL, true);
        UUID elevatedId = elevated.student().getId();

        mockMvc.perform(patch("/users/{id}", elevatedId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kitEmail\":\"moved@student.kit.edu\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}", elevatedId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isConflict());

        Student unchanged = reloadStudent(elevated);
        assertThat(unchanged.getKitEmail()).isEqualTo(SUPERUSER_EMAIL);
        assertThat(adminRepository.findByStudent(unchanged).isPresent()).isTrue();

        // An ordinary administrator target is still editable the way it always was.
        mockMvc.perform(patch("/users/{id}", admin.student().getId())
                        .header("Authorization", bearer(elevated.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Renamed Admin\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void reportedCommentsHaveNestedShapeAndNoOpDoesNotAudit() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Student reported = createStudent("reported@student.kit.edu");
        Student reporter = createStudent("reporter@student.kit.edu");
        Lecture lecture = createLecture();
        Comment comment = new Comment();
        comment.setContent("Reported content");
        comment.setStudent(reported);
        comment.setLecture(lecture);
        comment = commentRepository.saveAndFlush(comment);
        CommentReport report = new CommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(ReportReason.HARASSMENT);
        report.setExplanation(null);
        report = commentReportRepository.saveAndFlush(report);

        mockMvc.perform(get("/comments/reported")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].postContext").value("CS101 — Algorithms"))
                .andExpect(jsonPath("$.comments[0].reportText").value(""))
                .andExpect(jsonPath("$.comments[0].reportedUser.id")
                        .value(reported.getId().toString()))
                .andExpect(jsonPath("$.comments[0].reporter.id")
                        .value(reporter.getId().toString()));

        mockMvc.perform(patch("/comments/reported/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());
        long audits = auditLogRepository.count();
        mockMvc.perform(patch("/comments/reported/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());
        assertThat(auditLogRepository.count()).isEqualTo(audits);

        mockMvc.perform(patch("/comments/reported/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/comments/reported/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void bugReportUpdatesBothFieldsInOneAuditEvent() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        BugReport report = new BugReport();
        report.setTitle("Upload crash");
        report.setDescription("Description");
        report.setSeverity(BugSeverity.LOW);
        report.setReporter(null);
        report = bugReportRepository.saveAndFlush(report);

        mockMvc.perform(get("/reports").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bugReports[0].reporterName").value("Unknown"))
                .andExpect(jsonPath("$.bugReports[0].reportedAt", endsWith("Z")));

        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTION_TAKEN\",\"severity\":\"HIGH\"}"))
                .andExpect(status().isOk());

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.BUG_REPORT_UPDATED)
                .findFirst()
                .orElseThrow();
        assertThat(audit.getChanges().size()).isEqualTo(2);
        long auditCount = auditLogRepository.count();

        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTION_TAKEN\",\"severity\":\"HIGH\"}"))
                .andExpect(status().isOk());
        assertThat(auditLogRepository.count()).isEqualTo(auditCount);

        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\":\"CRITICAL\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/reports/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditFiltersAndStableCursorPaginationFollowContract() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Instant timestamp = Instant.parse("2026-07-23T10:00:00.000Z");
        saveAudit(admin, UUID.fromString("00000000-0000-0000-0000-000000000002"), timestamp);
        saveAudit(admin, UUID.fromString("00000000-0000-0000-0000-000000000001"), timestamp);

        String response = mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("limit", "1")
                        .param("q", "target@student.kit.edu")
                        .param("actorId", admin.student().getId().toString())
                        .param("action", "USER_BLOCKED")
                        .param("targetType", "USER")
                        .param("from", "2026-07-23T00:00:00.000Z")
                        .param("to", "2026-07-23T23:59:59.999Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(response, "$.nextCursor");

        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .param("q", "target@student.kit.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("cursor", "invalid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("action", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("targetType", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("actorId", "invalid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("actorType", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("from", "2026-07-24T00:00:00.000Z")
                        .param("to", "2026-07-23T00:00:00.000Z"))
                .andExpect(status().isBadRequest());

        admin.student().setUsername("renamed-admin");
        studentRepository.saveAndFlush(admin.student());
        mockMvc.perform(get("/audit-logs/meta")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actors[0].name").value("admin"))
                // The administrative page offers administrative actions only; student
                // activity and refusals are filtered by /activity-logs/meta.
                .andExpect(jsonPath("$.actions",
                        hasSize(AuditAction.of(AuditActionScope.ADMINISTRATIVE).size())))
                .andExpect(jsonPath("$.targetTypes", hasSize(AuditTargetType.values().length)))
                .andExpect(jsonPath("$.actorTypes", hasSize(AuditActorType.values().length)));

        // An action that belongs to the other page is a bad filter, not an empty result.
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("action", "USER_LOGIN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/activity-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("action", "USER_BLOCKED"))
                .andExpect(status().isBadRequest());

        Session student = createSession("audit-student@student.kit.edu", false);
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentActivityIsRecordedUnderTheUserActorType() throws Exception {
        requestCode("activity@student.kit.edu");
        String token = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"activity@student.kit.edu","loginToken":"%s"}
                                        """.formatted(codeDelivery.codeFor("activity@student.kit.edu"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.authToken"
        );

        Lecture lecture = createLecture();
        mockMvc.perform(post("/social/comments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lectureID":"%s","content":"Clear and well paced"}
                                """.formatted(lecture.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Profile upload fails","description":"Details","severity":"HIGH"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        AuditLog comment = auditOf(AuditAction.COMMENT_CREATED);
        assertThat(comment.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(comment.getActorRole()).isEqualTo("STUDENT");
        assertThat(comment.getTargetType()).isEqualTo(AuditTargetType.COMMENT);
        assertThat(comment.getTargetLabel()).isEqualTo("CS101 — Algorithms");
        assertThat(comment.getMetadata().get("contentPreview")).isEqualTo("Clear and well paced");
        assertThat(auditOf(AuditAction.BUG_REPORT_CREATED).getTargetType())
                .isEqualTo(AuditTargetType.BUG_REPORT);
        assertThat(auditOf(AuditAction.USER_LOGIN).getTargetType())
                .isEqualTo(AuditTargetType.USER_SESSION);
        auditOf(AuditAction.USER_LOGOUT);

        // The two log pages read the same table but must not show each other's events.
        // Nothing in this test performs an administrative action.
        Session admin = createSession("admin@student.kit.edu", true);
        mockMvc.perform(get("/activity-logs").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityLogs", hasSize(4)))
                .andExpect(jsonPath("$.activityLogs[0].actor.type").value("USER"));
        mockMvc.perform(get("/audit-logs").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs", hasSize(0)));

        mockMvc.perform(get("/activity-logs/meta")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions",
                        hasSize(AuditAction.of(AuditActionScope.ACTIVITY).size())))
                .andExpect(jsonPath("$.actors[0].email").value("activity@student.kit.edu"));

        Session student = createSession("student@student.kit.edu", false);
        mockMvc.perform(get("/activity-logs").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/activity-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void activityLogStaysListableWithARefusalOnThePage() throws Exception {
        // A refusal records something that did not happen, so it carries no target id. The
        // revertibility pass asked an empty immutable map for that null id, which throws, and
        // the panel saw a 500 on its activity log instead of a page with one unrevertible row.
        Session refused = createSession("refused@student.kit.edu", false);
        mockMvc.perform(get("/users").header("Authorization", bearer(refused.rawToken())))
                .andExpect(status().isForbidden());

        Session admin = createSession("admin@student.kit.edu", true);
        mockMvc.perform(get("/activity-logs").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityLogs", hasSize(1)))
                .andExpect(jsonPath("$.activityLogs[0].action").value("ACCESS_REFUSED"))
                .andExpect(jsonPath("$.activityLogs[0].revertible").value(false));
    }

    @Test
    void ratingsAnswersAndStudentReportsAreRecorded() throws Exception {
        Session student = createSession("poster@student.kit.edu", false);
        Lecture lecture = createLecture();

        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lectureId":"%s","topics":[{"category":"ORGANIZATION","value":4}]}
                                """.formatted(lecture.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Comment comment = new Comment();
        comment.setStudent(student.student());
        comment.setLecture(lecture);
        comment.setContent("Original comment");
        comment.setStatus(ContentStatus.VISIBLE);
        comment = commentRepository.saveAndFlush(comment);

        mockMvc.perform(post("/social/answers")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commentID":"%s","content":"An answer"}
                                """.formatted(comment.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/social/comments/report")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commentID":"%s","reportReason":"HARASSMENT","explanation":"Rude"}
                                """.formatted(comment.getId())))
                .andExpect(status().isOk());

        AuditLog rating = auditOf(AuditAction.RATING_SUBMITTED);
        assertThat(rating.getTargetType()).isEqualTo(AuditTargetType.RATING);
        assertThat(rating.getTargetLabel()).isEqualTo("CS101 — Algorithms");

        AuditLog answer = auditOf(AuditAction.ANSWER_CREATED);
        assertThat(answer.getTargetType()).isEqualTo(AuditTargetType.ANSWER);
        assertThat(answer.getTargetLabel()).isEqualTo("Answer to poster in CS101 — Algorithms");

        AuditLog report = auditOf(AuditAction.COMMENT_REPORT_CREATED);
        assertThat(report.getTargetType()).isEqualTo(AuditTargetType.COMMENT_REPORT);
        assertThat(report.getMetadata().get("reason")).isEqualTo("HARASSMENT");
    }

    @Test
    void refusedLoginsAndDeniedRequestsSurviveTheirRolledBackRequest() throws Exception {
        Student blocked = createStudent("blocked@student.kit.edu");
        blocked.setStatus(UserStatus.BLOCKED);
        studentRepository.saveAndFlush(blocked);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"blocked@student.kit.edu","loginToken":"ABC234"}
                                """))
                .andExpect(status().isForbidden());

        createStudent("wrong-code@student.kit.edu");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wrong-code@student.kit.edu","loginToken":"ABC234"}
                                """))
                .andExpect(status().isUnauthorized());

        // No account, so no actor to attribute the attempt to and nothing recorded.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@student.kit.edu","loginToken":"ABC234"}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.LOGIN_REFUSED)
                        .count()).isEqualTo(2);
        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.LOGIN_REFUSED)
                        .map(log -> log.getMetadata().get("reason"))
                        .collect(java.util.stream.Collectors.toSet())).isEqualTo(java.util.Set.of("ACCOUNT_BLOCKED", "INVALID_CODE"));

        Session student = createSession("denied@student.kit.edu", false);
        mockMvc.perform(get("/users").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());

        AuditLog refused = auditOf(AuditAction.ACCESS_REFUSED);
        assertThat(refused.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(refused.getTargetType()).isEqualTo(AuditTargetType.ENDPOINT);
        assertThat(refused.getTargetLabel()).isEqualTo("GET /users");
        assertThat(refused.getMetadata().get("path")).isEqualTo("/users");

        // An anonymous request never reaches an authorization rule and has no actor.
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.ACCESS_REFUSED)
                        .count()).isEqualTo(1);
    }

    @Test
    void systemStatusReportsUptimeCountsAndTheLastPersistedWrite() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("target@student.kit.edu", false);
        createLecture();

        mockMvc.perform(patch("/users/{id}/block", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/system/status").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.checkedAt", endsWith("Z")))
                .andExpect(jsonPath("$.startedAt", endsWith("Z")))
                .andExpect(jsonPath("$.uptimeSeconds").isNumber())
                .andExpect(jsonPath("$.database.reachable").value(true))
                .andExpect(jsonPath("$.database.latencyMs").isNumber())
                .andExpect(jsonPath("$.database.error").doesNotExist())
                .andExpect(jsonPath("$.counts.users").value(2))
                // The blocked target is still a user and no longer an active one, which is
                // the whole reason the dashboard needs both numbers.
                .andExpect(jsonPath("$.counts.activeUsers").value(1))
                .andExpect(jsonPath("$.counts.admins").value(1))
                .andExpect(jsonPath("$.counts.lectures").value(1))
                .andExpect(jsonPath("$.counts.auditEvents").value(1))
                .andExpect(jsonPath("$.counts.openCommentReports").value(0))
                .andExpect(jsonPath("$.lastWrite.action").value("USER_BLOCKED"))
                .andExpect(jsonPath("$.lastWrite.actorName").value("admin"))
                .andExpect(jsonPath("$.lastWrite.at", endsWith("Z")))
                .andExpect(jsonPath("$.lastWrite.ageSeconds").isNumber())
                // F-8: the test profile sets no IPINFO_TOKEN, so login locations cannot be
                // resolved -- and the panel can now see that instead of the operator
                // inferring it from every login mail saying "Unknown".
                .andExpect(jsonPath("$.locationLookupEnabled").value(false));

        Session student = createSession("student@student.kit.edu", false);
        mockMvc.perform(get("/system/status").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/system/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void systemStatusWithoutAuditHistoryOmitsTheLastWrite() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);

        mockMvc.perform(get("/system/status").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.counts.auditEvents").value(0))
                .andExpect(jsonPath("$.lastWrite").doesNotExist());
    }

    @Test
    void everyUserFieldIsEditableAndLockoutsAreRefused() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("target@student.kit.edu", false);
        String targetToken = issueToken(target.student());

        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"Corrected Name","biography":"Hi","credibilityScore":7}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Updated user successfully"));

        mockMvc.perform(get("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Corrected Name"))
                .andExpect(jsonPath("$.biography").value("Hi"));

        // Changing the address is a change of login identity, so the sessions have to go.
        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kitEmail\":\"MOVED@student.kit.edu\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/account/information").header("Authorization", bearer(targetToken)))
                .andExpect(status().isUnauthorized());
        assertThat(studentRepository.findById(target.student().getId()).orElseThrow().getKitEmail())
                .isEqualTo("moved@student.kit.edu");

        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        assertThat(adminRepository
                .findByStudent(studentRepository.findById(target.student().getId()).orElseThrow())
                .isPresent()).isTrue();

        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isOk());

        AuditLog updated = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.USER_UPDATED)
                .findFirst()
                .orElseThrow();
        assertThat(updated.getTargetType()).isEqualTo(AuditTargetType.USER);
        assertThat(updated.getChanges().containsKey("username")).isTrue();

        // Refusals: no empty update, no self lockout, no colliding identity, no delete
        // through a field edit.
        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kitEmail\":\"not-kit@example.com\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}", admin.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}", admin.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Ghost\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void warningsCanBeRewordedAndWithdrawn() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("warned@student.kit.edu", false);

        mockMvc.perform(post("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Typo in teh reason\"}"))
                .andExpect(status().isOk());
        UUID warningId = warningRepository.findAll().getFirst().getId();

        mockMvc.perform(patch("/users/{id}/warnings/{warningId}",
                        target.student().getId(), warningId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Typo in the reason\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0].message").value("Typo in the reason"));

        // A warning addressed under the wrong student is a wrong URL.
        mockMvc.perform(delete("/users/{id}/warnings/{warningId}",
                        admin.student().getId(), warningId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/users/{id}/warnings/{warningId}",
                        target.student().getId(), warningId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", hasSize(0)));

        auditOf(AuditAction.USER_WARNING_UPDATED);
        auditOf(AuditAction.USER_WARNING_DELETED);
    }

    @Test
    void commentsAndAnswersAreListedEditableAndDeletable() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session author = createSession("author@student.kit.edu", false);
        Lecture lecture = createLecture();

        Comment comment = new Comment();
        comment.setStudent(author.student());
        comment.setLecture(lecture);
        comment.setContent("Origianl typo");
        comment.setStatus(ContentStatus.VISIBLE);
        comment = commentRepository.saveAndFlush(comment);

        mockMvc.perform(post("/social/answers")
                        .header("Authorization", bearer(author.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commentID":"%s","content":"An answer"}
                                """.formatted(comment.getId())))
                .andExpect(status().isOk());
        UUID answerId = answerRepository.findAll().getFirst().getId();

        mockMvc.perform(get("/comments").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].content").value("Origianl typo"))
                .andExpect(jsonPath("$.comments[0].postContext").value("CS101 — Algorithms"))
                .andExpect(jsonPath("$.comments[0].author.name").value("author"))
                .andExpect(jsonPath("$.comments[0].answers").value(1))
                .andExpect(jsonPath("$.comments[0].reports").value(0));

        mockMvc.perform(get("/answers").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers", hasSize(1)))
                .andExpect(jsonPath("$.answers[0].commentPreview").value("Origianl typo"))
                .andExpect(jsonPath("$.answers[0].postContext").value("CS101 — Algorithms"));

        mockMvc.perform(patch("/comments/{id}", comment.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Original text\",\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk());
        Comment stored = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(stored.getContent()).isEqualTo("Original text");
        assertThat(stored.getStatus()).isEqualTo(ContentStatus.HIDDEN);

        mockMvc.perform(patch("/answers/{id}", answerId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"A better answer\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/comments/{id}", comment.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/comments/{id}", comment.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/comments/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isNotFound());

        // Deleting the comment takes the thread with it, including the notification the
        // answer raised — which is not cascaded and would dangle.
        mockMvc.perform(delete("/comments/{id}", comment.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        assertThat(commentRepository.count()).isEqualTo(0);
        assertThat(answerRepository.count()).isEqualTo(0);
        assertThat(notificationRepository.count()).isEqualTo(0);

        auditOf(AuditAction.COMMENT_UPDATED);
        auditOf(AuditAction.ANSWER_UPDATED);
        auditOf(AuditAction.COMMENT_DELETED);
    }

    @Test
    void answerReportsCanBeReviewedAndReportsWithdrawn() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session reporter = createSession("reporter@student.kit.edu", false);
        Lecture lecture = createLecture();

        Comment comment = new Comment();
        comment.setStudent(reporter.student());
        comment.setLecture(lecture);
        comment.setContent("Question");
        comment.setStatus(ContentStatus.VISIBLE);
        comment = commentRepository.saveAndFlush(comment);

        mockMvc.perform(post("/social/answers")
                        .header("Authorization", bearer(reporter.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commentID":"%s","content":"Rude answer"}
                                """.formatted(comment.getId())))
                .andExpect(status().isOk());
        UUID answerId = answerRepository.findAll().getFirst().getId();

        mockMvc.perform(post("/social/answers/report")
                        .header("Authorization", bearer(reporter.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answerID":"%s","reportReason":"HARASSMENT","explanation":"Rude"}
                                """.formatted(answerId)))
                .andExpect(status().isOk());

        String reported = mockMvc.perform(get("/answers/reported")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers", hasSize(1)))
                .andExpect(jsonPath("$.answers[0].answerContent").value("Rude answer"))
                .andExpect(jsonPath("$.answers[0].commentContent").value("Question"))
                .andExpect(jsonPath("$.answers[0].postContext").value("CS101 — Algorithms"))
                .andExpect(jsonPath("$.answers[0].status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String reportId = com.jayway.jsonpath.JsonPath.read(reported, "$.answers[0].id");

        // ACTION_TAKEN is the status that hides the answer, mirroring comment reports.
        mockMvc.perform(patch("/answers/reported/{id}", reportId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTION_TAKEN\"}"))
                .andExpect(status().isOk());
        assertThat(answerRepository.findById(answerId).orElseThrow().getStatus())
                .isEqualTo(ContentStatus.HIDDEN);

        mockMvc.perform(patch("/answers/reported/{id}", reportId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk());
        assertThat(answerRepository.findById(answerId).orElseThrow().getStatus())
                .isEqualTo(ContentStatus.VISIBLE);

        // Withdrawing the report leaves the answer alone.
        mockMvc.perform(delete("/answers/reported/{id}", reportId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/answers/reported").header("Authorization", bearer(admin.rawToken())))
                .andExpect(jsonPath("$.answers", hasSize(0)));
        assertThat(answerRepository.count()).isEqualTo(1);

        // Two status changes were made, and each is its own event.
        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.ANSWER_REPORT_STATUS_CHANGED)
                        .count()).isEqualTo(2);
        auditOf(AuditAction.ANSWER_REPORT_DELETED);
    }

    @Test
    void bugReportTextIsEditableAndReportsAreDeletable() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        BugReport report = new BugReport();
        report.setTitle("asdf");
        report.setDescription("broken");
        report.setSeverity(BugSeverity.LOW);
        report.setReporter(createStudent("reporter@student.kit.edu"));
        report = bugReportRepository.saveAndFlush(report);

        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Profile upload fails","description":"Steps to reproduce"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reports").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bugReports[0].title").value("Profile upload fails"))
                .andExpect(jsonPath("$.bugReports[0].description").value("Steps to reproduce"));

        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/reports/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        assertThat(bugReportRepository.count()).isEqualTo(0);
        auditOf(AuditAction.BUG_REPORT_DELETED);
    }

    @Test
    void ratingsCanBeDiscarded() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session student = createSession("rater@student.kit.edu", false);
        Lecture lecture = createLecture();

        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lectureId":"%s","topics":[{"category":"ORGANIZATION","value":1}]}
                                """.formatted(lecture.getId())))
                .andExpect(status().isOk());
        UUID ratingId = ratingRepository.findAll().getFirst().getId();

        mockMvc.perform(delete("/ratings/{id}", ratingId)
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/ratings/{id}", ratingId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        assertThat(ratingRepository.count()).isEqualTo(0);

        // The public read stays public even though the delete beside it is admin-only.
        mockMvc.perform(get("/ratings/{lectureId}", lecture.getId()))
                .andExpect(status().isOk());
        auditOf(AuditAction.RATING_DELETED);
    }

    @Test
    void ratingsListingIsAdminOnlyAndReturnsTheManagedShape() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session student = createSession("rater@student.kit.edu", false);
        Lecture lecture = createLecture();

        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lectureId":"%s","topics":[{"category":"ORGANIZATION","value":4}]}
                                """.formatted(lecture.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ratings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/ratings").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/ratings").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratings", hasSize(1)))
                .andExpect(jsonPath("$.ratings[0].lectureLabel").value("CS101 — Algorithms"))
                .andExpect(jsonPath("$.ratings[0].lectureId").value(lecture.getId().toString()))
                .andExpect(jsonPath("$.ratings[0].student.name").value("rater"))
                .andExpect(jsonPath("$.ratings[0].topics", hasSize(2)))
                // Inverted, not deleted. This read topics[0] == ORGANIZATION, which was the
                // order the rows happened to be inserted in -- Rating.topics is a JPA bag and
                // the listing walked it unordered, so the panel's column order was the
                // database's choice. Sorted by category now, which for an enum is declaration
                // order, and OVERALL is declared first. Same order as the two app-tier reads.
                .andExpect(jsonPath("$.ratings[0].topics[0].category").value("OVERALL"))
                .andExpect(jsonPath("$.ratings[0].topics[1].category").value("ORGANIZATION"))
                .andExpect(jsonPath("$.ratings[0].topics[1].value").value(4.0));
    }

    @Test
    void lecturesAndProfessorsAreEditableAndDeletable() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Lecture lecture = createLecture();
        Professor professor = new Professor();
        professor.setFirstName("Ada");
        professor.setLastName("Lovelase");
        professor = professorRepository.saveAndFlush(professor);

        mockMvc.perform(patch("/data/professor/{id}", professor.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastName":"Lovelace","lectureIds":["%s"]}
                                """.formatted(lecture.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/data/lectures/{id}", lecture.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecture.professors", hasSize(1)))
                .andExpect(jsonPath("$.lecture.professors[0].lastName").value("Lovelace"));

        mockMvc.perform(patch("/data/lectures/{id}", lecture.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS102","semesterYear":2027,"semesterSeason":"WS","active":false}
                                """))
                .andExpect(status().isOk());
        Lecture stored = lectureRepository.findById(lecture.getId()).orElseThrow();
        assertThat(stored.getCode()).isEqualTo("CS102");
        assertThat(stored.getSemesterYear()).isEqualTo(2027);
        assertThat(stored.isActive()).isFalse();

        mockMvc.perform(patch("/data/lectures/{id}", lecture.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/data/lectures/{id}", lecture.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professorIds\":[\"%s\"]}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        // Deleting the professor detaches it; the lecture and its history survive.
        mockMvc.perform(delete("/data/professor/{id}", professor.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        assertThat(professorRepository.count()).isEqualTo(0);
        mockMvc.perform(get("/data/lectures/{id}", lecture.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecture.professors", hasSize(0)));

        mockMvc.perform(delete("/data/lectures/{id}", lecture.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        assertThat(lectureRepository.count()).isEqualTo(0);

        auditOf(AuditAction.PROFESSOR_UPDATED);
        auditOf(AuditAction.PROFESSOR_DELETED);
        auditOf(AuditAction.LECTURE_UPDATED);
        auditOf(AuditAction.LECTURE_DELETED);

        // The public catalogue read is untouched by the admin-only write rules.
        mockMvc.perform(get("/data/lectures")).andExpect(status().isOk());
    }

    @Test
    void allLecturesAndAllProfessorsAreAdminOnlyAndIncludeDeactivatedEntries() throws Exception {
        Session student = createSession("student@student.kit.edu", false);
        Session admin = createSession("admin@student.kit.edu", true);

        Lecture inactiveLecture = createLecture();
        inactiveLecture.setCode("CS999");
        inactiveLecture.setActive(false);
        lectureRepository.saveAndFlush(inactiveLecture);

        Professor activeProfessor = new Professor();
        activeProfessor.setFirstName("Ada");
        activeProfessor.setLastName("Lovelace");
        professorRepository.saveAndFlush(activeProfessor);

        Professor inactiveProfessor = new Professor();
        inactiveProfessor.setFirstName("Grace");
        inactiveProfessor.setLastName("Hopper");
        inactiveProfessor.setActive(false);
        professorRepository.saveAndFlush(inactiveProfessor);

        mockMvc.perform(get("/data/lectures/all")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/data/lectures/all").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/data/lectures/all").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectures", hasSize(1)))
                .andExpect(jsonPath("$.lectures[0].code").value("CS999"))
                .andExpect(jsonPath("$.lectures[0].active").value(false))
                .andExpect(jsonPath("$.lectures[0].lectureType").value("LECTURE_ONLY"));

        mockMvc.perform(get("/data/professor/all")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/data/professor/all").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/data/professor/all").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professors", hasSize(2)))
                .andExpect(jsonPath("$.professors[?(@.lastName == 'Hopper')].active")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.professors[?(@.lastName == 'Lovelace')].active")
                        .value(hasItem(true)));

        // The public catalogue reads stay filtered to active = true.
        mockMvc.perform(get("/data/lectures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectures", hasSize(0)));
        mockMvc.perform(get("/data/professor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professors", hasSize(1)));
    }

    @Test
    void deletingALectureTakesItsCommentsRatingsAndAnswerRowsWithIt() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session author = createSession("author@student.kit.edu", false);
        Lecture lecture = createLecture();

        Comment comment = new Comment();
        comment.setStudent(author.student());
        comment.setLecture(lecture);
        comment.setContent("Comment");
        comment.setStatus(ContentStatus.VISIBLE);
        comment = commentRepository.saveAndFlush(comment);

        mockMvc.perform(post("/social/answers")
                        .header("Authorization", bearer(author.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commentID":"%s","content":"Answer"}
                                """.formatted(comment.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", bearer(author.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lectureId":"%s","topics":[{"category":"ORGANIZATION","value":3}]}
                                """.formatted(lecture.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/data/lectures/{id}", lecture.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        assertThat(lectureRepository.count()).isEqualTo(0);
        assertThat(commentRepository.count()).isEqualTo(0);
        assertThat(answerRepository.count()).isEqualTo(0);
        assertThat(ratingRepository.count()).isEqualTo(0);
        assertThat(notificationRepository.count()).isEqualTo(0);

        AuditLog deleted = auditOf(AuditAction.LECTURE_DELETED);
        assertThat(deleted.getMetadata().get("deletedComments")).isEqualTo(1);
        assertThat(deleted.getMetadata().get("deletedRatings")).isEqualTo(1);
    }

    @Test
    void auditWriterCoversAdminRefusalAndNullPayloadBranches() {
        Session admin = createSession("coverage-admin@student.kit.edu", true);
        Session student = createSession("coverage-student@student.kit.edu", false);

        AuditLog adminRefusal = auditWriter.writeRefusal(
                admin.student(),
                true,
                AuditAction.ACCESS_REFUSED,
                AuditTargetType.ENDPOINT,
                null,
                "PATCH /users/{id}/block",
                Map.of("path", "/users/coverage/block")
        );
        assertThat(adminRefusal.getActorType()).isEqualTo(AuditActorType.ADMIN);
        assertThat(adminRefusal.getActorRole()).isEqualTo("ADMIN");

        AuditLog studentRefusal = auditWriter.writeRefusal(
                student.student(),
                false,
                AuditAction.ACCESS_REFUSED,
                AuditTargetType.ENDPOINT,
                null,
                "GET /users",
                Map.of("path", "/users")
        );
        assertThat(studentRefusal.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(studentRefusal.getActorRole()).isEqualTo("STUDENT");

        AuditLog nullPayloads = auditWriter.write(
                admin.admin(),
                AuditAction.USER_BLOCKED,
                AuditTargetType.USER,
                student.student().getId(),
                "coverage-student",
                null,
                null
        );
        assertThat(nullPayloads.getChanges().isEmpty()).isTrue();
        assertThat(nullPayloads.getMetadata().isEmpty()).isTrue();
    }

    @Test
    void auditFailureRollsBackDomainMutation() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("rollback@student.kit.edu", false);
        doThrow(new IllegalStateException("audit failure"))
                .when(auditWriter)
                .write(
                        any(),
                        eq(AuditAction.USER_BLOCKED),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );

        mockMvc.perform(patch("/users/{id}/block", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isInternalServerError());
        assertThat(studentRepository.findById(target.student().getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void logoutAllRevokesEverySessionOfTheAccount() throws Exception {
        Session first = createSession("multi-device@student.kit.edu", false);
        String secondToken = issueToken(first.student());

        String validateBody = """
            {"email":"multi-device@student.kit.edu"}
            """;

        mockMvc.perform(post("/auth/logout-all")
                        .header("Authorization", bearer(first.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Both existing sessions have been revoked.
        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", bearer(first.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validateBody))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", bearer(secondToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validateBody))
                .andExpect(status().isUnauthorized());

        assertThat(tokenRepository.findAll().size()).isEqualTo(2);

        assertThat(tokenRepository.findAll().stream().allMatch(Token::isRevoked)).isTrue();

        assertThat(studentRepository.findById(first.student().getId())
                        .orElseThrow()
                        .getStatus()).isEqualTo(UserStatus.ACTIVE);

        // logout-all only destroys the sessions, not the account.
        // The account must be able to log in again afterwards.
        requestCode("multi-device@student.kit.edu");

        String freshToken = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email":"multi-device@student.kit.edu",
                                      "loginToken":"%s"
                                    }
                                    """.formatted(
                                        codeDelivery.codeFor("multi-device@student.kit.edu")
                                )))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.authToken"
        );

        // The newly issued session is usable again.
        mockMvc.perform(get("/social/notifications")
                        .header("Authorization", bearer(freshToken)))
                .andExpect(status().isOk());
    }


    @Test
    @WithAnonymousUser
    void logoutAllRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Not logged in"))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/auth/logout-all").header("Authorization", bearer("bogus-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Not logged in"));
    }

    @Test
    @WithAnonymousUser
    void healthChecksDatabaseReadiness() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("API healthy"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void warningIsDeliveredToTheStudentWithoutRestrictingThem() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("warned@student.kit.edu", false);

        String warningMessage = "Repeated harassment after prior warning";

        // Admin issues a warning to the student.
        mockMvc.perform(post("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "message": "%s"
                        }
                        """.formatted(warningMessage)))
                .andExpect(status().isOk());

        // The warning is delivered through the student's notification feed.
        // This successful request also proves that the student's existing session
        // remains valid after receiving the warning.
        mockMvc.perform(get("/social/notifications")
                        .header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.notifications", hasSize(1)))
                .andExpect(jsonPath("$.notifications[0].notificationID").exists())
                .andExpect(jsonPath("$.notifications[0].type").value("WARNING"))
                .andExpect(jsonPath("$.notifications[0].message").value(warningMessage))
                .andExpect(jsonPath("$.notifications[0].userName").isEmpty())
                .andExpect(jsonPath("$.notifications[0].content").isEmpty())
                .andExpect(jsonPath("$.notifications[0].lectureResponse").isEmpty())
                .andExpect(jsonPath("$.notifications[0].ownCommentID").isEmpty())
                .andExpect(jsonPath("$.notifications[0].createdAt").exists());

        // A warning is a disciplinary record, not an account restriction.
        assertThat(studentRepository.findById(target.student().getId())
                        .orElseThrow()
                        .getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Warnings accumulate and the current count is returned to admins.
        mockMvc.perform(post("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "message": "Second incident"
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.users[?(@.kitEmail == 'warned@student.kit.edu')].warnings",
                        hasItem(2)
                ));
    }



    @Test
    void blockedStudentIsRejectedEvenWithAnUnrevokedToken() throws Exception {
        Session target = createSession("blocked@student.kit.edu", false);

        // Change the status without going through the admin endpoint, so no token
        // is revoked. The filter itself must still reject the session.
        Student student = studentRepository.findById(target.student().getId()).orElseThrow();
        student.setStatus(UserStatus.BLOCKED);
        studentRepository.saveAndFlush(student);

        assertThat(tokenRepository.findAll().stream().noneMatch(Token::isRevoked)).isTrue();
        mockMvc.perform(get("/account/information").header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Not logged in"));

        // A blocked account cannot start a new session either, and its code is not
        // consumed on the way to the rejection.
        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blocked@student.kit.edu\"}"))
                .andExpect(status().isOk());
        assertThat(codeDelivery.codeFor("blocked@student.kit.edu")).isNull();
    }

    @Test
    void actionTakenHidesTheCommentAndRevertingRestoresIt() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Student reported = createStudent("hidden-author@student.kit.edu");
        Student reporter = createStudent("hidden-reporter@student.kit.edu");
        Lecture lecture = createLecture();
        Comment comment = new Comment();
        comment.setContent("Offending content");
        comment.setStudent(reported);
        comment.setLecture(lecture);
        comment = commentRepository.saveAndFlush(comment);
        CommentReport report = new CommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(ReportReason.HARASSMENT);
        report = commentReportRepository.saveAndFlush(report);

        mockMvc.perform(get("/social/comments/{id}", lecture.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)));

        mockMvc.perform(patch("/comments/reported/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTION_TAKEN\"}"))
                .andExpect(status().isOk());

        assertThat(commentRepository.findById(comment.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentStatus.HIDDEN);
        mockMvc.perform(get("/social/comments/{id}", lecture.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(0)));

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.COMMENT_REPORT_STATUS_CHANGED)
                .findFirst()
                .orElseThrow();
        assertThat(audit.getChanges().containsKey("commentStatus")).isTrue();

        mockMvc.perform(patch("/comments/reported/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/social/comments/{id}", lecture.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)));
    }

    /**
     * The test above is named for reverting and does not use the revert endpoint -- it toggles
     * the report back with a second PATCH. This is the one that presses the button the panel
     * shows, on the transition the handler's own javadoc calls the reason it exists.
     *
     * <p>{@code updateReportStatus} records two changed fields whenever visibility flips --
     * {@code status} and {@code commentStatus} -- while the handler's {@code currentValues}
     * offered only {@code status}, and {@code AuditRevertService} refuses an entry carrying a
     * key the handler does not know. So every {@code ACTION_TAKEN} transition, which is
     * exactly the set worth reverting, was permanently unrevertible.
     */
    @Test
    void anActionTakenReportStatusIsRevertibleThroughTheAuditEntry() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Student reported = createStudent("revert-author@student.kit.edu");
        Student reporter = createStudent("revert-reporter@student.kit.edu");
        Lecture lecture = createLecture();
        Comment comment = new Comment();
        comment.setContent("Offending content");
        comment.setStudent(reported);
        comment.setLecture(lecture);
        comment = commentRepository.saveAndFlush(comment);
        CommentReport report = new CommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(ReportReason.HARASSMENT);
        report = commentReportRepository.saveAndFlush(report);

        mockMvc.perform(patch("/comments/reported/{id}", report.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTION_TAKEN\"}"))
                .andExpect(status().isOk());
        assertThat(commentRepository.findById(comment.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentStatus.HIDDEN);

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.COMMENT_REPORT_STATUS_CHANGED)
                .findFirst()
                .orElseThrow();
        assertThat(audit.getChanges()).containsKeys("status", "commentStatus");

        // The panel decides whether to draw the button from this flag, so it has to agree
        // with what the button does.
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.auditLogs[?(@.action == 'COMMENT_REPORT_STATUS_CHANGED')].revertible")
                        .value(hasItem(true)));

        mockMvc.perform(post("/audit-logs/{id}/revert", audit.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        // Restoring the report status has to restore the visibility it took away, because
        // that is the damage the operator is undoing.
        assertThat(commentReportRepository.findById(report.getId()).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.OPEN);
        assertThat(commentRepository.findById(comment.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentStatus.VISIBLE);
        mockMvc.perform(get("/social/comments/{id}", lecture.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)));
    }

    @Test
    void deleteAnonymizesTheAccountButKeepsContentAndAuditHistory() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("gone@student.kit.edu", false);
        Lecture lecture = createLecture();
        Comment comment = new Comment();
        comment.setContent("Authored before deletion");
        comment.setStudent(target.student());
        comment.setLecture(lecture);
        comment = commentRepository.saveAndFlush(comment);

        mockMvc.perform(delete("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        Student deleted = studentRepository.findById(target.student().getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(deleted.getUsername().contains("gone")).isFalse();
        assertThat(deleted.getKitEmail().endsWith("@invalid.local")).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();

        // The content keeps a valid foreign key rather than dangling.
        assertThat(commentRepository.findById(comment.getId()).isPresent()).isTrue();

        // The audit row survives and still names who was deleted.
        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.USER_DELETED)
                .findFirst()
                .orElseThrow();
        assertThat(audit.getTargetLabel().contains("gone@student.kit.edu")).isTrue();

        mockMvc.perform(get("/account/information").header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userListPutsAdministratorsFirstThenNewestJoined() throws Exception {
        Session oldStudent = createSession("aaa-old@student.kit.edu", false);
        Session admin = createSession("admin@student.kit.edu", true);
        createSession("zzz-new@student.kit.edu", false);

        Student old = studentRepository.findById(oldStudent.student().getId()).orElseThrow();
        old.setCreatedAt(LocalDateTime.now().minusDays(30));
        studentRepository.saveAndFlush(old);

        mockMvc.perform(get("/users").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(3)))
                .andExpect(jsonPath("$.users[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.users[1].kitEmail").value("zzz-new@student.kit.edu"))
                .andExpect(jsonPath("$.users[2].kitEmail").value("aaa-old@student.kit.edu"));
    }

    @Test
    void studentsMayLogOutAndMalformedCursorsAreRejectedWithBadRequest() throws Exception {
        Session student = createSession("self-logout@student.kit.edu", false);

        mockMvc.perform(post("/auth/logout").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/account/information").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isUnauthorized());
        // A student logout is USER_LOGOUT; ADMIN_LOGOUT belongs to the administrative
        // record only.
        AuditLog logout = auditOf(AuditAction.USER_LOGOUT);
        assertThat(logout.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(auditLogRepository.count()).isEqualTo(1);

        Session admin = createSession("admin@student.kit.edu", true);
        String badTimestamp = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("1|not-a-timestamp|" + UUID.randomUUID())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("cursor", badTimestamp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid audit cursor"));
    }

    @Test
    void replayedRefusalsAgainstABlockedAccountCannotFillTheActivityLog() throws Exception {
        Student blocked = createStudent("blocked@student.kit.edu");
        blocked.setStatus(UserStatus.BLOCKED);
        studentRepository.saveAndFlush(blocked);

        // The status refusal has to consume the failure budget, otherwise the gate never
        // closes and every replay of a known address writes another record.
        for (int attempt = 0; attempt < 25; attempt++) {
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email":"blocked@student.kit.edu","loginToken":"ABC234"}
                            """));
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"blocked@student.kit.edu","loginToken":"ABC234"}
                                """))
                .andExpect(status().isTooManyRequests());

        long refusals = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.LOGIN_REFUSED)
                .count();
        assertThat(refusals)
                .as("login refusals must stop at the per-email failure limit")
                .isEqualTo(10);
    }

    /**
     * The third clause of {@code AdminRepository.hasModerationHistory} reads
     * {@code BugReport.resolvedBy}, and nothing in the application ever wrote it -- no setter
     * call in {@code src/main}, no default, no trigger. So an administrator whose entire
     * moderation history is bug reports could be demoted, taking the attribution with it,
     * while the same administrator could not be demoted over a single warning.
     *
     * <p>The comment and answer report services beside it both record {@code reviewedBy} and
     * {@code reviewedAt} on every status change; the bug report service recorded neither.
     * {@code docs/admin-api.md} already promised this: "Demoting is 409 once the account has
     * moderation history -- a warning it issued or a report it reviewed".
     */
    @Test
    void demotingAnAdministratorWhoResolvedABugReportIsRefused() throws Exception {
        Session acting = createSession("admin@student.kit.edu", true);
        Session other = createSession("bug-resolver@student.kit.edu", true);

        BugReport report = new BugReport();
        report.setTitle("Upload fails");
        report.setDescription("broken");
        report.setSeverity(BugSeverity.LOW);
        report.setReporter(createStudent("bug-reporter@student.kit.edu"));
        report = bugReportRepository.saveAndFlush(report);

        mockMvc.perform(patch("/reports/{id}", report.getId())
                        .header("Authorization", bearer(other.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTION_TAKEN\"}"))
                .andExpect(status().isOk());

        // Who dealt with it, and when, are recorded the way the two report twins record them.
        BugReport resolved = bugReportRepository.findById(report.getId()).orElseThrow();
        assertThat(resolved.getResolvedBy()).isNotNull();
        assertThat(resolved.getResolvedAt()).isNotNull();

        mockMvc.perform(patch("/users/{id}", other.student().getId())
                        .header("Authorization", bearer(acting.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Administrator has moderation history and cannot be demoted"));
    }

    @Test
    void demotingAnAdministratorWithModerationHistoryIsRefused() throws Exception {
        Session acting = createSession("admin@student.kit.edu", true);
        Session other = createSession("other@student.kit.edu", true);
        Session target = createSession("target@student.kit.edu", false);

        // The admin being demoted has already issued a warning, which points at its
        // admin row with a non-null foreign key.
        mockMvc.perform(post("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(other.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Be nice\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/users/{id}", other.student().getId())
                        .header("Authorization", bearer(acting.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Administrator has moderation history and cannot be demoted"));

        assertThat(adminRepository.findByStudent(other.student()).isPresent())
                .as("the admin row must survive a refused demotion")
                .isTrue();
        assertThat(warningRepository.count())
                .as("the warning must survive a refused demotion")
                .isEqualTo(1);
    }

    @Test
    void ownRatingCategoriesAndOverwriteAreCovered() throws Exception {
        Session student = createSession("edit-rating@student.kit.edu", false);
        Lecture lecture = createLecture();

        // Rating existiert noch nicht -- 200, und ratings ist eine leere Liste, nie null
        mockMvc.perform(get("/ratings/own/{lectureId}", lecture.getId())
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.ratings").isEmpty());

        // Lecture existiert nicht -- 404, wie bei der Kategorien-Route daneben
        mockMvc.perform(get("/ratings/own/{lectureId}", UUID.randomUUID())
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // Rating erstmals erstellen
        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lectureId":"%s","topics":[{"category":"ORGANIZATION","value":4}]}
                            """.formatted(lecture.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Eigenes Rating lesen
        mockMvc.perform(get("/ratings/own/{lectureId}", lecture.getId())
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.ratings", hasSize(2)));

        // Rating-Kategorien lesen
        mockMvc.perform(get("/ratings/{lectureId}/categories", lecture.getId())
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.categories").isArray());

        // Gleiche Lecture erneut bewerten -> EDIT/OVERWRITE-Zweig
        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"lectureId":"%s","topics":[{"category":"ORGANIZATION","value":2}]}
                            """.formatted(lecture.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(ratingRepository.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------
    // Fields the admin panel reads. Each of these switches on a control that is inert
    // without it, so the assertion is on the field being present and correct, not on the
    // request succeeding.
    // ---------------------------------------------------------------------------------

    @Test
    void warningHistoryNamesTheIssuingAdminAndIsAddressableById() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("student@student.kit.edu", false);

        mockMvc.perform(post("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Be civil\"}"))
                .andExpect(status().isOk());

        String history = mockMvc.perform(get("/users/{id}/warnings", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0].id").isString())
                // The issuer is named, not just referenced: a moderation record that cannot
                // say who warned someone is most of the point of the record missing.
                .andExpect(jsonPath("$.warnings[0].createdFrom.name").value("admin"))
                // and the id is the admin's student id, the same one /admin/auth/me returns.
                .andExpect(jsonPath("$.warnings[0].createdFrom.id")
                        .value(admin.student().getId().toString()))
                .andReturn().getResponse().getContentAsString();

        String warningId = com.jayway.jsonpath.JsonPath.read(history, "$.warnings[0].id");

        // The id the history hands out is the one the correct/withdraw routes accept.
        mockMvc.perform(patch("/users/{id}/warnings/{warningId}",
                        target.student().getId(), warningId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Please be civil\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/{id}/warnings/{warningId}",
                        target.student().getId(), warningId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        assertThat(warningRepository.count()).isEqualTo(0);
    }

    @Test
    void reportedContentCarriesTheIdOfTheContentItIsAbout() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session author = createSession("author@student.kit.edu", false);
        Session reporter = createSession("reporter@student.kit.edu", false);
        Lecture lecture = createLecture();

        Comment comment = new Comment();
        comment.setContent("Reported comment");
        comment.setStudent(author.student());
        comment.setLecture(lecture);
        comment = commentRepository.saveAndFlush(comment);

        CommentReport commentReport = new CommentReport();
        commentReport.setComment(comment);
        commentReport.setReporter(reporter.student());
        commentReport.setReason(ReportReason.SPAM);
        commentReport = commentReportRepository.saveAndFlush(commentReport);

        Answer answer = new Answer();
        answer.setContent("Reported answer");
        answer.setStudent(author.student());
        answer.setComment(comment);
        answer = answerRepository.saveAndFlush(answer);

        AnswerReport answerReport = new AnswerReport();
        answerReport.setAnswer(answer);
        answerReport.setReporter(reporter.student());
        answerReport.setReason(ReportReason.SPAM);
        answerReportRepository.saveAndFlush(answerReport);

        // id stays the report's; commentId is the content the report is about, which is what
        // lets the reports tab act on the comment without hunting for it in the content tab.
        mockMvc.perform(get("/comments/reported").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].id").value(commentReport.getId().toString()))
                .andExpect(jsonPath("$.comments[0].commentId").value(comment.getId().toString()));

        mockMvc.perform(get("/answers/reported").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].id").value(answerReport.getId().toString()))
                .andExpect(jsonPath("$.answers[0].answerId").value(answer.getId().toString()))
                // the parent thread, so the answer can be opened in context
                .andExpect(jsonPath("$.answers[0].commentId").value(comment.getId().toString()));
    }

    @Test
    void professorsReportTheirOwnLectureAssignment() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Lecture lecture = createLecture();
        Professor professor = new Professor();
        professor.setFirstName("Ada");
        professor.setLastName("Lovelace");
        professor.setActive(true);
        professor = professorRepository.saveAndFlush(professor);

        mockMvc.perform(patch("/data/professor/{id}", professor.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lectureIds\":[\"%s\"]}".formatted(lecture.getId())))
                .andExpect(status().isOk());

        // Read back from the professor rather than reverse-indexed from the lecture list,
        // which is what stops the two directions disagreeing once lectures are paginated.
        mockMvc.perform(get("/data/professor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professors[0].lectureIds", hasSize(1)))
                .andExpect(jsonPath("$.professors[0].lectureIds[0]")
                        .value(lecture.getId().toString()));

        mockMvc.perform(get("/data/professor/{id}", professor.getId()))
                .andExpect(status().isOk())
                // success was hard-coded false on this path, so a client trusting the flag
                // treated every found professor as a miss.
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.professor.lectureIds[0]").value(lecture.getId().toString()));
    }

    @Test
    void credibilityScoreIsReadableFromBothUserReads() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("student@student.kit.edu", false);

        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credibilityScore\":7}"))
                .andExpect(status().isOk());

        // Written through PATCH and now readable back, so the edit form can seed its input
        // instead of telling the operator the stored value is unknown.
        mockMvc.perform(get("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credibilityScore").value(7))
                // and GET /users/{id} is still flat, with no envelope
                .andExpect(jsonPath("$.success").doesNotExist());

        mockMvc.perform(get("/users").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[?(@.username == 'student')].credibilityScore")
                        .value(hasSize(1)));
    }

    // ---------------------------------------------------------------------------------
    // Listing parameters
    // ---------------------------------------------------------------------------------

    @Test
    void userListingFiltersSortsAndPaginatesWithoutTruncatingUnparameterisedCallers() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        createSession("ada@student.kit.edu", false);
        createSession("grace@student.kit.edu", false);

        // No limit: everything, and no cursor. A caller that has not been taught to follow
        // cursors is not silently cut down to one page.
        mockMvc.perform(get("/users").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(3)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        String firstPage = mockMvc.perform(get("/users?limit=2")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(2)))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(firstPage, "$.nextCursor");

        mockMvc.perform(get("/users?limit=2&cursor={cursor}", cursor)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mockMvc.perform(get("/users?q=ada").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].username").value("ada"));

        mockMvc.perform(get("/users?role=ADMIN").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].username").value("admin"));

        mockMvc.perform(get("/users?status=ACTIVE").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(3)));
    }

    @Test
    void userListingRejectsBadParametersWithASpecificReason() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);

        mockMvc.perform(get("/users?limit=0").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Limit must be an integer between 1 and 100"));
        mockMvc.perform(get("/users?limit=notanumber")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isBadRequest());
        // A malformed cursor is a 400 with a reason, never a 500.
        mockMvc.perform(get("/users?limit=5&cursor=not-a-cursor")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid user cursor"));
        mockMvc.perform(get("/users?cursor=anything")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A cursor requires a limit"));
        // Inverted, not deleted: this used to be a 400 "Deleted users are not listed",
        // because the listing excluded soft-deleted accounts unconditionally and the filter
        // could only have answered an empty page. The exclusion is conditional now, so the
        // ask is answerable and status=DELETED is an ordinary filter.
        mockMvc.perform(get("/users?status=DELETED")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.users", hasSize(0)));
        mockMvc.perform(get("/users?role=WIZARD").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid user role"));
    }

    @Test
    void deletedAccountsAreExcludedFromTheListingAndItsCountUnlessAskedForByName() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("student@student.kit.edu", false);
        UUID deletedId = target.student().getId();

        mockMvc.perform(delete("/users/{id}", deletedId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        // Counting a soft-deleted account would over-report the size of the platform, so the
        // default stays what it was. This half is unchanged and is the reason the exclusion
        // was made conditional rather than removed.
        mockMvc.perform(get("/users").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].username").value("admin"));
        mockMvc.perform(get("/users?limit=50").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(1)));
        // Neither does a status filter for something else reach them.
        mockMvc.perform(get("/users?status=ACTIVE").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(1)));

        // Asked for by name, the account is there -- anonymised, which is the whole point of
        // showing it: the id and the join date survive the scrub and the identity does not.
        // The panel puts a name back on this row from the USER_DELETED audit entry, whose
        // target label was written before the scrub.
        mockMvc.perform(get("/users?status=DELETED").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].id").value(deletedId.toString()))
                .andExpect(jsonPath("$.users[0].status").value("DELETED"))
                .andExpect(jsonPath("$.users[0].username")
                        .value("Deleted user " + deletedId.toString().substring(0, 8)));
    }

    /**
     * The panel's profile modal reads the account and its warning history in one pass and
     * renders the whole profile as "account gone" when <em>either</em> answers {@code 404}
     * (`useUserProfile.ts:24-32`, recorded in `adminweb-consumer-contract.md`). So these two
     * routes had to open together: opening one alone would have changed the contract and
     * fixed nothing on the screen. They are both read-only paths.
     *
     * <p>What did not move is the distinction underneath, and that is the half worth pinning:
     * {@code 404} still means <b>no such account</b>. A soft-deleted account exists, so it
     * stopped being reported as missing -- an unknown id has not.
     */
    @Test
    void aDeletedAccountIsReadableWithItsWarningsAndAnUnknownOneIsStillNotFound() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("student@student.kit.edu", false);
        UUID deletedId = target.student().getId();

        mockMvc.perform(post("/users/{id}/warnings", deletedId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Repeated harassment\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/{id}", deletedId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        // The account reads, anonymised. The scrub is what it is: the id and the join date
        // survive it and the identity does not.
        mockMvc.perform(get("/users/{id}", deletedId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deletedId.toString()))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.username")
                        .value("Deleted user " + deletedId.toString().substring(0, 8)));

        // The warning history survives the deletion and is what makes the modal worth
        // opening: the account is gone, the record of why it was moderated is not.
        mockMvc.perform(get("/users/{id}/warnings", deletedId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].message").value("Repeated harassment"));

        // An id that names no row at all is unchanged on both, and this is the assertion that
        // keeps the change from reading as "404 is gone from these routes".
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(get("/users/{id}", unknown)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/users/{id}/warnings", unknown)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.success").value(false));

        // Read-only, and deliberately so: ModeratedStudents.findMutable was left alone, so
        // every mutating route still refuses a deleted target. Visibility cannot grow into a
        // restore by accident.
        mockMvc.perform(patch("/users/{id}", deletedId)
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biography\":\"back\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/users/{id}/unblock", deletedId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void ratingListingFiltersByLectureAndPaginates() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session author = createSession("student@student.kit.edu", false);
        Lecture first = createLecture();
        Lecture second = new Lecture();
        second.setName("Databases");
        second.setCode("CS102");
        second.setSemesterYear(2026);
        second.setSemesterSeason(SemesterSeason.SS);
        second.setActive(true);
        second = lectureRepository.saveAndFlush(second);

        saveRating(author.student(), first);
        saveRating(author.student(), second);

        mockMvc.perform(get("/ratings").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratings", hasSize(2)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mockMvc.perform(get("/ratings?lectureId={id}", first.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratings", hasSize(1)))
                .andExpect(jsonPath("$.ratings[0].lectureId").value(first.getId().toString()));

        mockMvc.perform(get("/ratings?limit=1").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratings", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").isString());

        mockMvc.perform(get("/ratings?lectureId=nonsense")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid lecture id"));
    }

    // ---------------------------------------------------------------------------------
    // ADMIN_LOGIN / ADMIN_LOGOUT pairing
    // ---------------------------------------------------------------------------------

    @Test
    void promotingAStudentEndsItsSessionSoLogoutCannotOrphanAnAdminEvent() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("student@student.kit.edu", false);

        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        // Admin membership is resolved per request, so without this the promoted student's
        // live token would silently become an admin session that no ADMIN_LOGIN recorded,
        // and logging out would write an ADMIN_LOGOUT with no matching login.
        mockMvc.perform(post("/auth/logout").header("Authorization", bearer(target.rawToken())))
                .andExpect(status().isUnauthorized());
        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.ADMIN_LOGOUT)
                        .count()).as("no ADMIN_LOGOUT without a matching ADMIN_LOGIN").isEqualTo(0);
        assertThat(auditOf(AuditAction.USER_UPDATED).getMetadata().containsKey("sessionsRevoked"))
                .isTrue();
    }

    // ---------------------------------------------------------------------------------
    // Reverting an administrative edit
    // ---------------------------------------------------------------------------------

    @Test
    void aFieldEditIsRevertibleAndTheReversalIsItsOwnAuditEvent() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("student@student.kit.edu", false);

        mockMvc.perform(patch("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biography\":\"edited by mistake\"}"))
                .andExpect(status().isOk());
        UUID editId = auditOf(AuditAction.USER_UPDATED).getId();

        // The server decides revertibility and says so on the entry, so the panel never
        // reimplements the window or the staleness rule.
        mockMvc.perform(get("/audit-logs").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs[0].revertible").value(true))
                .andExpect(jsonPath("$.auditLogs[0].revertBlockedReason").doesNotExist());

        mockMvc.perform(post("/audit-logs/{id}/revert", editId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // The field is back...
        mockMvc.perform(get("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biography").value(""));

        // ...through a new event, with the original left exactly as it was recorded.
        AuditLog reversal = auditLogRepository.findFirstByRevertsAuditId(editId).orElseThrow();
        assertThat(reversal.getAction()).isEqualTo(AuditAction.USER_UPDATED);
        assertThat(reversal.getId()).isNotEqualTo(editId);
        assertThat(((Map<?, ?>) auditLogRepository.findById(editId).orElseThrow()
                        .getChanges().get("biography")).get("after")).isEqualTo("edited by mistake");

        // A second attempt is refused by cause, not by a generic failure.
        mockMvc.perform(post("/audit-logs/{id}/revert", editId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("ALREADY_REVERTED"));
    }

    @Test
    void everyRevertibleTargetTypeHasARegisteredHandler() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session author = createSession("author@student.kit.edu", false);
        Lecture lecture = createLecture();
        Comment comment = new Comment();
        comment.setContent("original");
        comment.setStudent(author.student());
        comment.setLecture(lecture);
        comment = commentRepository.saveAndFlush(comment);

        mockMvc.perform(patch("/comments/{id}", comment.getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"edited\"}"))
                .andExpect(status().isOk());

        // Most handlers are nested static @Component classes. If component scanning missed
        // them the registry would simply be smaller, with no startup error and every one of
        // their target types silently reporting ACTION_NOT_REVERTIBLE — so this reverts
        // through a nested handler rather than the top-level user one.
        UUID editId = auditOf(AuditAction.COMMENT_UPDATED).getId();
        mockMvc.perform(post("/audit-logs/{id}/revert", editId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        assertThat(commentRepository.findById(comment.getId()).orElseThrow().getContent())
                .isEqualTo("original");
        assertThat(auditLogRepository.findFirstByRevertsAuditId(editId).isPresent())
                .as("the reversal is recorded as its own event")
                .isTrue();
    }

    /**
     * A refusal reaches the panel through {@code GET /activity-logs} with a revert button
     * beside it like anything else, and clicking it used to answer 500: the entry carries no
     * target id, and {@code revert} asked an empty immutable map for a null key.
     * {@code describe} had been fixed for exactly this and {@code revert} had not, so the
     * list said "not revertible" while the action behind it crashed.
     */
    @Test
    void revertingARefusalIsRefusedRatherThanAnsweringAServerError() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session student = createSession("refused@student.kit.edu", false);

        // A student probing an admin route writes ACCESS_REFUSED, which has no target id.
        mockMvc.perform(get("/users").header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());

        AuditLog refusal = auditOf(AuditAction.ACCESS_REFUSED);
        assertThat(refusal.getTargetId()).as("the entry this test is about has a target id")
                .isNull();

        mockMvc.perform(post("/audit-logs/{id}/revert", refusal.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("ACTION_NOT_REVERTIBLE"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void revertRefusalsNameTheirCause() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("student@student.kit.edu", false);

        // A deletion cannot be inverted: USER_DELETED anonymises the row and the log does
        // not carry what was removed.
        mockMvc.perform(delete("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        UUID deleteId = auditOf(AuditAction.USER_DELETED).getId();
        mockMvc.perform(post("/audit-logs/{id}/revert", deleteId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("ACTION_NOT_REVERTIBLE"))
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/audit-logs").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs[?(@.action == 'USER_DELETED')].revertible")
                        .value(hasSize(1)))
                .andExpect(jsonPath("$.auditLogs[?(@.action == 'USER_DELETED')].revertBlockedReason")
                        .value(hasItem("ACTION_NOT_REVERTIBLE")));

        Session second = createSession("other@student.kit.edu", false);
        mockMvc.perform(patch("/users/{id}", second.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biography\":\"first\"}"))
                .andExpect(status().isOk());
        UUID firstEdit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.USER_UPDATED)
                .findFirst().orElseThrow().getId();

        // Something changed the field afterwards, so reverting would discard that change.
        mockMvc.perform(patch("/users/{id}", second.student().getId())
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biography\":\"second\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/audit-logs/{id}/revert", firstEdit)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("VALUE_CHANGED"));

        mockMvc.perform(post("/audit-logs/{id}/revert", UUID.randomUUID())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void blockingIsRevertibleAndAnOrdinaryErrorStillCarriesNoReason() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session target = createSession("student@student.kit.edu", false);

        mockMvc.perform(patch("/users/{id}/block", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        UUID blockId = auditOf(AuditAction.USER_BLOCKED).getId();

        mockMvc.perform(post("/audit-logs/{id}/revert", blockId)
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}", target.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Errors that carry no reason code are shaped exactly as they were before, with no
        // null field appearing in the body.
        mockMvc.perform(get("/users/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    // ---------------------------------------------------------------------------------
    // GitLab issues
    // ---------------------------------------------------------------------------------

    @Test
    void gitLabIssueCreationIsIdempotentAndRecorded() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        BugReport report = new BugReport();
        report.setTitle("Profile upload fails");
        report.setDescription("Uploading a picture returns 500");
        report.setSeverity(BugSeverity.HIGH);
        report.setStatus(ReportStatus.OPEN);
        report.setReporter(admin.student());
        report = bugReportRepository.saveAndFlush(report);

        gitLabClient.enable();

        mockMvc.perform(get("/reports").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bugReports[0].issueState").value("NONE"))
                .andExpect(jsonPath("$.bugReports[0].issueUrl").doesNotExist());

        mockMvc.perform(post("/reports/{id}/gitlab-issue", report.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueUrl").value("https://gitlab.example/issues/1"))
                .andExpect(jsonPath("$.issueState").value("CREATED"));

        // A double-click, a second admin, or a retry must not open another issue.
        mockMvc.perform(post("/reports/{id}/gitlab-issue", report.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueUrl").value("https://gitlab.example/issues/1"));
        assertThat(gitLabClient.calls()).isEqualTo(1);

        mockMvc.perform(get("/reports").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bugReports[0].issueState").value("CREATED"))
                .andExpect(jsonPath("$.bugReports[0].issueUrl")
                        .value("https://gitlab.example/issues/1"));

        AuditLog recorded = auditOf(AuditAction.BUG_REPORT_ISSUE_CREATED);
        assertThat(recorded.getActorId()).isEqualTo(admin.student().getId());
        assertThat(recorded.getTargetId()).isEqualTo(report.getId());
    }

    @Test
    void gitLabIssueCreationIsAdminOnlyAndDisabledWhenUnconfigured() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session student = createSession("student@student.kit.edu", false);
        BugReport report = new BugReport();
        report.setTitle("Broken");
        report.setDescription("Details");
        report.setSeverity(BugSeverity.LOW);
        report.setStatus(ReportStatus.OPEN);
        report.setReporter(student.student());
        report = bugReportRepository.saveAndFlush(report);

        gitLabClient.disable();

        // Unconfigured is a clear 503, and the panel is told up front so it can hide the
        // action rather than discover this by pressing it.
        mockMvc.perform(post("/reports/{id}/gitlab-issue", report.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("GitLab integration is not configured"));
        mockMvc.perform(get("/system/status").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gitlabEnabled").value(false));

        // Opening an issue is administrative; filing a bug report is not, and POST /reports
        // itself has to stay open to students.
        gitLabClient.enable();
        mockMvc.perform(post("/reports/{id}/gitlab-issue", report.getId())
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Still open\",\"description\":\"d\",\"severity\":\"LOW\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void gitLabFailureIsRecordedAsRetryableRatherThanSilent() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        BugReport report = new BugReport();
        report.setTitle("Broken");
        report.setDescription("Details");
        report.setSeverity(BugSeverity.LOW);
        report.setStatus(ReportStatus.OPEN);
        report.setReporter(admin.student());
        report = bugReportRepository.saveAndFlush(report);

        gitLabClient.enable();
        gitLabClient.failNext();

        mockMvc.perform(post("/reports/{id}/gitlab-issue", report.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isBadGateway());

        // The attempt is visible, so the panel can offer a retry instead of the failure
        // being invisible until someone clicks again.
        mockMvc.perform(get("/reports").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bugReports[0].issueState").value("FAILED"));

        mockMvc.perform(post("/reports/{id}/gitlab-issue", report.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueState").value("CREATED"));
    }

    /**
     * Every audit entry carries {@code changes}, {@code metadata} and {@code actor}, present
     * as keys and not merely non-null.
     *
     * <p>The distinction between "null" and "absent" is the whole finding. The admin panel
     * enumerates {@code changes} with {@code Object.keys(log.changes)} on <em>every row of the
     * audit table</em> and its mapper assigns no default, so a missing key is a TypeError that
     * blanks the screen rather than one bad row. It reads {@code log.actor.name} and
     * {@code .email} unguarded in the same place.
     *
     * <p>Which is why the fixture is what it is: the entries most likely to be omitted by a
     * serializer are the ones with nothing to say. A student sign-in and a content report
     * both record {@code changes: {}}, and roughly a dozen administrative actions record
     * {@code metadata: {}}. Those empty maps are exactly the shape a {@code NON_EMPTY}
     * inclusion rule would drop, and nothing today declares one -- this is what would notice
     * if something did.
     *
     * <p>Read as a map rather than through jsonPath because {@code jsonPath("$..changes")}
     * cannot tell a null from a key that was never written.
     */
    @Test
    void everyAuditEntryCarriesChangesMetadataAndAnActor() throws Exception {
        Session admin = createSession("audit-shape-admin@student.kit.edu", true);

        // An administrative action with a field diff, and one without: USER_BLOCKED records a
        // change, and the panel's own list of entry types with neither is sign-ins and
        // deletions.
        Student target = createStudent("audit-shape-target@student.kit.edu");
        mockMvc.perform(patch("/admin/users/{id}/block", target.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/users/{id}", target.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        // And an activity-scope entry, because the two above are administrative and land only
        // in /admin/audit-logs. A real student sign-in is also the single best case for this
        // test: USER_LOGIN is written through the path that hardcodes changes to an empty map,
        // so it is the entry a NON_EMPTY inclusion rule would strip first.
        String student = "audit-shape-student@student.kit.edu";
        createStudent(student);
        requestCode(student);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"loginToken\":\"%s\"}"
                                .formatted(student, codeDelivery.codeFor(student))))
                .andExpect(status().isOk());

        for (String path : List.of("/admin/audit-logs", "/admin/activity-logs")) {
            String body = mockMvc.perform(get(path)
                            .header("Authorization", bearer(admin.rawToken())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String key = path.endsWith("audit-logs") ? "auditLogs" : "activityLogs";
            List<Map<String, Object>> entries = JsonPath.read(body, "$." + key);

            assertThat(entries)
                    .as(path + " returned no entries, so the loop below asserts nothing")
                    .isNotEmpty();

            for (Map<String, Object> entry : entries) {
                assertThat(entry)
                        .as(path + " served an entry without one of the three keys the panel "
                                + "reads unguarded on every row: " + entry)
                        .containsKeys("changes", "metadata", "actor");

                assertThat(entry.get("changes")).as(path + " changes was null").isNotNull();
                assertThat(entry.get("metadata")).as(path + " metadata was null").isNotNull();
                assertThat(entry.get("actor")).as(path + " actor was null").isNotNull();
            }
        }
    }

    /**
     * Deleting an account does not leave its content authorless.
     *
     * <p>The panel reads {@code item.author.name} with no null check, so the question its
     * audit asks -- can {@code author} ever be null? -- decides whether the Comments table
     * survives a deleted account. It cannot: deletion anonymises the row in place rather than
     * removing it, precisely so that every comment, answer, vote and report keeps a valid
     * foreign key.
     *
     * <p>What the panel gets instead is a real author object whose name is a scrubbed literal
     * and whose status is {@code DELETED} -- renderable, and distinguishable.
     */
    @Test
    void contentOfADeletedAccountStillCarriesAnAuthor() throws Exception {
        Session admin = createSession("author-shape-admin@student.kit.edu", true);
        Student author = createStudent("author-shape-author@student.kit.edu");

        Comment comment = new Comment();
        comment.setContent("Written before the account went");
        comment.setStudent(author);
        comment.setLecture(createLecture());
        commentRepository.saveAndFlush(comment);

        mockMvc.perform(delete("/admin/users/{id}", author.getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/comments")
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].author").exists())
                .andExpect(jsonPath("$.comments[0].author.name")
                        .value(startsWith("Deleted user ")))
                .andExpect(jsonPath("$.comments[0].author.status").value("DELETED"));
    }

    private void saveRating(Student student, Lecture lecture) {
        Rating rating = new Rating();
        rating.setStudent(student);
        rating.setLecture(lecture);
        RatingTopic topic = new RatingTopic();
        topic.setCategory(RatingCategory.OVERALL);
        topic.setValue(4);
        topic.setRating(rating);
        rating.getTopics().add(topic);
        ratingRepository.saveAndFlush(rating);
    }

    private void requestCode(String email) throws Exception {
        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login code sent"));
    }

    /**
     * Creating a lecture is now audited, and the entry is deliberately not revertible.
     *
     * <p>Both halves matter. The record used to skip creations entirely, so a lecture could be
     * renamed with no entry saying where it came from. And the entry that closes that gap must
     * not offer a revert button: it is keyed {@code exists}, a lifecycle key, so
     * {@code AuditRevertService} refuses it -- undoing a creation is a deletion, which is its own
     * action with its own guards, not the inverse of a field diff.
     */
    @Test
    void creatingALectureIsRecordedAndIsNotRevertible() throws Exception {
        Session admin = createSession("catalogue-admin@student.kit.edu", true);

        mockMvc.perform(post("/admin/data/lectures")
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lineare Algebra 1",
                                  "code": "LA1",
                                  "semesterYear": 2026,
                                  "semesterSeason": "WS",
                                  "active": true,
                                  "professorIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/admin/audit-logs").header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs[0].action").value("LECTURE_CREATED"))
                .andExpect(jsonPath("$.auditLogs[0].target.type").value("LECTURE"))
                .andExpect(jsonPath("$.auditLogs[0].target.label").value("Lineare Algebra 1"))
                .andExpect(jsonPath("$.auditLogs[0].changes.exists.before").value(false))
                .andExpect(jsonPath("$.auditLogs[0].changes.exists.after").value(true))
                .andExpect(jsonPath("$.auditLogs[0].revertible").value(false))
                .andExpect(jsonPath("$.auditLogs[0].revertBlockedReason")
                        .value("ACTION_NOT_REVERTIBLE"));
    }

    /**
     * Characterization, not a defect. {@code UserResponse.reports} is built from
     * {@code CommentReportRepository.countByCommentStudent} alone, and
     * {@code AnswerReportRepository} has no per-author count at all -- neither the scalar nor
     * the grouped one its comment-side twin carries.
     *
     * <p>The asymmetry turned up in the twin-pair sweep and is **documented**:
     * {@code docs/admin-api.md} says "reports counts reports against comments authored by the
     * user". So the code matches its contract and the field is narrower than its name, which
     * is a product question rather than a bug. Pinned here so that widening it later is a
     * deliberate act with a test to invert, not a silent change to a number the panel shows.
     */
    @Test
    void theUserReportCountCountsCommentReportsAndNotAnswerReports() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);
        Session author = createSession("counted-author@student.kit.edu", false);
        Session reporter = createSession("counted-reporter@student.kit.edu", false);
        Lecture lecture = createLecture();

        Comment comment = new Comment();
        comment.setContent("Reported comment");
        comment.setStudent(author.student());
        comment.setLecture(lecture);
        comment = commentRepository.saveAndFlush(comment);

        CommentReport commentReport = new CommentReport();
        commentReport.setComment(comment);
        commentReport.setReporter(reporter.student());
        commentReport.setReason(ReportReason.SPAM);
        commentReportRepository.saveAndFlush(commentReport);

        // Two reports on this author's answer, and none of them is counted.
        Answer answer = new Answer();
        answer.setContent("Reported answer");
        answer.setStudent(author.student());
        answer.setComment(comment);
        answer = answerRepository.saveAndFlush(answer);

        AnswerReport answerReport = new AnswerReport();
        answerReport.setAnswer(answer);
        answerReport.setReporter(reporter.student());
        answerReport.setReason(ReportReason.SPAM);
        answerReportRepository.saveAndFlush(answerReport);

        mockMvc.perform(get("/users/{id}", author.student().getId())
                        .header("Authorization", bearer(admin.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports").value(1));
    }

    /**
     * The client-visible half of the two new actions, asserted against the response an
     * operator actually reads rather than against the row in the table.
     *
     * <p>Both are {@code ADMINISTRATIVE} scope, so they answer on {@code GET /audit-logs} beside
     * {@code USER_DELETED} rather than on {@code /activity-logs} — which matters because the
     * admin panel deleted its activity-log screen, so an {@code ACTIVITY} entry would have been
     * a record nobody can read. Both are non-revertible with
     * {@code revertBlockedReason: "ACTION_NOT_REVERTIBLE"}: recording the revival as a
     * {@code USER_UPDATED} field diff instead would have handed the panel a working undo and put
     * a restore route into the product through the back door of the revert machinery, which
     * {@code docs/adr/0015-deletion-not-split-from-anonymisation.md} refuses.
     *
     * <p>This is the shape written into the {@code CHANGELOG} under {@code 10.09 (20)}, and it is
     * here so that the entry and the response cannot drift apart.
     */
    @Test
    void selfDeletionAndRevivalAreServedOnTheAdministrativeLogAndNeitherCanBeReverted()
            throws Exception {

        Session admin = createSession("admin@student.kit.edu", true);
        Session student = createSession("leaving@student.kit.edu", false);

        mockMvc.perform(patch("/account/deleteAccount")
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isOk());

        // Anonymous, no header: this is the revival, and it is F-48 rather than a feature.
        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leaving@student.kit.edu\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("action", "USER_SELF_DELETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs", hasSize(1)))
                .andExpect(jsonPath("$.auditLogs[0].actor.type").value("USER"))
                .andExpect(jsonPath("$.auditLogs[0].actor.role").value("STUDENT"))
                .andExpect(jsonPath("$.auditLogs[0].target.type").value("USER"))
                .andExpect(jsonPath("$.auditLogs[0].target.label")
                        .value("leaving (leaving@student.kit.edu)"))
                .andExpect(jsonPath("$.auditLogs[0].metadata.anonymized").value(false))
                .andExpect(jsonPath("$.auditLogs[0].revertible").value(false))
                .andExpect(jsonPath("$.auditLogs[0].revertBlockedReason")
                        .value("ACTION_NOT_REVERTIBLE"));

        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("action", "USER_SELF_REACTIVATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs", hasSize(1)))
                .andExpect(jsonPath("$.auditLogs[0].actor.type").value("USER"))
                .andExpect(jsonPath("$.auditLogs[0].metadata.trigger")
                        .value("LOGIN_CODE_REQUEST"))
                .andExpect(jsonPath("$.auditLogs[0].metadata.callerAuthenticated").value(false))
                .andExpect(jsonPath("$.auditLogs[0].revertible").value(false));

        // Neither reaches the activity log, which is the half of the scope decision that a
        // reader would otherwise have to take on trust.
        mockMvc.perform(get("/activity-logs")
                        .header("Authorization", bearer(admin.rawToken()))
                        .param("action", "USER_SELF_DELETED"))
                .andExpect(status().isBadRequest());
    }

    private Session createSession(String email, boolean adminRole) {
        Student student = createStudent(email);
        Admin admin = null;
        if (adminRole) {
            admin = new Admin();
            admin.setStudent(student);
            admin = adminRepository.saveAndFlush(admin);
        }
        String raw = TokenGenerator.generateToken();
        Token token = new Token();
        token.setEmail(email);
        token.setStudent(student);
        token.setHash(TokenHasher.hash(raw));
        token.setExpiresAt(LocalDateTime.now().plusHours(12));
        token = tokenRepository.saveAndFlush(token);
        return new Session(student, admin, token, raw);
    }

    private String issueToken(Student student) {
        String raw = TokenGenerator.generateToken();
        Token token = new Token();
        token.setEmail(student.getKitEmail());
        token.setStudent(student);
        token.setHash(TokenHasher.hash(raw));
        token.setExpiresAt(LocalDateTime.now().plusHours(12));
        tokenRepository.saveAndFlush(token);
        return raw;
    }

    private Student createStudent(String email) {
        Student student = new Student();
        student.setKitEmail(email);
        student.setUsername(email.substring(0, email.indexOf('@')));
        student.setCredibilityScore(0);
        student.setStatus(UserStatus.ACTIVE);
        student.setEmailVerifiedAt(LocalDateTime.now());
        return studentRepository.saveAndFlush(student);
    }

    private Lecture createLecture() {
        Lecture lecture = new Lecture();
        lecture.setName("Algorithms");
        lecture.setCode("CS101");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        return lectureRepository.saveAndFlush(lecture);
    }

    private void saveAudit(Session admin, UUID id, Instant createdAt) {
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setAction(AuditAction.USER_BLOCKED);
        log.setCreatedAt(createdAt);
        log.setActorType(AuditActorType.ADMIN);
        log.setActorId(admin.student().getId());
        log.setActorName(admin.student().getUsername());
        log.setActorEmail(admin.student().getKitEmail());
        log.setActorRole("ADMIN");
        log.setTargetType(AuditTargetType.USER);
        log.setTargetId(UUID.randomUUID());
        log.setTargetLabel("Target (target@student.kit.edu)");
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("before", "ACTIVE");
        status.put("after", "BLOCKED");
        log.setChanges(Map.of("status", status));
        log.setMetadata(Map.of());
        auditLogRepository.saveAndFlush(log);
    }

    /** The single recorded event with this action, failing when there is not exactly one. */
    private AuditLog auditOf(AuditAction action) {
        java.util.List<AuditLog> matches = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == action)
                .toList();
        assertThat(matches.size()).as(action + " entries").isEqualTo(1);
        return matches.getFirst();
    }

    private Student reloadStudent(Session session) {
        return studentRepository.findById(session.student().getId()).orElseThrow();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(Student student, Admin admin, Token token, String rawToken) {
    }
}


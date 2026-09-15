package com.pse;

import com.jayway.jsonpath.JsonPath;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.support.MappedRoutes;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.auth.repository.OneTimePasswordRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.repository.RatingRepository;
import com.pse.shared.enums.ContentStatus;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.UserStatus;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.model.Notification;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.CommentVoteRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestDeliveryConfig.CapturingLoginCodeDelivery;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;

/**
 * The ownership matrix: can one student reach another student's resource by its id?
 *
 * <p>Built the same way as {@link ApiAuthorizationMatrixTests} and for the same reason.
 * That class asks "may an anonymous caller in?", this one asks the question after the
 * caller is already authenticated: a valid token is not permission to touch a row that
 * belongs to somebody else. The two together are the horizontal and vertical halves of the
 * same table.
 *
 * <p>The route list is read from Spring's handler mappings rather than kept by hand, so a
 * new endpoint taking an id cannot join the API without someone classifying it. Findings
 * are recorded from F-18 in docs/test-findings.md.
 */
@ApiIntegrationTest
class ApiOwnershipMatrixTests {

    /**
     * Routes whose id names a row belonging to one student, probed cross-user below.
     *
     * <p>This is the set that can carry an IDOR. It is short because the app tier turned
     * out to address exactly one per-user resource by id; see
     * {@link #noAppTierHandlerTakesACallerIdentityFromTheRequest()} for the structural
     * reason that is not an accident.
     */
    private static final Set<String> OWNER_SCOPED_ROUTES = Set.of(
            "PATCH /social/notifications/{notification_id}"
    );

    /**
     * Routes where reaching another student's row is the feature, not a defect.
     *
     * <p>Voting is the whole point of a vote endpoint: the comment belongs to somebody
     * else and a student votes on it precisely because it is not theirs. The id here
     * addresses public content, not a private row, and the vote that gets written is keyed
     * to the caller's own id from the principal -- which is what
     * {@code SocialServiceVoteTests} pins.
     */
    private static final Set<String> CROSS_USER_BY_DESIGN = Set.of(
            "POST /social/comments/vote/comment/{comment_id}",
            "POST /social/comments/vote/answer/{answer_id}"
    );

    /**
     * The administrative tier, where acting on another account is the job.
     *
     * <p>These are not exempt from authorization, they are governed by a different rule:
     * role, not ownership. {@link AdminApiPathSplitTests} covers the role side and
     * {@link #administrativeRoutesRefuseAStudentEvenWithARealTargetId()} checks the
     * ownership-shaped attack on them -- a student who knows a real victim id.
     */
    private static final Set<String> ADMIN_TIER = Set.of(
            "PATCH /users/{id}",
            "PATCH /admin/users/{id}",
            "GET /users/{id}",
            "GET /admin/users/{id}",
            "DELETE /users/{id}",
            "DELETE /admin/users/{id}",
            "PATCH /users/{id}/block",
            "PATCH /admin/users/{id}/block",
            "PATCH /users/{id}/unblock",
            "PATCH /admin/users/{id}/unblock",
            "GET /users/{id}/warnings",
            "GET /admin/users/{id}/warnings",
            "POST /users/{id}/warnings",
            "POST /admin/users/{id}/warnings",
            "PATCH /users/{id}/warnings/{warningId}",
            "PATCH /admin/users/{id}/warnings/{warningId}",
            "DELETE /users/{id}/warnings/{warningId}",
            "DELETE /admin/users/{id}/warnings/{warningId}",
            "PATCH /comments/{id}",
            "PATCH /admin/comments/{id}",
            "DELETE /comments/{id}",
            "DELETE /admin/comments/{id}",
            "PATCH /comments/reported/{id}",
            "PATCH /admin/comments/reported/{id}",
            "DELETE /comments/reported/{id}",
            "DELETE /admin/comments/reported/{id}",
            "PATCH /answers/{id}",
            "PATCH /admin/answers/{id}",
            "DELETE /answers/{id}",
            "DELETE /admin/answers/{id}",
            "PATCH /answers/reported/{id}",
            "PATCH /admin/answers/reported/{id}",
            "DELETE /answers/reported/{id}",
            "DELETE /admin/answers/reported/{id}",
            "DELETE /ratings/{id}",
            "DELETE /admin/ratings/{id}",
            "PATCH /reports/{id}",
            "PATCH /admin/reports/{id}",
            "DELETE /reports/{id}",
            "DELETE /admin/reports/{id}",
            "POST /reports/{id}/gitlab-issue",
            "POST /admin/reports/{id}/gitlab-issue",
            "POST /audit-logs/{id}/revert",
            "POST /admin/audit-logs/{id}/revert",
            "PATCH /data/lectures/{id}",
            "PATCH /admin/data/lectures/{id}",
            "DELETE /data/lectures/{id}",
            "DELETE /admin/data/lectures/{id}",
            "PATCH /data/professor/{id}",
            "PATCH /admin/data/professor/{id}",
            "DELETE /data/professor/{id}",
            "DELETE /admin/data/professor/{id}"
    );

    /**
     * Reads whose id names a lecture or a professor -- catalogue rows that belong to
     * nobody. There is no "other student's copy" of a lecture to reach.
     *
     * <p>{@code GET /ratings/own/{lectureId}} is in here rather than in
     * {@link #OWNER_SCOPED_ROUTES} for a reason worth stating: the id in the path is the
     * lecture, and whose rating comes back is decided by the principal alone. The path
     * offers no way to name another student, which is why
     * {@link #ownRatingIsScopedToThePrincipalAndNotToThePath()} tests it by giving two
     * students the same lecture id instead of by swapping ids.
     */
    private static final Set<String> NOT_OWNED_BY_A_USER = Set.of(
            "GET /data/lectures/{lecture_id}",
            "GET /data/professor/{professor_id}",
            "GET /ratings/{lectureId}",
            "GET /ratings/{lectureId}/categories",
            "GET /ratings/own/{lectureId}",
            "GET /social/comments/{lecture_id}"
    );

    /**
     * Controllers that serve the app tier -- the surface a student's own token reaches.
     * The moderation, audit and admin-auth controllers are governed by role instead and
     * are allowed to take a target id from the request.
     */
    private static final Set<String> APP_TIER_CONTROLLERS = Set.of(
            "SocialController",
            "AccountController",
            "RatingController",
            "AuthController",
            "LectureController",
            "ProfessorController",
            "HealthController"
    );

    /** Parameter and field names that would mean the caller names themselves. */
    private static final List<String> IDENTITY_NAMES =
            List.of("studentid", "userid", "ownerid", "authorid", "recipientid",
                    "kitemail", "accountid", "principalid");

    private static final String STUDENT_A = "owner-a@student.kit.edu";
    private static final String STUDENT_B = "owner-b@student.kit.edu";
    private static final String ADMIN_EMAIL = "admin@student.kit.edu";

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
    @Autowired CommentVoteRepository commentVoteRepository;
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
     * The ratchet. Every route that takes an id has to be classified, so a new one cannot
     * arrive without somebody deciding whether its id can name another student's row.
     *
     * <p>A failure here is a question, not a defect: which of the four sets does this
     * route belong in? Put it in one and, if it is owner-scoped, give it a probe below.
     */
    @Test
    void everyRouteTakingAnIdIsClassified() {
        Set<String> classified = new TreeSet<>();
        classified.addAll(OWNER_SCOPED_ROUTES);
        classified.addAll(CROSS_USER_BY_DESIGN);
        classified.addAll(ADMIN_TIER);
        classified.addAll(NOT_OWNED_BY_A_USER);

        Set<String> idTaking = idTakingRoutes();

        assertThat(idTaking.size() >= 40)
                .as("expected the id-taking surface, found only " + idTaking.size() + " routes")
                .isTrue();

        List<String> unclassified = new ArrayList<>();
        for (String route : idTaking) {
            if (!classified.contains(route)) {
                unclassified.add(route);
            }
        }

        assertThat(unclassified.isEmpty()).as("these routes take an id and nobody has said whose row it names. Put each in "
                        + "OWNER_SCOPED_ROUTES (and add a cross-user probe), CROSS_USER_BY_DESIGN, "
                        + "ADMIN_TIER or NOT_OWNED_BY_A_USER: " + unclassified).isTrue();

        List<String> stale = new ArrayList<>();
        for (String route : classified) {
            if (!idTaking.contains(route)) {
                stale.add(route);
            }
        }

        assertThat(stale.isEmpty()).as("these routes are classified but are not mapped any more -- drop them from the "
                        + "sets so the classification cannot drift from the API: " + stale).isTrue();
    }

    /**
     * The one per-user row the app tier addresses by id, reached with the wrong student's
     * token.
     *
     * <p>Asserted on the <em>effect</em> first and on the status second, and the order is
     * deliberate: the refusal is real -- {@code findByRecipientAndId} scopes the lookup to
     * the caller, so B's row is not touched -- and it used to come back as HTTP 200 with
     * {@code success:false}, which was F-5. The row check is what proved the refusal while
     * the status could not, and it stays the primary assertion: a status is evidence about
     * the answer, not about the table.
     */
    @Test
    void markingAnotherStudentsNotificationAsSeenDoesNotTouchTheRow() throws Exception {
        Student owner = createStudent(STUDENT_B);
        Lecture lecture = createLecture();
        Comment comment = createComment(owner, lecture);
        Notification notification = createNotification(owner, lecture, comment);

        String attacker = studentSession(STUDENT_A);

        int status = mockMvc.perform(patch("/social/notifications/" + notification.getId())
                        .header("Authorization", "Bearer " + attacker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        Notification after = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(after.isSeen())
                .as("another student's token marked this notification as seen -- this is an IDOR")
                .isFalse();

        // F-5, fixed: the refusal has a status of its own, so a client can tell it from a
        // success without reading the body.
        assertThat(status)
                .as("the refusal lost its own status code -- it is a 200 with success:false again")
                .isEqualTo(404);
    }

    /**
     * The owner's own call still works, so the test above is not passing because the
     * endpoint is broken for everybody.
     */
    @Test
    void theOwnerCanStillMarkTheirOwnNotificationAsSeen() throws Exception {
        String ownerToken = studentSession(STUDENT_B);
        Student owner = studentRepository.findByKitEmail(STUDENT_B).orElseThrow();
        Lecture lecture = createLecture();
        Comment comment = createComment(owner, lecture);
        Notification notification = createNotification(owner, lecture, comment);

        mockMvc.perform(patch("/social/notifications/" + notification.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        Notification after = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(after.isSeen())
                .as("the owner's own call did not mark the notification as seen")
                .isTrue();
    }

    /**
     * {@code GET /ratings/own/{lectureId}} takes a lecture id, not a student id, so the
     * cross-user probe is two students asking for the same lecture: each has to get their
     * own rating and neither may see the other's.
     */
    @Test
    void ownRatingIsScopedToThePrincipalAndNotToThePath() throws Exception {
        String tokenA = studentSession(STUDENT_A);
        String tokenB = studentSession(STUDENT_B);
        Lecture lecture = createLecture();

        // Only B rates the lecture. A asks for the same lecture and must not receive it.
        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lectureId":"%s","topics":[{"category":"ORGANIZATION","value":4}]}
                                """.formatted(lecture.getId())))
                .andExpect(status().isOk());

        String bodyForA = mockMvc.perform(get("/ratings/own/" + lecture.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getContentAsString();

        assertThat(Boolean.TRUE.equals(JsonPath.read(bodyForA, "$.success"))).as("student A was given a rating for a lecture they never rated -- B's rating "
                        + "leaked through /ratings/own: " + bodyForA).isFalse();

        String bodyForB = mockMvc.perform(get("/ratings/own/" + lecture.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn().getResponse().getContentAsString();

        assertThat(Boolean.TRUE.equals(JsonPath.read(bodyForB, "$.success")))
                .as("the owner could not read their own rating back: " + bodyForB)
                .isTrue();
    }

    /**
     * A student's notification list contains only their own rows. The list endpoint takes
     * no id at all, which is exactly why it is worth a test: the scoping is invisible in
     * the route and lives entirely in the query.
     */
    @Test
    void theNotificationListDoesNotLeakAnotherStudentsRows() throws Exception {
        Student other = createStudent(STUDENT_B);
        Lecture lecture = createLecture();
        Comment comment = createComment(other, lecture);
        createNotification(other, lecture, comment);

        String tokenA = studentSession(STUDENT_A);

        String body = mockMvc.perform(get("/social/notifications")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Object> notifications = JsonPath.read(body, "$.notifications");
        assertThat(notifications.isEmpty())
                .as("student A was shown notifications belonging to B: " + body)
                .isTrue();
    }

    /**
     * The ownership-shaped attack on the administrative tier: a student who has learned a
     * real victim id. The refusal has to come from the role, before the handler looks the
     * id up, so knowing a real id must buy nothing over knowing a random one.
     */
    @Test
    void administrativeRoutesRefuseAStudentEvenWithARealTargetId() throws Exception {
        Student victim = createStudent(STUDENT_B);
        String attacker = studentSession(STUDENT_A);

        List<String> refused = new ArrayList<>();
        List<String> probes = List.of(
                "GET /admin/users/" + victim.getId(),
                "PATCH /admin/users/" + victim.getId(),
                "PATCH /admin/users/" + victim.getId() + "/block",
                "POST /admin/users/" + victim.getId() + "/warnings",
                "GET /users/" + victim.getId(),
                "PATCH /users/" + victim.getId() + "/block");

        for (String probe : probes) {
            String verb = probe.substring(0, probe.indexOf(' '));
            String path = probe.substring(probe.indexOf(' ') + 1);

            var request = switch (verb) {
                case "GET" -> get(path);
                case "POST" -> post(path);
                default -> patch(path);
            };
            if (!"GET".equals(verb)) {
                request = request.contentType(MediaType.APPLICATION_JSON).content("{}");
            }

            int status = mockMvc.perform(request.header("Authorization", "Bearer " + attacker))
                    .andReturn().getResponse().getStatus();

            if (status != 403) {
                refused.add(probe + " -> " + status);
            }
        }

        assertThat(refused.isEmpty())
                .as("a student reached an administrative route carrying a real victim id: " + refused)
                .isTrue();

        Student stillThere = studentRepository.findById(victim.getId()).orElseThrow();
        assertThat(stillThere.getStatus())
                .as("the victim's account was modified by a student's request")
                .isEqualTo(UserStatus.ACTIVE);
    }

    /**
     * The structural reason the owner-scoped set above is one line long: in the app tier
     * the caller's identity comes only from {@code @AuthenticationPrincipal}. No handler
     * accepts a student id as a path variable, a query parameter or a body field, so there
     * is nothing to tamper with.
     *
     * <p>This is the invariant worth keeping rather than the individual probes. The day a
     * handler takes {@code studentId} from the request, the IDOR surface stops being one
     * endpoint and this test says so before anyone has to notice.
     */
    @Test
    void noAppTierHandlerTakesACallerIdentityFromTheRequest() {
        // A set, because a handler mapped at several patterns reaches this loop once each and
        // repeating its name would not make the failure clearer.
        Set<String> offenders = new TreeSet<>();

        for (MappedRoutes.Route route : MappedRoutes.application(handlerMapping)) {
            HandlerMethod handler = route.handler();
            if (!isAppTier(handler)) {
                continue;
            }
            for (Parameter parameter : handler.getMethod().getParameters()) {
                if (parameter.isAnnotationPresent(PathVariable.class)
                        || parameter.isAnnotationPresent(RequestParam.class)) {
                    if (looksLikeIdentity(parameter.getName())) {
                        offenders.add(describe(handler) + " takes " + parameter.getName()
                                + " from the request");
                    }
                }
                if (parameter.isAnnotationPresent(RequestBody.class)) {
                    for (Field field : parameter.getType().getDeclaredFields()) {
                        if (looksLikeIdentity(field.getName())) {
                            offenders.add(describe(handler) + " reads "
                                    + parameter.getType().getSimpleName() + "." + field.getName()
                                    + " from the body");
                        }
                    }
                }
            }
        }

        assertThat(offenders.isEmpty()).as("these app-tier handlers let the caller name a user instead of taking it from the "
                        + "principal, which is how an IDOR gets in: " + offenders).isTrue();
    }

    private boolean isAppTier(HandlerMethod handler) {
        return handler.getBeanType().getPackageName().startsWith("com.pse")
                && APP_TIER_CONTROLLERS.contains(handler.getBeanType().getSimpleName());
    }

    private static boolean looksLikeIdentity(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "");
        return IDENTITY_NAMES.contains(normalized);
    }

    private static String describe(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
    }

    /** Every {@code "VERB /pattern"} that carries at least one path variable. */
    private Set<String> idTakingRoutes() {
        Set<String> routes = new TreeSet<>();

        for (MappedRoutes.Route route : MappedRoutes.application(handlerMapping)) {
            // A mapping declaring no verb has no single verb to name here, and none of the
            // id-taking routes is written that way.
            if (route.verb() != null && route.hasPathVariable()) {
                routes.add(route.verbAndPattern());
            }
        }

        return routes;
    }

    /** A real app session through the real login endpoints, so the session type is right. */
    private String studentSession(String email) throws Exception {
        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"loginToken\":\"%s\"}"
                                .formatted(email, codeDelivery.codeFor(email))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.authToken");
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
        lecture.setName("Lineare Algebra 1");
        lecture.setCode("LA1");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        return lectureRepository.saveAndFlush(lecture);
    }

    private Comment createComment(Student student, Lecture lecture) {
        Comment comment = new Comment();
        comment.setStudent(student);
        comment.setLecture(lecture);
        comment.setContent("A comment that belongs to somebody");
        comment.setStatus(ContentStatus.VISIBLE);
        return commentRepository.saveAndFlush(comment);
    }

    private Notification createNotification(Student recipient, Lecture lecture, Comment comment) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setLecture(lecture);
        notification.setOwnComment(comment);
        notification.setMessage("Somebody answered your comment");
        notification.setSeen(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepository.saveAndFlush(notification);
    }
}

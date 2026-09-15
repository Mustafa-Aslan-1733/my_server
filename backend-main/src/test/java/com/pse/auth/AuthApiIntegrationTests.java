package com.pse.auth;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.repository.AuditLogRepository;
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
import com.pse.social.repository.*;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestDeliveryConfig.CapturingLoginCodeDelivery;
import com.pse.shared.enums.UserStatus;
import com.pse.user.repository.StudentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;

@ApiIntegrationTest
class AuthApiIntegrationTests {

    @Autowired
    DatabaseReset databaseReset;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CapturingLoginCodeDelivery codeDelivery;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    TokenRepository tokenRepository;

    @Autowired
    OneTimePasswordRepository otpRepository;

    @Autowired
    AuthRateLimitBucketRepository rateLimitRepository;

    @Autowired
    AdminRepository adminRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    WarningRepository warningRepository;

    @Autowired
    AnswerReportRepository answerReportRepository;

    @Autowired
    AnswerVoteRepository answerVoteRepository;

    @Autowired
    AnswerRepository answerRepository;

    @Autowired
    CommentReportRepository commentReportRepository;

    @Autowired
    CommentVoteRepository commentVoteRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    BugReportRepository bugReportRepository;

    @Autowired
    RatingRepository ratingRepository;

    @Autowired
    LectureRepository lectureRepository;

    @Autowired
    ProfessorRepository professorRepository;


    @BeforeEach
    void cleanDatabase() {

        databaseReset.all();









        codeDelivery.clear();
    }


    @Test
    void studentCanRequestCodeAndLogin() throws Exception {

        mockMvc.perform(
                        post("/auth/request-login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"student@student.kit.edu"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Login code sent")
                );


        String code = codeDelivery.codeFor("student@student.kit.edu");
        assertThat(code).isNotNull();


        mockMvc.perform(
                        post("/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"student@student.kit.edu",
                                          "loginToken":"%s"
                                        }
                                        """.formatted(code))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Login successful"
                                )
                )
                .andExpect(
                        jsonPath("$.authToken")
                                .isString()
                );


        assertThat(studentRepository.count()).isEqualTo(1);

        assertThat(tokenRepository.count()).isEqualTo(1);
    }


    /**
     * Characterization of F-48. <b>This pins current behaviour, not intended behaviour</b>, and
     * the behaviour it pins is an authorization defect deliberately left open until a product
     * decision is taken -- {@code docs/TODO.md} item 39 carries the three costed options.
     *
     * <p>An account deletes itself, and then <b>an anonymous caller who knows nothing but the
     * address brings it back</b>. There is no {@code Authorization} header on the second
     * request and no proof of any kind that the caller holds the address: the status flip in
     * {@code LoginCodeService.requestLogin} happens when the code is <em>requested</em>, not
     * when it is entered. So the party who revives an account need not be the party who
     * deleted it.
     *
     * <p>What comes back with it is the point, rather than the row: an {@code ACTIVE} account
     * shows its real username on every comment and answer it ever wrote
     * ({@code SocialResponseMapper}) and its ratings return to the public averages
     * ({@code RatingAverages}). A third party can undo somebody's deletion and put their name
     * back into the app.
     *
     * <p>Written to be inverted, not deleted. If the decision is that reviving an account needs
     * the code to be entered, the status assertion becomes {@code DELETED} and the reactivation
     * moves to {@code SessionIssuer}.
     */
    @Test
    void anAnonymousLoginCodeRequestBringsBackAnAccountThatDeletedItself() throws Exception {

        String email = "revived@student.kit.edu";
        String token = registerAndLogIn(email);

        mockMvc.perform(patch("/account/deleteAccount")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(studentRepository.findByKitEmail(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.DELETED);

        // Both events are recorded, and both are in the administrative log rather than the
        // activity log: the reader who needs them is an operator, and the panel deleted its
        // activity-log screen. AuditActionScope's own note allows the split to follow the log
        // a reader needs rather than the actor type.
        AuditLog deletion = auditOf(AuditAction.USER_SELF_DELETED);
        assertThat(deletion.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(deletion.getTargetLabel()).contains(email);

        // No Authorization header, and nothing that proves the caller holds the address.
        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login code sent"));

        assertThat(studentRepository.findByKitEmail(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        // The entry says the account came back. It does not say who asked for it, because
        // nobody identified themselves -- the actor is the account itself, which is the
        // closest true statement available and is exactly what makes this worth reading.
        AuditLog reactivation = auditOf(AuditAction.USER_SELF_REACTIVATED);
        assertThat(reactivation.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(reactivation.getTargetLabel()).contains(email);
    }

    private AuditLog auditOf(AuditAction action) {
        return auditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == action)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + action + " entry was written"));
    }

    /** Registers an address and returns its bearer token. */
    private String registerAndLogIn(String email) throws Exception {
        mockMvc.perform(post("/auth/request-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(email)))
                .andExpect(status().isOk());

        String code = codeDelivery.codeFor(email);

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"loginToken\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return body.replaceAll(".*\"authToken\":\"([^\"]+)\".*", "$1");
    }

    private static String requestBody(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    @Test
    void wrongLoginCodeIsRejected() throws Exception {

        mockMvc.perform(
                        post("/auth/request-login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"student@student.kit.edu"
                                        }
                                        """)
                )
                .andExpect(status().isOk());


        mockMvc.perform(
                        post("/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"student@student.kit.edu",
                                          "loginToken":"WRONG"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                );


        assertThat(tokenRepository.count()).isEqualTo(0);
    }


    @Test
    void loggedInStudentCanValidateToken() throws Exception {

        String token = login("student@student.kit.edu");


        mockMvc.perform(
                        post("/auth/validate")
                                .header(
                                        "Authorization",
                                        bearer(token)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"student@student.kit.edu"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Correct auth-Token for email: "
                                                + "student@student.kit.edu"
                                )
                );
    }


    @Test
    void logoutRevokesToken() throws Exception {

        String rawToken = login("logout@student.kit.edu");


        Token stored = tokenRepository.findAll().getFirst();

        assertThat(stored.isRevoked()).isFalse();

        mockMvc.perform(
                        post("/auth/logout")
                                .header(
                                        "Authorization",
                                        bearer(rawToken)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Logged out successfully"
                                )
                );


        Token afterLogout = tokenRepository.findById(stored.getId()).orElseThrow();


        assertThat(afterLogout.isRevoked()).isTrue();


        assertThat(tokenRepository.count()).isEqualTo(1);


        mockMvc.perform(post("/auth/validate")
                                .header(
                                        "Authorization",
                                        bearer(rawToken)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"logout@student.kit.edu"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }


    @Test
    void logoutAllRevokesEverySession() throws Exception {

        String firstToken = login("multi@student.kit.edu");

        String secondToken = login("multi@student.kit.edu");

        assertThat(tokenRepository.count()).isEqualTo(2);

        mockMvc.perform(post("/auth/logout-all")
                                .header(
                                        "Authorization",
                                        bearer(firstToken)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Logged out from all devices (2 sessions)"
                                )
                );


        assertThat(tokenRepository.count()).isEqualTo(2);

        assertThat(tokenRepository
                        .findAll()
                        .stream()
                        .allMatch(Token::isRevoked)).isTrue();


        validateUnauthorized(firstToken, "multi@student.kit.edu");

        validateUnauthorized(secondToken, "multi@student.kit.edu");
    }


    @Test
    void invalidKitEmailIsRejected() throws Exception {

        mockMvc.perform(
                        post("/auth/request-login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"student@gmail.com"
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                );


        assertThat(otpRepository.count()).isEqualTo(0);
    }


    @Test
    void logoutRequiresAuthentication() throws Exception {

        mockMvc.perform(
                        post("/auth/logout")
                )
                .andExpect(
                        status().isUnauthorized()
                );


        mockMvc.perform(
                        post("/auth/logout-all")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }



    private String login(String email) throws Exception {

        mockMvc.perform(post("/auth/request-login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"%s"
                                        }
                                        """.formatted(email))
                )
                .andExpect(status().isOk());


        String code = codeDelivery.codeFor(email);


        String response = mockMvc.perform(
                                post("/auth/login")
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content("""
                                                {
                                                  "email":"%s",
                                                  "loginToken":"%s"
                                                }
                                                """.formatted(
                                                email,
                                                code
                                        ))
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                jsonPath("$.success")
                                        .value(true)
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();


        return com.jayway.jsonpath.JsonPath.read(response, "$.authToken");
    }


    private void validateUnauthorized(String token, String email) throws Exception {

        mockMvc.perform(post("/auth/validate")
                                .header(
                                        "Authorization",
                                        bearer(token)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email":"%s"
                                        }
                                        """.formatted(email))
                )
                .andExpect(status().isUnauthorized());
    }



    /**
     * F-5's last instance, and the sweep's own honesty check.
     *
     * <p>{@code IdentityService.validate} had three failure branches, all answering
     * {@code 200 {"success": false}}. Two of them cannot be reached over HTTP:
     * {@code BearerTokenAuthenticationFilter} answers 401 for any present-but-invalid
     * {@code Authorization} header before the controller runs, and {@code @NotBlank} on
     * {@code email} means a missing address is a 400 from Bean Validation. They were corrected
     * anyway so the method agrees with itself, but this is the one a client can actually
     * observe: a **valid** token together with an address that is not its owner's.
     */
    @Test
    void validatingAnAddressThatIsNotTheTokenOwnersIsRefusedRatherThanReported() throws Exception {
        String token = login("owner@student.kit.edu");

        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone-else@student.kit.edu\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Not logged in"));

        // The matching address is untouched.
        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@student.kit.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
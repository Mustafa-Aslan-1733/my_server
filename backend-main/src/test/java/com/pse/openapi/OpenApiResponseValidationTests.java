package com.pse.openapi;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.ValidationReport;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.shared.enums.SemesterSeason;
import com.pse.support.DatabaseReset;
import com.pse.moderation.repository.AdminRepository;
import com.pse.support.AdminSessions;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestGitLabConfig;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pse.support.ApiIntegrationTest;

/**
 * The other half of the contract: {@link OpenApiContractTests} proves the schema covers every
 * route, this proves the bodies actually match what it says about them.
 *
 * <p>The schema is fetched from the running context rather than read from a checked-in file,
 * so there is no generation step to forget and no copy to go stale.
 */
@ApiIntegrationTest
class OpenApiResponseValidationTests {

    private static final String ADMIN_EMAIL = "openapi-validate@student.kit.edu";
    private static final String STUDENT_EMAIL = "openapi-student@student.kit.edu";

    /**
     * Every readable GET worth sweeping, in the shape the validator wants them.
     */
    private static final List<String> READ_PATHS = List.of(
            "/health",
            "/admin/users",
            "/admin/system/status",
            "/admin/audit-logs",
            "/admin/activity-logs",
            "/admin/comments",
            "/admin/answers",
            "/admin/reports",
            "/admin/ratings",
            "/admin/data/lectures/all",
            "/admin/data/professor/all",
            "/admin/auth/me",
            "/data/lectures",
            "/data/professor",
            "/social/sync/comments");

    /**
     * Response fields the schema is knowingly wrong about, as {@code path -> JSON pointer}.
     *
     * <p>A springdoc limitation. {@code @Schema(nullable = true)} produces the right 3.1
     * type union on a scalar -- every cursor and {@code database.error} carry it and
     * validate -- but on a property that is a {@code $ref} it emits
     * {@code {"type":"null","$ref":…}}, which says the value can <em>only</em> be null.
     * Neither form is right, so the annotation is not used on {@code counts} or
     * {@code lastWrite}, and the mismatch is named here rather than hidden by skipping the
     * endpoint: everything else about {@code GET /admin/system/status} is still checked.
     *
     * <p>One entry, not two. {@code counts} has the same defect in the schema, but this
     * fixture cannot reach it: the database is up, so {@code counts} is an object and
     * matches, and it is only null when a read fails. Listing it anyway would be claiming
     * an observation this sweep does not make -- the same way an over-broad exemption list
     * passes by looking at less than it thinks.
     *
     * <p>Declared as an exact set, so a <em>new</em> mismatch fails even on a path that
     * already has a known one.
     */
    private static final Set<String> KNOWN_SCHEMA_GAPS = Set.of(
            "/admin/system/status /lastWrite"
    );

    @Autowired MockMvc mockMvc;
    @Autowired TestDeliveryConfig.CapturingLoginCodeDelivery codeDelivery;
    @Autowired DatabaseReset databaseReset;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private String adminToken;
    private OpenApiInteractionValidator validator;

    @BeforeEach
    void createAdminSessionAndLoadSchema() throws Exception {
        // Deterministic, and that is the whole point: this class used to validate whatever rows
        // the previously-run class happened to leave behind. Surefire orders classes by
        // filesystem, so which rows those were changed with the checkout -- and the two schema
        // gaps below went unseen for as long as the audit log happened to be empty here. F-19.
        databaseReset.all();
        adminToken = AdminSessions.createAdmin(
                ADMIN_EMAIL, studentRepository, adminRepository, tokenRepository);
        seedTheAuditLogWithItsNullableShapes();

        String schema = mockMvc.perform(get("/v3/api-docs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        validator = OpenApiInteractionValidator.createForInlineApiSpecification(schema).build();
    }

    /**
     * Three response fields are null by design, and each needs a row that makes it so -- an
     * unannotated nullable field is only a defect once something returns null in it.
     *
     * <ul>
     *   <li>A <b>refused request from a signed-in student</b> is audited against
     *       {@code AuditTargetType.ENDPOINT}, which is not a row and so carries no
     *       {@code target.id}. It has to be signed in: {@code AccessRefusalAuditor.record}
     *       returns early when there is no authenticated principal, so an anonymous 401 writes
     *       nothing at all and would leave this shape unexercised.</li>
     *   <li>A <b>revertible entry</b> reports {@code revertible: true}, and
     *       {@code Revertability.YES} leaves both {@code revertBlockedReason} and
     *       {@code revertedByAuditId} null.</li>
     * </ul>
     */
    private void seedTheAuditLogWithItsNullableShapes() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer " + studentSession()))
                .andExpect(status().isForbidden());

        Lecture lecture = new Lecture();
        lecture.setName("Algorithmen 1");
        lecture.setCode("IN0001");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        UUID lectureId = lectureRepository.saveAndFlush(lecture).getId();

        AuditLog log = new AuditLog();
        log.setActorType(AuditActorType.ADMIN);
        log.setActorId(UUID.randomUUID());
        log.setActorName("admin");
        log.setActorEmail(ADMIN_EMAIL);
        log.setActorRole("ADMIN");
        log.setAction(AuditAction.LECTURE_UPDATED);
        log.setTargetType(AuditTargetType.LECTURE);
        log.setTargetId(lectureId);
        log.setTargetLabel("IN0001 Algorithmen 1");
        log.setChanges(Map.of("name", Map.of("before", "Algorithmen 0", "after", "Algorithmen 1")));
        log.setMetadata(Map.of());
        log.setCreatedAt(Instant.now());
        auditLogRepository.saveAndFlush(log);
    }

    /** A real app session, through the real login endpoints. */
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

        return body.replaceAll(".*\"authToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void everyReadableResponseMatchesTheSchemaThatDescribesIt() throws Exception {
        Set<String> mismatches = new TreeSet<>();

        for (String path : READ_PATHS) {
            for (String pointer : mismatchedPointers(path)) {
                if (!KNOWN_SCHEMA_GAPS.contains(path + " " + pointer)) {
                    mismatches.add(path + " " + pointer);
                }
            }
        }

        assertThat(mismatches.isEmpty()).as("these response fields do not match the schema that describes them. Either "
                        + "the schema is wrong (annotate the field) or the response is: "
                        + mismatches).isTrue();
    }

    /**
     * The JSON pointers the validator objected to, e.g. {@code /nextCursor}.
     *
     * <p>Taken from {@link ValidationReport} rather than from the MockMvc matcher. The
     * matcher reports a failure by throwing, with everything useful inside the exception's
     * rendered message -- and reading it back out meant a regular expression over a
     * library's error text, which breaks the first time that text is reworded. The validator
     * hands the same information over as typed messages, so this asks it directly.
     */
    private List<String> mismatchedPointers(String path) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse();

        ValidationReport report = validator.validateResponse(
                path, Request.Method.GET, MockMvcResponse.of(response));

        return report.getMessages().stream()
                .filter(message -> message.getLevel() == ValidationReport.Level.ERROR)
                .map(message -> message.getContext()
                        .flatMap(ValidationReport.MessageContext::getPointers)
                        .map(ValidationReport.MessageContext.Pointers::getInstance)
                        // A message the report gives no pointer for is still a failure, and
                        // dropping it would let the sweep pass by ignoring what it cannot
                        // categorise.
                        .orElseGet(() -> "(no pointer) " + message.getMessage()))
                .toList();
    }
}

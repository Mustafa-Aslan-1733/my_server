package com.pse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.rating.model.Rating;
import com.pse.rating.repository.RatingRepository;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.UserStatus;
import com.pse.support.AdminSessions;
import com.pse.support.PostgresIntegrationTest;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cursor, <em>applied</em> -- against the database the application actually runs on.
 *
 * <p>{@code KeysetCursorCodecTests} covers producing and rejecting a cursor, and
 * {@code docs/test-plan.md} records under that class why it stops there: "producing a cursor
 * correctly and applying it correctly are separate things; the predicate and the page boundary
 * belong to the integration layer." This is that layer. Four endpoints paginate by keyset, over
 * two different column types, and until now none of their predicates had ever met a real
 * {@code timestamp(6)}:
 *
 * <table>
 *   <tr><th>Route</th><th>Cursor column</th></tr>
 *   <tr><td>{@code GET /admin/activity-logs}</td><td>{@code Instant}</td></tr>
 *   <tr><td>{@code GET /admin/audit-logs}</td><td>{@code Instant}</td></tr>
 *   <tr><td>{@code GET /admin/ratings}</td><td>{@code LocalDateTime}</td></tr>
 *   <tr><td>{@code GET /admin/users}</td><td>{@code LocalDateTime}, in two directions</td></tr>
 * </table>
 *
 * <p><b>What each test actually asserts.</b> Not "no row repeats" -- that is satisfied by a
 * walk which drops rows, and dropping rows is the same defect wearing the other face. Every
 * test pages the whole list one row at a time and asserts the resulting sequence equals the
 * ordering the database itself produces, element for element. A repeat, a skip and a wrong
 * boundary are then all one assertion.
 *
 * <p><b>Why rows are forced to share a microsecond.</b> The tiebreaker branch of every one of
 * these predicates -- {@code createdAt = cursor AND id < cursor.id} -- is unreachable unless
 * two rows land in the same microsecond, and left untested it is the branch that decides
 * whether a page boundary repeats or skips. It is also the exact shape BUG-1 describes.
 * {@code @CreationTimestamp} overwrites whatever an entity carries, so the students and
 * ratings are stamped afterwards through {@link JdbcTemplate}; {@code AuditLog.@PrePersist}
 * only fills a null, so those rows can simply be stamped before the insert.
 *
 * <p>Shares its Spring context with every other {@link PostgresIntegrationTest}; see that
 * annotation for why that is not optional.
 */
@PostgresIntegrationTest
class CursorPaginationPostgresTests {

    /** Enough to force several page boundaries without making the fixture the subject. */
    private static final int ROWS = 5;

    /**
     * A walk that never ends is a real possible defect -- it is what a cursor that fails to
     * advance looks like from outside -- so the loop is capped and the cap fails loudly rather
     * than hanging the job.
     */
    private static final int MAX_PAGES = ROWS * 3;

    /**
     * Its own, rather than the context's: these tests only read a response body, and the
     * application does not expose an {@code ObjectMapper} bean to autowire.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired AuditLogRepository auditLogRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;
    @Autowired RatingRepository ratingRepository;
    @Autowired LectureRepository lectureRepository;

    private String adminToken;

    @BeforeEach
    void resetAndSignIn() {
        // Ratings before students: the row holds a student_id, and AdminSessions empties the
        // students table.
        ratingRepository.deleteAll();
        lectureRepository.deleteAll();
        auditLogRepository.deleteAll();
        adminToken = AdminSessions.createAdmin(
                "admin@student.kit.edu", studentRepository, adminRepository, tokenRepository);
    }

    // ---------------------------------------------------------------- activity + audit logs

    @Test
    void activityLogPagesEveryRowExactlyOnceAndInOrder() throws Exception {
        seedAuditLogs(AuditAction.COMMENT_CREATED, distinctInstants());

        assertThat(pageThrough("/admin/activity-logs", "activityLogs", Map.of()))
                .isEqualTo(auditLogIdsInKeysetOrder());
    }

    @Test
    void activityLogResumesCorrectlyWhenEveryRowSharesAMicrosecond() throws Exception {
        seedAuditLogs(AuditAction.COMMENT_CREATED, sameInstant());

        assertThat(pageThrough("/admin/activity-logs", "activityLogs", Map.of()))
                .as("with one microsecond for all %d rows the predicate has only the id "
                        + "tiebreaker left to separate them", ROWS)
                .isEqualTo(auditLogIdsInKeysetOrder());
    }

    @Test
    void auditLogPagesEveryRowExactlyOnceAndInOrder() throws Exception {
        seedAuditLogs(AuditAction.USER_UPDATED, distinctInstants());

        assertThat(pageThrough("/admin/audit-logs", "auditLogs", Map.of()))
                .isEqualTo(auditLogIdsInKeysetOrder());
    }

    @Test
    void auditLogResumesCorrectlyWhenEveryRowSharesAMicrosecond() throws Exception {
        seedAuditLogs(AuditAction.USER_UPDATED, sameInstant());

        assertThat(pageThrough("/admin/audit-logs", "auditLogs", Map.of()))
                .isEqualTo(auditLogIdsInKeysetOrder());
    }

    // ------------------------------------------------------------------------------ ratings

    @Test
    void ratingsPageEveryRowExactlyOnceAndInOrder() throws Exception {
        seedRatings(distinctLocalDateTimes());

        assertThat(pageThrough("/admin/ratings", "ratings", Map.of()))
                .isEqualTo(ratingIdsInKeysetOrder());
    }

    @Test
    void ratingsResumeCorrectlyWhenEveryRowSharesAMicrosecond() throws Exception {
        seedRatings(sameLocalDateTime());

        assertThat(pageThrough("/admin/ratings", "ratings", Map.of()))
                .as("this route decodes the cursor through Cursor.createdAtLocal(), so the "
                        + "boundary is a LocalDateTime compared against a timestamp(6)")
                .isEqualTo(ratingIdsInKeysetOrder());
    }

    // -------------------------------------------------------------------------------- users

    @Test
    void usersPageEveryRowExactlyOnceAndInOrder() throws Exception {
        seedStudents(distinctLocalDateTimes());

        assertThat(pageThrough("/admin/users", "users", Map.of()))
                .isEqualTo(studentIdsInKeysetOrder(Sort.Direction.DESC));
    }

    @Test
    void usersResumeCorrectlyWhenEveryRowSharesAMicrosecond() throws Exception {
        seedStudents(sameLocalDateTime());

        assertThat(pageThrough("/admin/users", "users", Map.of()))
                .isEqualTo(studentIdsInKeysetOrder(Sort.Direction.DESC));
    }

    /**
     * The other half of {@code ModerationUserService.cursorPredicate}. Ascending order swaps
     * every comparison in it for its mirror, and no descending test touches that branch --
     * which is how a keyset predicate ends up correct in one direction only.
     */
    @Test
    void usersPageEveryRowExactlyOnceWhenSortedOldestFirst() throws Exception {
        seedStudents(distinctLocalDateTimes());

        assertThat(pageThrough("/admin/users", "users", Map.of("sort", "oldest")))
                .isEqualTo(studentIdsInKeysetOrder(Sort.Direction.ASC));
    }

    @Test
    void usersResumeOldestFirstWhenEveryRowSharesAMicrosecond() throws Exception {
        seedStudents(sameLocalDateTime());

        assertThat(pageThrough("/admin/users", "users", Map.of("sort", "oldest")))
                .isEqualTo(studentIdsInKeysetOrder(Sort.Direction.ASC));
    }

    // ------------------------------------------------------------------------------- paging

    /**
     * Walks the whole list with {@code limit=1}, following {@code nextCursor} until it is null,
     * and returns the ids in the order they were served.
     */
    private List<String> pageThrough(String path, String arrayField, Map<String, String> filters)
            throws Exception {
        List<String> served = new ArrayList<>();
        String cursor = null;

        for (int page = 0; page <= MAX_PAGES; page++) {
            MockHttpServletRequestBuilder request = get(path)
                    .header("Authorization", "Bearer " + adminToken)
                    .param("limit", "1");
            filters.forEach(request::param);
            if (cursor != null) {
                request.param("cursor", cursor);
            }

            String body = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode root = objectMapper.readTree(body);
            for (JsonNode row : root.get(arrayField)) {
                served.add(row.get("id").asText());
            }

            JsonNode next = root.get("nextCursor");
            if (next == null || next.isNull()) {
                return served;
            }
            cursor = next.asText();
        }

        throw new AssertionError(
                path + " served " + served.size() + " rows in " + MAX_PAGES + " pages without "
                        + "reaching the end -- the cursor is not advancing past a row.");
    }

    // ----------------------------------------------------------------------------- fixtures

    /**
     * Ids in the order the endpoint's own {@code (createdAt DESC, id DESC)} sort produces.
     *
     * <p>Each of these asserts its own size before returning. Without that a fixture which
     * silently wrote nothing would leave the walk and the expectation both empty, and
     * {@code isEqualTo} would pass on two empty lists -- a green test asserting nothing at all.
     */
    private List<String> auditLogIdsInKeysetOrder() {
        List<String> ids = auditLogRepository
                .findAll(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
                .stream()
                .map(log -> log.getId().toString())
                .toList();
        assertThat(ids).as("the seeded audit log rows must be in the table").hasSize(ROWS);
        return ids;
    }

    private List<String> ratingIdsInKeysetOrder() {
        List<String> ids = ratingRepository
                .findAll(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
                .stream()
                .map(rating -> rating.getId().toString())
                .toList();
        assertThat(ids).as("the seeded ratings must be in the table").hasSize(ROWS);
        return ids;
    }

    /** The admin's own account is listed too, which is why this expects one row more. */
    private List<String> studentIdsInKeysetOrder(Sort.Direction direction) {
        List<String> ids = studentRepository
                .findAll(Sort.by(
                        new Sort.Order(direction, "createdAt"),
                        new Sort.Order(direction, "id")))
                .stream()
                .filter(student -> student.getStatus() != UserStatus.DELETED)
                .map(student -> student.getId().toString())
                .toList();
        assertThat(ids)
                .as("the seeded students plus the administrator this suite signs in as")
                .hasSize(ROWS + 1);
        return ids;
    }

    private void seedAuditLogs(AuditAction action, List<Instant> stamps) {
        for (Instant stamp : stamps) {
            AuditLog log = new AuditLog();
            log.setActorType(AuditActorType.USER);
            log.setActorId(UUID.randomUUID());
            log.setActorName("ada");
            log.setActorEmail("ada@kit.edu");
            log.setActorRole("STUDENT");
            log.setAction(action);
            log.setTargetType(AuditTargetType.COMMENT);
            log.setTargetId(UUID.randomUUID());
            log.setTargetLabel("IN0001 — Algorithmen 1");
            log.setChanges(Map.of());
            log.setMetadata(Map.of());
            // @PrePersist only fills a null, so this survives the insert.
            log.setCreatedAt(stamp);
            auditLogRepository.saveAndFlush(log);
        }
    }

    private void seedStudents(List<LocalDateTime> stamps) {
        for (int index = 0; index < stamps.size(); index++) {
            Student student = new Student();
            student.setKitEmail("student" + index + "@student.kit.edu");
            student.setUsername("student" + index);
            student.setStatus(UserStatus.ACTIVE);
            student.setEmailVerifiedAt(LocalDateTime.now());
            stamp("students", studentRepository.saveAndFlush(student).getId(), stamps.get(index));
        }
    }

    /**
     * One lecture and one rating per student: {@code ratings} is unique on
     * {@code (student_id, lecture_id)}, so several ratings need several students.
     */
    private void seedRatings(List<LocalDateTime> stamps) {
        Lecture lecture = new Lecture();
        lecture.setName("Algorithmen 1");
        lecture.setCode("IN0001");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        Lecture saved = lectureRepository.saveAndFlush(lecture);

        seedStudents(stamps);
        List<Student> students = studentRepository.findAll().stream()
                .filter(student -> student.getUsername().startsWith("student"))
                .toList();

        for (int index = 0; index < students.size(); index++) {
            Rating rating = new Rating();
            rating.setStudent(students.get(index));
            rating.setLecture(saved);
            stamp("ratings", ratingRepository.saveAndFlush(rating).getId(), stamps.get(index));
        }
    }

    /**
     * Sets {@code created_at} after the insert. {@code @CreationTimestamp} overwrites whatever
     * the entity carried, so a test that needs a chosen instant has to write it as SQL.
     */
    private void stamp(String table, UUID id, LocalDateTime at) {
        jdbcTemplate.update(
                "update " + table + " set created_at = ? where id = ?", Timestamp.valueOf(at), id);
    }

    private static List<Instant> distinctInstants() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        List<Instant> stamps = new ArrayList<>();
        for (int index = 0; index < ROWS; index++) {
            stamps.add(base.plusSeconds(index));
        }
        return stamps;
    }

    /** One microsecond for every row, so only the id can order them. */
    private static List<Instant> sameInstant() {
        Instant shared = Instant.parse("2026-01-01T00:00:00.123456Z");
        List<Instant> stamps = new ArrayList<>();
        for (int index = 0; index < ROWS; index++) {
            stamps.add(shared);
        }
        return stamps;
    }

    private static List<LocalDateTime> distinctLocalDateTimes() {
        LocalDateTime base = LocalDateTime.parse("2026-01-01T00:00:00");
        List<LocalDateTime> stamps = new ArrayList<>();
        for (int index = 0; index < ROWS; index++) {
            stamps.add(base.plusSeconds(index));
        }
        return stamps;
    }

    private static List<LocalDateTime> sameLocalDateTime() {
        LocalDateTime shared = LocalDateTime.parse("2026-01-01T00:00:00.123456");
        List<LocalDateTime> stamps = new ArrayList<>();
        for (int index = 0; index < ROWS; index++) {
            stamps.add(shared);
        }
        return stamps;
    }
}

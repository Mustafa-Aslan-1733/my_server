package com.pse.audit;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.shared.enums.SemesterSeason;
import com.pse.support.AdminSessions;
import com.pse.support.PostgresIntegrationTest;
import com.pse.user.repository.StudentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one place the two schemas disagree.
 *
 * <p>{@code src/test/resources/db/migration/h2} and
 * {@code src/main/resources/db/migration/postgresql} are the same file except for two lines:
 *
 * <pre>
 *   audit_logs.changes   json  (H2)  ->  jsonb (PostgreSQL)
 *   audit_logs.metadata  json  (H2)  ->  jsonb (PostgreSQL)
 * </pre>
 *
 * <p>That is not a cosmetic difference. {@code json} stores the document as text and hands it
 * back byte for byte; {@code jsonb} parses it into a binary form, which reorders object keys,
 * collapses duplicates and normalises numbers. Every revert handler reads its {@code before}
 * values back out of {@code changes} — so the entire revert feature rests on a round trip that
 * the H2 suite, on the other column type, cannot vouch for.
 *
 * <p>{@code RevertValues} already says out loud that values "come back as whatever Jackson
 * chose to rebuild them into", and is representation-insensitive because of it. These tests
 * pin what that actually amounts to against the real column, so the next person to widen the
 * comparison can see what they are widening it for.
 */
@PostgresIntegrationTest
class AuditJsonbRoundTripPostgresTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;

    private String adminToken;

    @BeforeEach
    void resetAndSignIn() {
        auditLogRepository.deleteAll();
        lectureRepository.deleteAll();
        adminToken = AdminSessions.createAdmin(
                "admin@student.kit.edu", studentRepository, adminRepository, tokenRepository);
    }

    /**
     * The premise of this whole class, asserted rather than assumed. If the production
     * migration ever produced {@code json} here, every test below would still pass and would
     * silently be testing the H2 shape against a PostgreSQL server.
     */
    @Test
    void theProductionMigrationGivesThoseTwoColumnsTheBinaryJsonType() {
        List<String> types = jdbcTemplate.queryForList(
                "select data_type from information_schema.columns "
                        + "where table_name = 'audit_logs' and column_name in ('changes', 'metadata') "
                        + "order by column_name",
                String.class);

        assertThat(types)
                .as("the H2 baseline declares these as json; this is the difference the class exists for")
                .containsExactly("jsonb", "jsonb");
    }

    /**
     * Nested objects, arrays, nulls, an empty object, non-ASCII text and a quote inside a key
     * — written, evicted from the persistence context, and read back off the server.
     */
    @Test
    void richDocumentsSurviveTheRoundTripThroughJsonb() {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("name", change("Algorithmen 1", "Algorithmen 2"));
        changes.put("semesterYear", change(2025, 2026));
        changes.put("active", change(true, false));
        changes.put("professors", change(List.of("a", "b"), List.of()));
        changes.put("code", change(null, "IN0001"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("api", "ADMIN");
        metadata.put("note", "Ünïcödé — ärger, 名前, emoji 🎓");
        metadata.put("a \"quoted\" key", "value");
        metadata.put("empty", Map.of());
        metadata.put("list", List.of(1, 2, 3));
        metadata.put("nested", Map.of("deep", Map.of("deeper", "value")));
        metadata.put("nullValue", null);

        UUID id = auditLogRepository.saveAndFlush(auditLog(changes, metadata)).getId();
        AuditLog reloaded = reload(id);

        assertThat(reloaded.getChanges()).isEqualTo(changes);
        assertThat(reloaded.getMetadata()).isEqualTo(metadata);

        // Read back through PostgreSQL's own jsonb operator rather than through Hibernate, so
        // the assertions above cannot be satisfied by a value that never left the JVM.
        assertThat(jdbcTemplate.queryForObject(
                "select metadata->>'note' from audit_logs where id = ?", String.class, id))
                .isEqualTo("Ünïcödé — ärger, 名前, emoji 🎓");
        assertThat(jdbcTemplate.queryForObject(
                "select changes->'name'->>'before' from audit_logs where id = ?", String.class, id))
                .isEqualTo("Algorithmen 1");
    }

    /**
     * A revert, end to end, whose {@code before} value has been through {@code jsonb}.
     *
     * <p>The assertion is that the lecture's name is restored, not merely that the JSON came
     * back intact: that is the property the feature actually promises, and it fails if any
     * layer between the column and {@code RevertValues.asString} loses the value.
     */
    @Test
    void aRevertRestoresTheValueItReadsBackOutOfJsonb() throws Exception {
        Lecture lecture = new Lecture();
        lecture.setName("Algorithmen 2");
        lecture.setCode("IN0001");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        UUID lectureId = lectureRepository.saveAndFlush(lecture).getId();

        AuditLog log = auditLog(
                Map.of("name", change("Algorithmen 1", "Algorithmen 2")),
                Map.of("api", "ADMIN"));
        log.setAction(AuditAction.LECTURE_UPDATED);
        log.setTargetType(AuditTargetType.LECTURE);
        log.setTargetId(lectureId);
        // Inside the revert window, which is measured from this timestamp.
        log.setCreatedAt(Instant.now());
        UUID auditId = auditLogRepository.saveAndFlush(log).getId();

        mockMvc.perform(post("/admin/audit-logs/" + auditId + "/revert")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertThat(lectureRepository.findById(lectureId).orElseThrow().getName())
                .as("the name came out of the jsonb column and back through updateLecture")
                .isEqualTo("Algorithmen 1");
    }

    /** Reads the row back off the server rather than out of the persistence context. */
    private AuditLog reload(UUID id) {
        auditLogRepository.flush();
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> change(Object before, Object after) {
        Map<String, Object> half = new LinkedHashMap<>();
        half.put("before", before);
        half.put("after", after);
        return half;
    }

    private static AuditLog auditLog(Map<String, Object> changes, Map<String, Object> metadata) {
        AuditLog log = new AuditLog();
        log.setActorType(AuditActorType.ADMIN);
        log.setActorId(UUID.randomUUID());
        log.setActorName("admin");
        log.setActorEmail("admin@student.kit.edu");
        log.setActorRole("ADMIN");
        log.setAction(AuditAction.LECTURE_UPDATED);
        log.setTargetType(AuditTargetType.LECTURE);
        log.setTargetId(UUID.randomUUID());
        log.setTargetLabel("IN0001 — Algorithmen 1");
        log.setChanges(changes);
        log.setMetadata(metadata);
        return log;
    }
}

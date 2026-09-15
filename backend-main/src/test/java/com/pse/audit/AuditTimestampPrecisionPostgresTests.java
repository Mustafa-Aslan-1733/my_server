package com.pse.audit;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-1 against the database that actually runs in production.
 *
 * <p>{@link AuditTimestampPrecisionTests} settles everything about this that does not need a
 * server: {@code Instant.now()} carries nanoseconds here, {@code created_at} is
 * {@code timestamp(6)}, and a cursor taken from a reloaded row equals that row. What it
 * cannot settle is what the *server* does with the digits it cannot store, because it runs
 * on H2 -- and H2 was measured <b>rounding</b> to the nearest microsecond, which moves an
 * instant forward as often as back.
 *
 * <p>That difference is the whole reason to run this here. If PostgreSQL truncates where H2
 * rounds, then the H2 suite is not modelling the production keyset boundary, and every
 * pagination test in it is one step further from the thing it claims to check.
 *
 * <p>Skipped unless {@code POSTGRES_SMOKE_JDBC_URL} is set, so a developer machine with no
 * database runs the rest of the suite unaffected -- that condition comes with
 * {@link PostgresIntegrationTest}, along with the context this shares with every other class
 * in this layer.
 */
@PostgresIntegrationTest
class AuditTimestampPrecisionPostgresTests {

    @Autowired AuditLogRepository auditLogRepository;
    @Autowired EntityManager entityManager;

    /**
     * What PostgreSQL does with a value finer than the column: truncate, round, or reject.
     * The answer is recorded in the failure message rather than assumed, so the first CI run
     * of this test reports the fact even if the assertion below holds.
     */
    @Test
    @Transactional
    void storedInstantsAreMicrosecondsAndStayWithinOneOfTheInstantStamped() {
        AuditLog written = auditLogRepository.saveAndFlush(auditLog());
        Instant asWritten = written.getCreatedAt();
        UUID id = written.getId();
        entityManager.clear();

        Instant reloaded = auditLogRepository.findById(id).orElseThrow().getCreatedAt();

        assertThat(reloaded.getNano() % 1000)
                .as("timestamp(6) cannot hold anything below a microsecond")
                .isZero();
        assertThat(Duration.between(asWritten, reloaded).abs())
                .as("stamped %s, stored %s -- PostgreSQL moved it by more than a microsecond, "
                        + "which no rounding or truncation would do", asWritten, reloaded)
                .isLessThan(Duration.ofNanos(1000));
    }

    /**
     * Which way PostgreSQL goes, pinned with a value chosen for the answer rather than with
     * whatever the clock happened to produce.
     *
     * <p>It <b>rounds</b>, and the hypothesis assumed it truncated. That is not a detail:
     * truncation can only move an instant backwards, so the hypothesis reasoned about a
     * stored row that always sorts before the value the application stamped. Rounding moves
     * it in either direction, and half the time forwards -- so the mechanism BUG-1 describes
     * could not have been derived from the real behaviour of this column.
     *
     * <p>{@code .0000009} is a microsecond's worth of nanoseconds minus one, so a database
     * that truncated would answer {@code .000000} and this test would go red on the first
     * PostgreSQL version that changed its mind.
     */
    @Test
    @Transactional
    void postgresRoundsTheDigitsItCannotStoreRatherThanTruncatingThem() {
        AuditLog log = auditLog();
        log.setCreatedAt(Instant.parse("2026-01-01T00:00:00.000000900Z"));

        UUID id = auditLogRepository.saveAndFlush(log).getId();
        entityManager.clear();

        assertThat(auditLogRepository.findById(id).orElseThrow().getCreatedAt())
                .as("a truncating database would answer .000000 here")
                .isEqualTo(Instant.parse("2026-01-01T00:00:00.000001Z"));
    }

    /**
     * The bug report itself, reproduced or not: page through the audit log one row at a
     * time and check that no row is served twice.
     *
     * <p>Two rows written inside one transaction is the shape the hypothesis names, because
     * it is where two timestamps are most likely to land in the same microsecond and force
     * the keyset predicate onto its id tiebreaker.
     */
    @Test
    void pagingOneRowAtATimeNeverServesTheSameRowTwice() {
        auditLogRepository.deleteAll();
        auditLogRepository.saveAll(List.of(auditLog(), auditLog(), auditLog()));

        List<UUID> seen = new java.util.ArrayList<>();
        Instant cursorCreatedAt = null;
        UUID cursorId = null;

        for (int page = 0; page < 5; page++) {
            List<AuditLog> rows = pageAfter(cursorCreatedAt, cursorId);
            if (rows.isEmpty()) {
                break;
            }
            AuditLog last = rows.getLast();
            seen.add(last.getId());
            cursorCreatedAt = last.getCreatedAt();
            cursorId = last.getId();
        }

        assertThat(seen)
                .as("a row served on two pages is the defect the cursor bug report describes")
                .doesNotHaveDuplicates()
                .hasSize(3);
    }

    /**
     * The service's keyset predicate, stated in JPQL so this test owns its own query rather
     * than depending on the listing's filters, sorting and DTO mapping. It is the same
     * shape: strictly older, or the same instant and a smaller id.
     */
    private List<AuditLog> pageAfter(Instant createdAt, UUID id) {
        if (createdAt == null) {
            return entityManager.createQuery(
                            "select l from AuditLog l order by l.createdAt desc, l.id desc",
                            AuditLog.class)
                    .setMaxResults(1)
                    .getResultList();
        }
        return entityManager.createQuery(
                        "select l from AuditLog l "
                                + "where l.createdAt < :createdAt "
                                + "   or (l.createdAt = :createdAt and l.id < :id) "
                                + "order by l.createdAt desc, l.id desc",
                        AuditLog.class)
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
    }

    private static AuditLog auditLog() {
        AuditLog log = new AuditLog();
        log.setActorType(AuditActorType.USER);
        log.setActorId(UUID.randomUUID());
        log.setActorName("ada");
        log.setActorEmail("ada@kit.edu");
        log.setActorRole("STUDENT");
        log.setAction(AuditAction.COMMENT_CREATED);
        log.setTargetType(AuditTargetType.COMMENT);
        log.setTargetId(UUID.randomUUID());
        log.setTargetLabel("IN0001 — Algorithmen 1");
        log.setChanges(Map.of());
        log.setMetadata(Map.of());
        return log;
    }
}

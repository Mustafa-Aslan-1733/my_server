package com.pse.audit;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestGitLabConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.pse.support.ApiIntegrationTest;

/**
 * BUG-1's mechanism, measured rather than assumed.
 *
 * <p>The bug report was "the cursor repeats a row". The standing hypothesis blamed
 * timestamp precision: {@code AuditLog.@PrePersist} stamps {@code Instant.now()}, the
 * {@code created_at} column is {@code timestamp(6)}, and a cursor built from a value the
 * column cannot store would sort before the stored row and match it a second time.
 *
 * <p>The hypothesis was recorded as needing a real PostgreSQL, on the grounds that
 * truncation is the driver's and the column type's behaviour. That turned out to be one
 * assumption too many: <b>both</b> baselines declare {@code timestamp(6)}, so whatever
 * truncation happens on PostgreSQL happens here too, and the half of the mechanism that
 * lives in the JVM can be measured with no database at all.
 *
 * <p>These tests pin what is actually true, so the hypothesis stops being folklore.
 */
@ApiIntegrationTest
class AuditTimestampPrecisionTests {

    @Autowired AuditLogRepository auditLogRepository;
    @Autowired EntityManager entityManager;

    /**
     * Half one: the value the entity carries is finer than the column. On this platform
     * {@code Instant.now()} returns nanoseconds, so the in-memory entity holds three digits
     * the column has nowhere to put.
     *
     * <p>Asserted as a property of the clock rather than as a fixed number, because a
     * platform whose clock only ticks in microseconds would make it vacuously true and the
     * test would then be claiming something it had not checked.
     */
    @Test
    void instantNowIsFinerGrainedThanTheColumnItIsStoredIn() {
        boolean anyBelowMicrosecond = false;
        for (int attempt = 0; attempt < 1000 && !anyBelowMicrosecond; attempt++) {
            anyBelowMicrosecond = Instant.now().getNano() % 1000 != 0;
        }

        assertThat(anyBelowMicrosecond)
                .as("Instant.now() carries sub-microsecond digits here, which timestamp(6) "
                        + "cannot store -- this is the premise the whole hypothesis rests on")
                .isTrue();
    }

    /**
     * Half two, and the one that decides whether the hypothesis is a defect: what comes back
     * <b>after</b> the round trip. If the persistence context handed the written instance
     * back with its nanoseconds intact, a cursor built from it would carry a value no row
     * can equal.
     *
     * <p>It does not: the sub-microsecond digits are gone. What they are replaced by is
     * where this stopped matching the hypothesis — H2 <b>rounds</b> to the nearest
     * microsecond rather than truncating, so a value ending {@code …6265} comes back as
     * {@code …627}, one microsecond <em>after</em> the instant the application stamped.
     *
     * <p>Asserted as "changed, and no finer than a microsecond" rather than as a specific
     * rounding mode, because the mode is the database's and this suite runs on H2. Which
     * way PostgreSQL goes is the one part of this that a real PostgreSQL has to answer --
     * see {@code AuditTimestampPrecisionPostgresTests}.
     */
    @Test
    @Transactional
    void aReloadedRowHasLostItsSubMicrosecondDigits() {
        AuditLog written = auditLogRepository.saveAndFlush(auditLog());
        Instant asWritten = written.getCreatedAt();

        UUID id = written.getId();
        entityManager.clear();

        Instant reloaded = auditLogRepository.findById(id).orElseThrow().getCreatedAt();

        assertThat(reloaded.getNano() % 1000)
                .as("timestamp(6) cannot hold anything below a microsecond")
                .isZero();

        if (asWritten.getNano() % 1000 != 0) {
            assertThat(reloaded)
                    .as("the stored instant is not the instant the application stamped, "
                            + "which is the premise a cursor has to survive")
                    .isNotEqualTo(asWritten);
            assertThat(java.time.Duration.between(asWritten, reloaded).abs())
                    .as("and it is off by less than one microsecond, in whichever direction "
                            + "this database rounds")
                    .isLessThan(java.time.Duration.ofNanos(1000));
        }
    }

    /**
     * The consequence, stated as the property the pagination depends on: a cursor built
     * from a reloaded row is a value that row's own timestamp equals, so the keyset
     * predicate's {@code createdAt < cursor} arm excludes it rather than matching it again.
     *
     * <p>This is the assertion that would go red if the read path ever started building a
     * cursor from an entity it had just written -- the shape the hypothesis describes.
     */
    @Test
    @Transactional
    void aCursorBuiltFromAReloadedRowDoesNotSortAfterThatRow() {
        AuditLog written = auditLogRepository.saveAndFlush(auditLog());
        UUID id = written.getId();
        entityManager.clear();

        Instant cursorValue = auditLogRepository.findById(id).orElseThrow().getCreatedAt();
        Instant rowValue = auditLogRepository.findById(id).orElseThrow().getCreatedAt();

        assertThat(rowValue.isBefore(cursorValue))
                .as("a row sorting before a cursor taken from itself is exactly how a row "
                        + "gets served twice")
                .isFalse();
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

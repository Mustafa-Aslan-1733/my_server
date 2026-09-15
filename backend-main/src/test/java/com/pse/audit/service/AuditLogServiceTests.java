package com.pse.audit.service;

import com.pse.audit.dto.AuditLogPageResponse;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActionScope;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.audit.revert.AuditRevertService;
import com.pse.shared.error.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The audit listing's request handling, which had no unit test of its own.
 *
 * <p>{@code docs/TODO.md} listed this service under "covered only through the API", and that is
 * the wrong place for it: what {@code getLogs} mostly does before it reaches the database is
 * parse and reject nine query parameters, and each rejection is a status code the panel depends
 * on. Driving those through MockMvc costs a Spring context per case and says nothing about which
 * argument was at fault.
 *
 * <p><b>Deliberately not covered here:</b> the {@code Specification} lambda. It is Criteria API
 * assembly, and the only way to run it without a database is to hand it a mocked {@code Root},
 * {@code CriteriaQuery} and {@code CriteriaBuilder} and then assert which builder methods were
 * called -- which asserts the implementation rather than the behaviour, and would go red on any
 * rewrite that produced the same query. The predicates are covered where they mean something:
 * {@code CursorPaginationPostgresTests} pages all four cursor endpoints against a real
 * PostgreSQL. This matches the position {@code docs/test-plan.md} already takes on Criteria
 * predicates.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceTests {

    @Mock AuditLogRepository repository;
    @Mock AuditCursorCodec cursorCodec;
    @Mock AuditRevertService revertService;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository, cursorCodec, revertService);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(revertService.describe(any())).thenReturn(Map.of());
    }

    // ------------------------------------------------------------------------------- limit

    @Test
    void anAbsentLimitFallsBackToTheDefaultPageSize() {
        assertThat(getLogs(null).success()).isTrue();
        assertThat(getLogs("").success()).isTrue();
        assertThat(getLogs("  ").success()).isTrue();
    }

    @Test
    void aLimitOutsideOneToAHundredIsRefused() {
        assertThatThrownBy(() -> getLogs("0")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> getLogs("101")).isInstanceOf(ApiException.class);
    }

    @Test
    void aLimitThatIsNotANumberIsRefusedWithFourHundred() {
        assertThatThrownBy(() -> getLogs("many"))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void theBoundaryLimitsAreAccepted() {
        assertThat(getLogs("1").success()).isTrue();
        assertThat(getLogs("100").success()).isTrue();
    }

    // ------------------------------------------------------------------------------ filters

    @Test
    void anActorIdThatIsNotAUuidIsRefused() {
        assertThatThrownBy(() -> call(null, null, "not-a-uuid", null, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid actor id");
    }

    @Test
    void anEmptyActorIdIsTreatedAsNoFilterRatherThanAsBadInput() {
        assertThat(call(null, null, "", null, null, null, null, null).success()).isTrue();
        assertThat(call(null, null, "   ", null, null, null, null, null).success()).isTrue();
    }

    @Test
    void anUnknownActorTypeIsRefused() {
        assertThatThrownBy(() -> call(null, null, null, "ROBOT", null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid audit actor type");
    }

    @Test
    void anUnknownTargetTypeIsRefused() {
        assertThatThrownBy(() -> call(null, null, null, null, null, "PLANET", null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid audit target type");
    }

    @Test
    void anUnknownActionIsRefused() {
        assertThatThrownBy(() -> call(null, null, null, null, "DANCED", null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid audit action");
    }

    /**
     * An action belonging to the other page is refused rather than quietly matching nothing.
     * Returning an empty page would read as "no such events" instead of "wrong endpoint".
     */
    @Test
    void anActionFromTheOtherScopeIsRefusedRatherThanSilentlyMatchingNothing() {
        assertThatThrownBy(() -> call(
                AuditActionScope.ADMINISTRATIVE, null, null, null, "COMMENT_CREATED", null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid audit action");

        assertThatThrownBy(() -> call(
                AuditActionScope.ACTIVITY, null, null, null, "USER_UPDATED", null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid audit action");
    }

    @Test
    void anActionFromTheRequestedScopeIsAccepted() {
        assertThat(call(AuditActionScope.ACTIVITY, null, null, null, "COMMENT_CREATED", null, null, null)
                .success()).isTrue();
    }

    // -------------------------------------------------------------------------- date range

    @Test
    void anUnparseableDateIsRefused() {
        assertThatThrownBy(() -> call(null, null, null, null, null, null, "yesterday", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid audit date range");
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> call(
                null, null, null, null, null, null,
                "2026-02-01T00:00:00Z", "2026-01-01T00:00:00Z"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid audit date range");
    }

    @Test
    void aRangeInOrderIsAccepted() {
        assertThat(call(
                null, null, null, null, null, null,
                "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z").success()).isTrue();
    }

    @Test
    void oneEndOfTheRangeOnItsOwnIsAccepted() {
        assertThat(call(null, null, null, null, null, null, "2026-01-01T00:00:00Z", null)
                .success()).isTrue();
        assertThat(call(null, null, null, null, null, null, null, "2026-02-01T00:00:00Z")
                .success()).isTrue();
    }

    // ------------------------------------------------------------------------------ search

    @Test
    void aSearchLongerThanTwoHundredCharactersIsRefused() {
        assertThatThrownBy(() -> call(null, "x".repeat(201), null, null, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Audit search is too long");
    }

    @Test
    void aSearchAtTheLimitIsAccepted() {
        assertThat(call(null, "x".repeat(200), null, null, null, null, null, null).success())
                .isTrue();
    }

    @Test
    void aBlankSearchIsTreatedAsNoSearch() {
        assertThat(call(null, "   ", null, null, null, null, null, null).success()).isTrue();
    }

    // ------------------------------------------------------------------------------ cursor

    @Test
    void aBlankCursorIsNotDecoded() {
        assertThat(service.getLogs(AuditActionScope.ACTIVITY, null, "  ", null, null, null, null,
                null, null, null).success()).isTrue();
    }

    // ------------------------------------------------------------------------------ mapping

    /**
     * A refusal records something that did not happen, so it names no row: {@code targetType} is
     * null and the response carries no target at all. This is the shape that once made the whole
     * page answer 500.
     */
    @Test
    void anEntryWithNoTargetTypeIsMappedWithoutATarget() {
        AuditLog log = auditLog();
        log.setTargetType(null);
        returnPage(log);

        AuditLogPageResponse page = getLogs(null);

        assertThat(page.auditLogs()).hasSize(1);
        assertThat(page.auditLogs().getFirst().target()).isNull();
    }

    @Test
    void anEntryWithATargetTypeKeepsItsTarget() {
        AuditLog log = auditLog();
        returnPage(log);

        AuditLogPageResponse page = getLogs(null);

        assertThat(page.auditLogs().getFirst().target()).isNotNull();
        assertThat(page.auditLogs().getFirst().target().type()).isEqualTo(AuditTargetType.COMMENT);
    }

    // ----------------------------------------------------------------------------- helpers

    private AuditLogPageResponse getLogs(String limit) {
        return service.getLogs(AuditActionScope.ACTIVITY, limit, null, null, null, null, null,
                null, null, null);
    }

    private AuditLogPageResponse call(
            AuditActionScope scope,
            String q,
            String actorId,
            String actorType,
            String action,
            String targetType,
            String from,
            String to
    ) {
        return service.getLogs(
                scope == null ? AuditActionScope.ACTIVITY : scope,
                null, null, q, actorId, actorType, action, targetType, from, to);
    }

    /**
     * A page of one, with the verdict {@code AuditRevertService.describe} would have supplied.
     * {@code toResponse} reads the verdict straight out of that map, so a stub returning an
     * empty one is not "no verdicts" but a null dereference.
     */
    private void returnPage(AuditLog log) {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(revertService.describe(any()))
                .thenReturn(Map.of(log.getId(),
                        new AuditRevertService.Revertability(false, null, null)));
    }

    private static AuditLog auditLog() {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActorType(AuditActorType.USER);
        log.setActorId(UUID.randomUUID());
        log.setActorName("ada");
        log.setActorEmail("ada@kit.edu");
        log.setActorRole("STUDENT");
        log.setAction(AuditAction.COMMENT_CREATED);
        log.setTargetType(AuditTargetType.COMMENT);
        log.setTargetId(UUID.randomUUID());
        log.setTargetLabel("IN0001");
        log.setChanges(Map.of());
        log.setMetadata(Map.of());
        log.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return log;
    }
}

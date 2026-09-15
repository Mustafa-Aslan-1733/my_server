package com.pse.audit.revert;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.audit.service.AuditRevertContext;
import com.pse.shared.dto.BasicResponse;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Revertibility is decided here and reported on the entry, so the panel never recomputes the
 * window or the staleness rule. That makes each refusal a contract: the reason code is what
 * the panel branches on, and getting one wrong either offers a revert that cannot work or
 * hides one that can.
 */
@ExtendWith(MockitoExtension.class)
class AuditRevertServiceTests {

    private static final Duration WINDOW = Duration.ofDays(7);
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UUID AUDIT_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final UUID TARGET_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(null, null, null);

    @Mock
    private AuditLogRepository repository;

    @Mock
    private AuditRevertHandler userHandler;

    private AuditRevertService serviceWith(AuditRevertHandler... handlers) {
        return new AuditRevertService(repository, List.of(handlers), WINDOW, CLOCK);
    }

    /** A recorded field: both halves present, which is the shape a reversal inverts. */
    private static Map<String, Object> change(Object before, Object after) {
        Map<String, Object> half = new LinkedHashMap<>();
        half.put("before", before);
        half.put("after", after);
        return half;
    }

    private static AuditLog log(AuditAction action, Map<String, Object> changes) {
        AuditLog log = new AuditLog();
        log.setId(AUDIT_ID);
        log.setAction(action);
        log.setCreatedAt(NOW.minus(Duration.ofDays(1)));
        log.setTargetType(AuditTargetType.USER);
        log.setTargetId(TARGET_ID);
        log.setChanges(changes);
        return log;
    }

    private static AuditLog userUpdated() {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("username", change("old", "new"));
        return log(AuditAction.USER_UPDATED, changes);
    }

    private void handlerKnows(Map<String, Object> current) {
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        when(userHandler.currentValues(anyCollection()))
                .thenReturn(Map.of(TARGET_ID, current));
    }

    private AuditRevertService.Revertability verdictFor(AuditLog log) {
        return serviceWith(userHandler).describe(List.of(log)).get(log.getId());
    }

    // ---------- describe ----------

    @Test
    void describe_emptyPage_returnsAnEmptyMapWithoutQuerying() {
        // When
        Map<UUID, AuditRevertService.Revertability> verdicts =
                serviceWith(userHandler).describe(List.of());

        // Then
        assertThat(verdicts).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void describe_fieldDiffWhoseValuesStillMatch_isRevertible() {
        // Given
        handlerKnows(Map.of("username", "new"));

        // When
        AuditRevertService.Revertability verdict = verdictFor(userUpdated());

        // Then
        assertThat(verdict.revertible()).isTrue();
        assertThat(verdict.reason()).isNull();
        assertThat(verdict.revertedByAuditId()).isNull();
    }

    /** A deletion records only that the row stopped existing, so there is nothing to invert. */
    @Test
    void describe_actionThatIsNotAFieldEdit_refusesAsActionNotRevertible() {
        // Given
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("username", change("old", "new"));

        // When
        AuditRevertService.Revertability verdict =
                verdictFor(log(AuditAction.USER_DELETED, changes));

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
        verify(userHandler, never()).currentValues(anyCollection());
    }

    @Test
    void describe_targetTypeWithNoRegisteredHandler_refusesAsActionNotRevertible() {
        // Given
        AuditLog log = userUpdated();
        log.setTargetType(AuditTargetType.RATING);

        // When -- the only handler serves USER
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        AuditRevertService.Revertability verdict = verdictFor(log);

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
    }

    /** "exists" and "anonymized" mark a lifecycle event however the action is classified. */
    @Test
    void describe_changesCarryingALifecycleKey_refusesAsActionNotRevertible() {
        // Given
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("exists", change(true, false));

        // When
        AuditRevertService.Revertability verdict = verdictFor(log(AuditAction.USER_UPDATED, changes));

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
    }

    @Test
    void describe_changeThatIsNotABeforeAfterPair_refusesAsActionNotRevertible() {
        // Given
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("username", "just-a-value");

        // When
        AuditRevertService.Revertability verdict = verdictFor(log(AuditAction.USER_UPDATED, changes));

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
    }

    @Test
    void describe_entryWithNoRecordedChanges_refusesAsActionNotRevertible() {
        // Given
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);

        // When
        AuditRevertService.Revertability verdict =
                verdictFor(log(AuditAction.USER_UPDATED, new LinkedHashMap<>()));

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
    }

    /**
     * A refusal records something that did not happen and so carries no target id. Asking an
     * immutable empty map for a null key throws, which once turned a page holding a single
     * refusal into a 500.
     */
    @Test
    void describe_refusalEntryWithoutATargetId_isRefusedWithoutThrowing() {
        // Given
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        AuditLog refusal = log(AuditAction.LOGIN_REFUSED, new LinkedHashMap<>());
        refusal.setTargetType(null);
        refusal.setTargetId(null);

        // When
        AuditRevertService.Revertability verdict = verdictFor(refusal);

        // Then
        assertThat(verdict.revertible()).isFalse();
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
    }

    @Test
    void describe_entryThatWasAlreadyReversed_reportsTheReversingEntry() {
        // Given
        UUID reversalId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        when(userHandler.currentValues(anyCollection())).thenReturn(Map.of(TARGET_ID, Map.of()));
        when(repository.findReversalsOf(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{AUDIT_ID, reversalId}));

        // When
        AuditRevertService.Revertability verdict = verdictFor(userUpdated());

        // Then -- the panel links straight to the entry that undid it
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ALREADY_REVERTED);
        assertThat(verdict.revertedByAuditId()).isEqualTo(reversalId);
    }

    @Test
    void describe_entryOlderThanTheRevertWindow_refusesAsWindowExpired() {
        // Given
        handlerKnows(Map.of("username", "new"));
        AuditLog log = userUpdated();
        log.setCreatedAt(NOW.minus(WINDOW).minusSeconds(1));

        // When
        AuditRevertService.Revertability verdict = verdictFor(log);

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.WINDOW_EXPIRED);
    }

    /** The cutoff comparison is strict, so an entry sitting exactly on it is still revertible. */
    @Test
    void describe_entryExactlyOnTheCutoff_isStillRevertible() {
        // Given
        handlerKnows(Map.of("username", "new"));
        AuditLog log = userUpdated();
        log.setCreatedAt(NOW.minus(WINDOW));

        // When
        AuditRevertService.Revertability verdict = verdictFor(log);

        // Then
        assertThat(verdict.revertible()).isTrue();
    }

    @Test
    void describe_entryOneNanosecondPastTheCutoff_refusesAsWindowExpired() {
        // Given
        handlerKnows(Map.of("username", "new"));
        AuditLog log = userUpdated();
        log.setCreatedAt(NOW.minus(WINDOW).minusNanos(1));

        // When
        AuditRevertService.Revertability verdict = verdictFor(log);

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.WINDOW_EXPIRED);
    }

    @Test
    void describe_targetThatNoLongerExists_refusesAsTargetMissing() {
        // Given -- the handler finds no row for that id
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        when(userHandler.currentValues(anyCollection())).thenReturn(Map.of());

        // When
        AuditRevertService.Revertability verdict = verdictFor(userUpdated());

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.TARGET_MISSING);
    }

    /** Reverting a value that moved on since would silently discard the newer change. */
    @Test
    void describe_valueChangedSinceTheEntry_refusesAsValueChanged() {
        // Given
        handlerKnows(Map.of("username", "changed-by-someone-else"));

        // When
        AuditRevertService.Revertability verdict = verdictFor(userUpdated());

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.VALUE_CHANGED);
    }

    @Test
    void describe_fieldTheHandlerDoesNotKnow_refusesAsActionNotRevertible() {
        // Given -- the handler reports a target, but not this field
        handlerKnows(Map.of("biography", "unrelated"));

        // When
        AuditRevertService.Revertability verdict = verdictFor(userUpdated());

        // Then
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
    }

    /** Batched on purpose: a query per row would land on the panel's heaviest read. */
    @Test
    void describe_severalEntriesOfOneTargetType_loadsTheirCurrentValuesInOneCall() {
        // Given
        UUID secondTarget = UUID.fromString("22222222-3333-4444-5555-666666666666");
        AuditLog first = userUpdated();
        AuditLog second = userUpdated();
        second.setId(UUID.fromString("99999999-8888-7777-6666-555555555555"));
        second.setTargetId(secondTarget);
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        when(userHandler.currentValues(anyCollection())).thenReturn(Map.of(
                TARGET_ID, Map.of("username", "new"),
                secondTarget, Map.of("username", "new")));

        // When
        Map<UUID, AuditRevertService.Revertability> verdicts =
                serviceWith(userHandler).describe(List.of(first, second));

        // Then
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.captor();
        verify(userHandler).currentValues(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(TARGET_ID, secondTarget);
        assertThat(verdicts).hasSize(2);
        assertThat(verdicts.values()).allMatch(AuditRevertService.Revertability::revertible);
    }

    /** An audit page full of logins must not cost a single extra query. */
    @Test
    void describe_pageWithNoRevertibleCandidates_neverAsksForReversals() {
        // Given
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        AuditLog login = log(AuditAction.USER_LOGIN, new LinkedHashMap<>());

        // When
        Map<UUID, AuditRevertService.Revertability> verdicts =
                serviceWith(userHandler).describe(List.of(login));

        // Then
        assertThat(verdicts.get(AUDIT_ID).revertible()).isFalse();
        verify(repository, never()).findReversalsOf(anyCollection());
        verify(userHandler, never()).currentValues(anyCollection());
    }

    @Test
    void describe_pageMixingCandidatesAndNonCandidates_returnsAVerdictForEveryentry() {
        // Given
        UUID loginId = UUID.fromString("44444444-5555-6666-7777-888888888888");
        AuditLog login = log(AuditAction.USER_LOGIN, new LinkedHashMap<>());
        login.setId(loginId);
        handlerKnows(Map.of("username", "new"));

        // When
        Map<UUID, AuditRevertService.Revertability> verdicts =
                serviceWith(userHandler).describe(List.of(userUpdated(), login));

        // Then
        assertThat(verdicts).hasSize(2);
        assertThat(verdicts.get(AUDIT_ID).revertible()).isTrue();
        assertThat(verdicts.get(loginId).reason())
                .isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
    }

    // ---------- revert ----------

    @Test
    void revert_revertibleEntry_appliesTheRecordedBeforeValues() {
        // Given
        handlerKnows(Map.of("username", "new"));
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(userUpdated()));

        // When
        BasicResponse response = serviceWith(userHandler).revert(PRINCIPAL, AUDIT_ID);

        // Then
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.captor();
        verify(userHandler).applyInverse(any(), any(), before.capture());
        assertThat(before.getValue()).containsExactly(Map.entry("username", "old"));
        assertThat(response.message()).isEqualTo("Reverted USER_UPDATED");
        assertThat(response.success()).isTrue();
    }

    /** The context is what links the new audit event back to the entry being reversed. */
    @Test
    void revert_whileApplyingTheInverse_exposesTheRevertedIdAndClearsItAfterwards() {
        // Given
        handlerKnows(Map.of("username", "new"));
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(userUpdated()));
        UUID[] seenInsideHandler = new UUID[1];
        doAnswerCapturingContext(seenInsideHandler);

        // When
        serviceWith(userHandler).revert(PRINCIPAL, AUDIT_ID);

        // Then
        assertThat(seenInsideHandler[0]).isEqualTo(AUDIT_ID);
        assertThat(AuditRevertContext.get()).isNull();
    }

    @Test
    void revert_handlerThrows_stillClearsTheRevertContext() {
        // Given
        handlerKnows(Map.of("username", "new"));
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(userUpdated()));
        org.mockito.Mockito.doThrow(new IllegalStateException("handler blew up"))
                .when(userHandler).applyInverse(any(), any(), any());
        AuditRevertService service = serviceWith(userHandler);

        // When / Then -- a leaked ThreadLocal would mislabel the next request on this thread
        assertThatThrownBy(() -> service.revert(PRINCIPAL, AUDIT_ID))
                .isInstanceOf(IllegalStateException.class);
        assertThat(AuditRevertContext.get()).isNull();
    }

    /**
     * The revert twin of {@code describe_refusalEntryWithoutATargetId_isRefusedWithoutThrowing}.
     *
     * <p>{@code describe} learned to guard the null target id -- a refusal records something
     * that did not happen, so it carries none -- and {@code revert} never did, though the
     * panel hands the operator both from the same list. Asking {@code Map.of()} for a null key
     * throws, so clicking revert on a LOGIN_REFUSED row answered 500 where the contract is a
     * 409 naming the reason.
     */
    @Test
    void revert_refusalEntryWithoutATargetId_isRefusedRatherThanAnsweringAServerError() {
        // Given
        AuditLog refusal = log(AuditAction.LOGIN_REFUSED, new LinkedHashMap<>());
        refusal.setTargetType(null);
        refusal.setTargetId(null);
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(refusal));
        AuditRevertService service = serviceWith(userHandler);

        // When / Then
        assertThatThrownBy(() -> service.revert(PRINCIPAL, AUDIT_ID))
                .isInstanceOf(ApiException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE.name());
                });
        verify(userHandler, never()).applyInverse(any(), any(), any());
    }

    @Test
    void revert_unknownAuditId_throwsNotFound() {
        // Given
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.empty());
        AuditRevertService service = serviceWith(userHandler);

        // When / Then
        assertThatThrownBy(() -> service.revert(PRINCIPAL, AUDIT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("Audit entry not found")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .satisfies(exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * 409 rather than 400: the request is well formed, the state of the world is what makes
     * it inapplicable. The reason code is the part the panel branches on.
     */
    @Test
    void revert_actionThatCannotBeInverted_throwsConflictNamingTheAction() {
        // Given
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("username", change("old", "new"));
        when(repository.findById(AUDIT_ID))
                .thenReturn(Optional.of(log(AuditAction.USER_DELETED, changes)));
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        AuditRevertService service = serviceWith(userHandler);

        // When / Then
        assertThatThrownBy(() -> service.revert(PRINCIPAL, AUDIT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("USER_DELETED cannot be reversed")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE.name());
                });
        verify(userHandler, never()).applyInverse(any(), any(), any());
    }

    @Test
    void revert_entryOlderThanTheWindow_throwsConflictQuotingTheConfiguredWindow() {
        // Given
        handlerKnows(Map.of("username", "new"));
        AuditLog log = userUpdated();
        log.setCreatedAt(NOW.minus(WINDOW).minusSeconds(1));
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(log));
        AuditRevertService service = serviceWith(userHandler);

        // When / Then -- the Duration renders in ISO-8601 form, so P7D reads as PT168H
        assertThatThrownBy(() -> service.revert(PRINCIPAL, AUDIT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("This entry is older than the PT168H revert window")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .satisfies(exception -> assertThat(exception.getCode())
                        .isEqualTo(AuditRevertRefusal.WINDOW_EXPIRED.name()));
    }

    @Test
    void revert_valueChangedSinceTheEntry_throwsConflictWithValueChanged() {
        // Given
        handlerKnows(Map.of("username", "changed-by-someone-else"));
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(userUpdated()));
        AuditRevertService service = serviceWith(userHandler);

        // When / Then
        assertThatThrownBy(() -> service.revert(PRINCIPAL, AUDIT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("The value changed after this entry, so reverting would discard that change")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .satisfies(exception -> assertThat(exception.getCode())
                        .isEqualTo(AuditRevertRefusal.VALUE_CHANGED.name()));
        verify(userHandler, never()).applyInverse(any(), any(), any());
    }

    @Test
    void revert_entryAlreadyReverted_throwsConflictWithAlreadyReverted() {
        // Given
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        when(userHandler.currentValues(anyCollection()))
                .thenReturn(Map.of(TARGET_ID, Map.of("username", "new")));
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(userUpdated()));
        when(repository.findReversalsOf(anyCollection())).thenReturn(List.<Object[]>of(
                new Object[]{AUDIT_ID, UUID.fromString("11111111-2222-3333-4444-555555555555")}));
        AuditRevertService service = serviceWith(userHandler);

        // When / Then
        assertThatThrownBy(() -> service.revert(PRINCIPAL, AUDIT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("This entry has already been reverted")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .satisfies(exception -> assertThat(exception.getCode())
                        .isEqualTo(AuditRevertRefusal.ALREADY_REVERTED.name()));
    }

    @Test
    void revert_targetThatNoLongerExists_throwsConflictWithTargetMissing() {
        // Given
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        when(userHandler.currentValues(anyCollection())).thenReturn(Map.of());
        when(repository.findById(AUDIT_ID)).thenReturn(Optional.of(userUpdated()));
        AuditRevertService service = serviceWith(userHandler);

        // When / Then
        assertThatThrownBy(() -> service.revert(PRINCIPAL, AUDIT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("The record this entry is about no longer exists")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .satisfies(exception -> assertThat(exception.getCode())
                        .isEqualTo(AuditRevertRefusal.TARGET_MISSING.name()));
    }

    // ---------- handler wiring ----------

    @Test
    void constructor_twoHandlersClaimingTheSameTargetType_failsFast() {
        // Given
        AuditRevertHandler duplicate = org.mockito.Mockito.mock(AuditRevertHandler.class);
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        when(duplicate.targetType()).thenReturn(AuditTargetType.USER);

        // When / Then -- silently keeping one of them would revert through the wrong handler
        assertThatThrownBy(() -> serviceWith(userHandler, duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate key");
    }

    @Test
    void describe_severalHandlers_routesEachEntryToTheOneForItsTargetType() {
        // Given
        AuditRevertHandler lectureHandler = org.mockito.Mockito.mock(AuditRevertHandler.class);
        when(userHandler.targetType()).thenReturn(AuditTargetType.USER);
        when(lectureHandler.targetType()).thenReturn(AuditTargetType.LECTURE);
        when(userHandler.currentValues(anyCollection()))
                .thenReturn(Map.of(TARGET_ID, Map.of("username", "new")));

        // When
        serviceWith(userHandler, lectureHandler).describe(List.of(userUpdated()));

        // Then -- the lecture handler is never consulted about a user entry
        verify(userHandler).currentValues(anyCollection());
        verify(lectureHandler, never()).currentValues(anyCollection());
    }

    @Test
    void describe_noHandlersRegisteredAtAll_refusesEveryEntry() {
        // Given
        AuditRevertService service = new AuditRevertService(repository, List.of(), WINDOW, CLOCK);

        // When
        AuditRevertService.Revertability verdict =
                service.describe(List.of(userUpdated())).get(AUDIT_ID);

        // Then
        assertThat(verdict.revertible()).isFalse();
        assertThat(verdict.reason()).isEqualTo(AuditRevertRefusal.ACTION_NOT_REVERTIBLE);
    }

    private void doAnswerCapturingContext(UUID[] sink) {
        org.mockito.Mockito.doAnswer(invocation -> {
            sink[0] = AuditRevertContext.get();
            return null;
        }).when(userHandler).applyInverse(any(), any(), any());
    }
}

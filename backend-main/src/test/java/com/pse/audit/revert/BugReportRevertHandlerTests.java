package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.moderation.dto.request.BugReportUpdateRequest;
import com.pse.moderation.model.BugReport;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.service.ModerationBugReportService;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.BugSeverity;
import com.pse.shared.enums.ReportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A handler is one half of a contract with {@link AuditRevertService}: the field names it
 * reports have to be the ones the audit entry recorded, or the staleness check compares a
 * recorded field against nothing and the entry silently stops being revertible.
 *
 * <p>So the assertions here are on the key set as much as on the values.
 */
@ExtendWith(MockitoExtension.class)
class BugReportRevertHandlerTests {

    private static final UUID FIRST = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final UUID SECOND = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(null, null, null);

    @Mock
    private BugReportRepository bugReportRepository;

    @Mock
    private ModerationBugReportService bugReportService;

    private BugReportRevertHandler handler() {
        return new BugReportRevertHandler(bugReportRepository, bugReportService);
    }

    private static BugReport report(UUID id) {
        BugReport report = new BugReport();
        report.setId(id);
        report.setStatus(ReportStatus.REVIEWED);
        report.setSeverity(BugSeverity.HIGH);
        report.setTitle("Vote button does nothing");
        report.setDescription("Tapping it twice registers one vote.");
        return report;
    }

    private BugReportUpdateRequest replayed(UUID targetId, Map<String, Object> before) {
        handler().applyInverse(PRINCIPAL, targetId, before);
        ArgumentCaptor<BugReportUpdateRequest> request =
                ArgumentCaptor.forClass(BugReportUpdateRequest.class);
        verify(bugReportService).updateBugReport(eq(PRINCIPAL), eq(targetId), request.capture());
        return request.getValue();
    }

    @Test
    void targetType_isBugReport() {
        // When / Then
        assertThat(handler().targetType()).isEqualTo(AuditTargetType.BUG_REPORT);
    }

    // ---------- currentValues ----------

    @Test
    void currentValues_reportThatExists_mapsEveryFieldTheHandlerCanRevert() {
        // Given
        when(bugReportRepository.findAllById(List.of(FIRST))).thenReturn(List.of(report(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST));

        // Then -- enums as their names, because that is how they survived the JSON column
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(
                entry("status", "REVIEWED"),
                entry("severity", "HIGH"),
                entry("title", "Vote button does nothing"),
                entry("description", "Tapping it twice registers one vote.")
        );
    }

    /** An id with no surviving row is how {@code TARGET_MISSING} is detected upstream. */
    @Test
    void currentValues_idWithNoSurvivingRow_isAbsentRatherThanMappedToNull() {
        // Given
        when(bugReportRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(report(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values).doesNotContainKey(SECOND);
    }

    @Test
    void currentValues_noTargets_returnsAnEmptyMap() {
        // Given
        when(bugReportRepository.findAllById(List.of())).thenReturn(List.of());

        // When / Then
        assertThat(handler().currentValues(List.of())).isEmpty();
        verifyNoInteractions(bugReportService);
    }

    // ---------- applyInverse ----------

    /**
     * The handler does not write fields itself. It calls the method the panel calls, so the
     * revert inherits that path's validation and writes its own audit event.
     */
    @Test
    void applyInverse_recordedValues_replaysThemThroughTheUpdateService() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("status", "OPEN");
        before.put("severity", "LOW");
        before.put("title", "Vote button");
        before.put("description", "Original description.");

        // When
        BugReportUpdateRequest request = replayed(FIRST, before);

        // Then
        assertThat(request.status()).isEqualTo(ReportStatus.OPEN);
        assertThat(request.severity()).isEqualTo(BugSeverity.LOW);
        assertThat(request.title()).isEqualTo("Vote button");
        assertThat(request.description()).isEqualTo("Original description.");
    }

    /**
     * Only the edited fields are in an entry's {@code before} half. The rest arrive as null,
     * which the update service reads as "leave alone" -- so a revert touches exactly the
     * fields the original edit touched.
     */
    @Test
    void applyInverse_beforeHalfCarryingOneField_leavesTheOthersNull() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("status", "OPEN");

        // When
        BugReportUpdateRequest request = replayed(FIRST, before);

        // Then
        assertThat(request.status()).isEqualTo(ReportStatus.OPEN);
        assertThat(request.severity()).isNull();
        assertThat(request.title()).isNull();
        assertThat(request.description()).isNull();
    }
}

package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.auth.service.IdentityService;
import com.pse.moderation.dto.request.BugReportRequest;
import com.pse.moderation.dto.request.BugReportUpdateRequest;
import com.pse.moderation.dto.response.GitLabIssueResponse;
import com.pse.moderation.model.Admin;
import com.pse.moderation.model.BugReport;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.auth.model.Token;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.BugSeverity;
import com.pse.shared.enums.IssueState;
import com.pse.shared.enums.ReportStatus;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The bug report path, driven through {@code AdminApiIntegrationTests} until now and therefore
 * at 3 missed lines but 15 missed branches — [P-2] in the findings, exactly: an integration
 * test can hold a unit's lines at green while nothing states what the unit is supposed to do.
 *
 * <p>The branches it leaves out are the ones that matter here. `sendBugReport` refuses through a
 * single six-way condition, so the integration test that submits one valid report covers the
 * whole disjunction with one arm; each arm is a different way a student's report is dropped.
 * And `updateBugReport` has the shape batch 4 tested in the other three moderation services:
 * record a change only where the value differs, and write nothing at all when nothing did.
 */
@ExtendWith(MockitoExtension.class)
class ModerationBugReportServiceTests {

    private static final UUID REPORT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    /** Fixed, per the house rule, so resolvedAt is an assertable value rather than "now". */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
    private static final String AUTH_HEADER = "Bearer token";

    @Mock private BugReportRepository repository;
    @Mock private IdentityService identityService;
    @Mock private AuditWriter auditWriter;
    @Mock private GitLabClient gitLabClient;
    @Mock private BugReportIssueTransactions issueTransactions;

    private ModerationBugReportService service() {
        return new ModerationBugReportService(
                repository, identityService, auditWriter, gitLabClient, issueTransactions, CLOCK);
    }

    private static AuthenticatedUser principal() {
        return new AuthenticatedUser(new Student(), new Token(), new Admin());
    }

    private static BugReport report() {
        BugReport report = new BugReport();
        report.setId(REPORT_ID);
        report.setTitle("Login button does nothing");
        report.setDescription("Tapping it on Android 14 has no effect");
        report.setSeverity(BugSeverity.HIGH);
        report.setStatus(ReportStatus.OPEN);
        return report;
    }

    private void reportExists(BugReport report) {
        when(repository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
    }

    // ---------------------------------------------------------------- sendBugReport

    @Test
    void sendBugReport_withoutAValidToken_isRefusedBeforeAnythingIsRead() {
        when(identityService.verifyUser(AUTH_HEADER)).thenReturn(null);

        assertThatThrownBy(() -> service().sendBugReport(AUTH_HEADER, validRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Not logged in")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(repository, auditWriter);
    }

    static Stream<Arguments> incompleteReports() {
        return Stream.of(
                Arguments.of("no request at all", null),
                Arguments.of(
                        "no title", new BugReportRequest(null, "d", BugSeverity.LOW)),
                Arguments.of(
                        "blank title", new BugReportRequest("   ", "d", BugSeverity.LOW)),
                Arguments.of(
                        "no description", new BugReportRequest("t", null, BugSeverity.LOW)),
                Arguments.of(
                        "blank description", new BugReportRequest("t", "   ", BugSeverity.LOW)),
                Arguments.of(
                        "no severity", new BugReportRequest("t", "d", null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteReports")
    void sendBugReport_incompleteReport_isRefusedWithoutBeingStored(
            String scenario, BugReportRequest request) {
        // One condition with six arms in production; six separate cases here, because each arm
        // is a different way a student's report disappears and a single valid submission in an
        // integration test exercises none of them.
        when(identityService.verifyUser(AUTH_HEADER)).thenReturn(new Student());

        assertThatThrownBy(() -> service().sendBugReport(AUTH_HEADER, request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid bug report")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(repository, auditWriter);
    }

    @Test
    void sendBugReport_validReport_storesItTrimmedAndAttributedToTheReporter() {
        Student reporter = new Student();
        when(identityService.verifyUser(AUTH_HEADER)).thenReturn(reporter);

        BasicResponse response = service().sendBugReport(
                AUTH_HEADER,
                new BugReportRequest("  Login broken  ", "  on Android  ", BugSeverity.CRITICAL));

        ArgumentCaptor<BugReport> saved = ArgumentCaptor.forClass(BugReport.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Login broken");
        assertThat(saved.getValue().getDescription()).isEqualTo("on Android");
        assertThat(saved.getValue().getSeverity()).isEqualTo(BugSeverity.CRITICAL);
        assertThat(saved.getValue().getReporter()).isSameAs(reporter);
        assertThat(response.success()).isTrue();
    }

    @Test
    void sendBugReport_validReport_isRecordedAsAStudentActionCarryingTheSeverity() {
        Student reporter = new Student();
        when(identityService.verifyUser(AUTH_HEADER)).thenReturn(reporter);

        service().sendBugReport(AUTH_HEADER, validRequest());

        verify(auditWriter).writeStudentAction(
                reporter,
                AuditAction.BUG_REPORT_CREATED,
                AuditTargetType.BUG_REPORT,
                null,
                "Login button does nothing",
                Map.of("severity", "HIGH"));
    }

    private static BugReportRequest validRequest() {
        return new BugReportRequest(
                "Login button does nothing", "Tapping it does nothing", BugSeverity.HIGH);
    }

    // ---------------------------------------------------------------- updateBugReport

    @Test
    void updateBugReport_nullRequest_isRefusedBeforeTheReportIsLookedUp() {
        assertThatThrownBy(() -> service().updateBugReport(principal(), REPORT_ID, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(repository, auditWriter);
    }

    @Test
    void updateBugReport_everyFieldOmitted_isRefusedBeforeTheReportIsLookedUp() {
        BugReportUpdateRequest empty = new BugReportUpdateRequest(null, null, null, null);

        assertThatThrownBy(() -> service().updateBugReport(principal(), REPORT_ID, empty))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied");

        verifyNoInteractions(repository, auditWriter);
    }

    @Test
    void updateBugReport_unknownReport_isNotFound() {
        when(repository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateBugReport(
                principal(), REPORT_ID,
                new BugReportUpdateRequest(ReportStatus.REVIEWED, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Bug report not found")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateBugReport_statusChanged_recordsBeforeAndAfterAndSaves() {
        BugReport report = report();
        reportExists(report);

        service().updateBugReport(principal(), REPORT_ID,
                new BugReportUpdateRequest(ReportStatus.ACTION_TAKEN, null, null, null));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.ACTION_TAKEN);
        assertThat(capturedChanges()).containsOnlyKeys("status")
                .extractingByKey("status")
                .isEqualTo(Map.of("before", "OPEN", "after", "ACTION_TAKEN"));
        verify(repository).save(report);
    }

    /**
     * The columns existed with a foreign key and nothing ever wrote them, so the BugReport
     * clause of {@code AdminRepository.hasModerationHistory} could never be true. The two
     * report services beside this one have always recorded the equivalent pair.
     */
    @Test
    void updateBugReport_statusChanged_recordsWhoDealtWithItAndWhen() {
        BugReport report = report();
        reportExists(report);
        AuthenticatedUser principal = principal();

        service().updateBugReport(principal, REPORT_ID,
                new BugReportUpdateRequest(ReportStatus.ACTION_TAKEN, null, null, null));

        assertThat(report.getResolvedBy()).isSameAs(principal.admin());
        assertThat(report.getResolvedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    /**
     * OPEN is the one status that is not a disposition. Reopening has to clear the pair --
     * leaving a name on an open report would say somebody dealt with it when nobody has.
     */
    @Test
    void updateBugReport_reopened_clearsWhoDealtWithItAndWhen() {
        BugReport report = report();
        report.setStatus(ReportStatus.ACTION_TAKEN);
        report.setResolvedBy(new Admin());
        report.setResolvedAt(LocalDateTime.now(CLOCK).minusDays(1));
        reportExists(report);

        service().updateBugReport(principal(), REPORT_ID,
                new BugReportUpdateRequest(ReportStatus.OPEN, null, null, null));

        assertThat(report.getResolvedBy()).isNull();
        assertThat(report.getResolvedAt()).isNull();
    }

    @Test
    void updateBugReport_severityChanged_recordsBeforeAndAfter() {
        BugReport report = report();
        reportExists(report);

        service().updateBugReport(principal(), REPORT_ID,
                new BugReportUpdateRequest(null, BugSeverity.LOW, null, null));

        assertThat(report.getSeverity()).isEqualTo(BugSeverity.LOW);
        assertThat(capturedChanges()).containsOnlyKeys("severity");
    }

    @Test
    void updateBugReport_titleAndDescriptionChanged_areTrimmedAndRecorded() {
        BugReport report = report();
        reportExists(report);

        service().updateBugReport(principal(), REPORT_ID,
                new BugReportUpdateRequest(null, null, "  Better title  ", "  Better text  "));

        assertThat(report.getTitle()).isEqualTo("Better title");
        assertThat(report.getDescription()).isEqualTo("Better text");
        assertThat(capturedChanges()).containsOnlyKeys("title", "description");
    }

    static Stream<Arguments> unchangedResubmissions() {
        return Stream.of(
                Arguments.of(
                        "the same status",
                        new BugReportUpdateRequest(ReportStatus.OPEN, null, null, null)),
                Arguments.of(
                        "the same severity",
                        new BugReportUpdateRequest(null, BugSeverity.HIGH, null, null)),
                Arguments.of(
                        "the same title",
                        new BugReportUpdateRequest(null, null, "Login button does nothing", null)),
                Arguments.of(
                        "the same title with padding",
                        new BugReportUpdateRequest(
                                null, null, "  Login button does nothing  ", null)),
                Arguments.of(
                        "the same description",
                        new BugReportUpdateRequest(
                                null, null, null, "Tapping it on Android 14 has no effect")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unchangedResubmissions")
    void updateBugReport_valueResubmittedUnchanged_writesNothingAtAll(
            String scenario, BugReportUpdateRequest request) {
        // The property batch 4 tested hardest in the other moderation services: an edit that
        // changed nothing must not save and must not write an audit entry, or the moderation
        // trail fills with events nobody performed.
        reportExists(report());

        BasicResponse response = service().updateBugReport(principal(), REPORT_ID, request);

        assertThat(response.success()).isTrue();
        verify(repository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }

    @Test
    void updateBugReport_everyFieldResubmittedUnchanged_writesNothingAtAll() {
        reportExists(report());

        service().updateBugReport(principal(), REPORT_ID, new BugReportUpdateRequest(
                ReportStatus.OPEN,
                BugSeverity.HIGH,
                "Login button does nothing",
                "Tapping it on Android 14 has no effect"));

        verify(repository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }

    static Stream<Arguments> invalidText() {
        return Stream.of(
                Arguments.of(
                        "blank title",
                        new BugReportUpdateRequest(null, null, "   ", null), "Invalid title"),
                Arguments.of(
                        "title past 500 characters",
                        new BugReportUpdateRequest(null, null, "t".repeat(501), null),
                        "Invalid title"),
                Arguments.of(
                        "blank description",
                        new BugReportUpdateRequest(null, null, null, "   "),
                        "Invalid description"),
                Arguments.of(
                        "description past 10000 characters",
                        new BugReportUpdateRequest(null, null, null, "d".repeat(10_001)),
                        "Invalid description"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidText")
    void updateBugReport_textOutsideItsBounds_isRefusedWithoutWriting(
            String scenario, BugReportUpdateRequest request, String message) {
        reportExists(report());

        assertThatThrownBy(() -> service().updateBugReport(principal(), REPORT_ID, request))
                .isInstanceOf(ApiException.class)
                .hasMessage(message)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(repository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }

    @Test
    void updateBugReport_titleAtTheBound_isAccepted() {
        BugReport report = report();
        reportExists(report);

        service().updateBugReport(principal(), REPORT_ID,
                new BugReportUpdateRequest(null, null, "t".repeat(500), null));

        assertThat(report.getTitle()).hasSize(500);
    }

    @Test
    void updateBugReport_change_isAuditedAgainstTheReportUnderItsNewTitle() {
        BugReport report = report();
        reportExists(report);

        service().updateBugReport(principal(), REPORT_ID,
                new BugReportUpdateRequest(null, null, "Renamed", null));

        verify(auditWriter).write(
                any(Admin.class),
                eq(AuditAction.BUG_REPORT_UPDATED),
                eq(AuditTargetType.BUG_REPORT),
                eq(REPORT_ID),
                eq("Renamed"),
                any(),
                any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedChanges() {
        ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter).write(
                any(), any(), any(), any(), any(), changes.capture(), any());
        return changes.getValue();
    }

    // ---------------------------------------------------------------- createIssue

    @Test
    void createIssue_integrationNotConfigured_isUnavailableWithoutTouchingTheReport() {
        when(gitLabClient.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service().createIssue(principal(), REPORT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("GitLab integration is not configured")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        verifyNoInteractions(issueTransactions);
    }

    @Test
    void createIssue_reportDoesNotExist_isNotMarkedAsAFailedAttempt() {
        // A missing report is not a failed attempt: marking one would invent state for a row
        // that does not exist, and the marker would outlive the request.
        when(gitLabClient.isEnabled()).thenReturn(true);
        when(issueTransactions.createOrReturnExisting(any(), eq(REPORT_ID)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Bug report not found"));

        assertThatThrownBy(() -> service().createIssue(principal(), REPORT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("Bug report not found");

        verify(issueTransactions, never()).markFailed(any());
    }

    @Test
    void createIssue_trackerRefuses_marksTheAttemptFailedAndRethrows() {
        when(gitLabClient.isEnabled()).thenReturn(true);
        when(issueTransactions.createOrReturnExisting(any(), eq(REPORT_ID)))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach GitLab"));

        assertThatThrownBy(() -> service().createIssue(principal(), REPORT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("Could not reach GitLab");

        verify(issueTransactions).markFailed(REPORT_ID);
    }

    @Test
    void createIssue_firstAttempt_reportsTheIssueAsCreated() {
        when(gitLabClient.isEnabled()).thenReturn(true);
        when(issueTransactions.createOrReturnExisting(any(), eq(REPORT_ID)))
                .thenReturn(new BugReportIssueTransactions.Attempt("https://gitlab/issues/7", 7, false));

        GitLabIssueResponse response = service().createIssue(principal(), REPORT_ID);

        assertThat(response.message()).isEqualTo("Created GitLab issue");
        assertThat(response.issueUrl()).isEqualTo("https://gitlab/issues/7");
        assertThat(response.issueIid()).isEqualTo(7);
        assertThat(response.issueState()).isEqualTo(IssueState.CREATED);
    }

    @Test
    void createIssue_reportThatAlreadyHasAnIssue_saysSoRatherThanOpeningASecondOne() {
        // Idempotency is the property this endpoint exists to guarantee: a double-clicked
        // button and two admins triaging the same queue must not open two issues.
        when(gitLabClient.isEnabled()).thenReturn(true);
        when(issueTransactions.createOrReturnExisting(any(), eq(REPORT_ID)))
                .thenReturn(new BugReportIssueTransactions.Attempt("https://gitlab/issues/7", 7, true));

        GitLabIssueResponse response = service().createIssue(principal(), REPORT_ID);

        assertThat(response.message()).isEqualTo("Bug report already has a GitLab issue");
    }
}

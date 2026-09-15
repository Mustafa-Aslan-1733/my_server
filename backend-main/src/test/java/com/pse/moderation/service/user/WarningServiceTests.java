package com.pse.moderation.service.user;

import com.pse.audit.service.AuditWriter;
import com.pse.auth.repository.TokenRepository;
import com.pse.auth.service.AdminSuperuserPolicy;
import com.pse.moderation.model.Admin;
import com.pse.moderation.dto.response.WarningHistoryResponse;
import com.pse.moderation.dto.request.WarningRequest;
import com.pse.shared.enums.NotificationType;
import com.pse.social.model.Notification;
import com.pse.moderation.model.Warning;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.social.repository.NotificationRepository;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Issuing, listing, correcting and withdrawing a warning.
 *
 * <p>Split out of {@code ModerationUserServiceTests} with the code it covers.
 */
@ExtendWith(MockitoExtension.class)class WarningServiceTests {

    @Mock private StudentRepository studentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private WarningRepository warningRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private AuditWriter auditWriter;
    @Mock private AdminSuperuserPolicy superuserPolicy;


    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-07T10:15:30Z"), ZoneOffset.UTC);

    private static final UUID TARGET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CALLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private WarningService warnings() {
        return new WarningService(studentRepository, adminRepository, warningRepository,
                notificationRepository, auditWriter, moderatedStudents());
    }

    /**
     * Real, not mocked: it only forwards to the repositories already mocked above, and every
     * assertion here is about what reaches those.
     */
    private ModeratedStudents moderatedStudents() {
        return new ModeratedStudents(
                studentRepository, adminRepository, tokenRepository, superuserPolicy);
    }

    private static Warning warning(Student student, String message) {
        Warning warning = new Warning();
        warning.setId(UUID.randomUUID());
        warning.setStudent(student);
        warning.setAdmin(new Admin());
        warning.setMessage(message);
        warning.setCreatedAt(LocalDateTime.parse("2026-01-01T00:00:00"));
        return warning;
    }

    /** The acting administrator; {@code elevated} decides whether the guards lift. */
    private AuthenticatedUser caller(boolean elevated) {
        Student callerStudent = student(CALLER_ID, "operator", "operator@student.kit.edu");
        if (elevated) {
            when(superuserPolicy.isSuperuser("operator@student.kit.edu")).thenReturn(true);
        }
        return new AuthenticatedUser(callerStudent, null, new Admin());
    }

    private static Student student(UUID id, String username, String kitEmail) {
        Student student = new Student();
        student.setId(id);
        student.setUsername(username);
        student.setKitEmail(kitEmail);
        student.setStatus(UserStatus.ACTIVE);
        student.setCredibilityScore(0);
        return student;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> changesWritten() {
        ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter).write(any(), any(), any(), any(), any(), changes.capture(), any());
        return changes.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadataWritten() {
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter).write(any(), any(), any(), any(), any(), any(), metadata.capture());
        return metadata.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Object before(Map<String, Object> changes, String field) {
        return ((Map<String, Object>) changes.get(field)).get("before");
    }

    @SuppressWarnings("unchecked")
    private static Object after(Map<String, Object> changes, String field) {
        return ((Map<String, Object>) changes.get(field)).get("after");
    }

     /*
     * Nothing in this file touched warnings before. They are the one moderation action a
     * student is actually told about -- warnStudent is the only place that writes a
     * Notification -- and the whole issue/correct/withdraw cycle was covered only through the
     * admin API, which is what docs/TODO.md meant by "covered only through the API".
     */

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t "})
    void warnStudent_blankMessage_isRejectedBeforeTheTargetIsLookedUp(String message) {
        assertThatThrownBy(() ->
                warnings().warnStudent(caller(false), TARGET_ID, new WarningRequest(message)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Warning message is required");

        verifyNoInteractions(studentRepository, warningRepository, notificationRepository);
    }

    @Test
    void warnStudent_nullMessage_isRejectedRatherThanStoredAsNull() {
        assertThatThrownBy(() ->
                warnings().warnStudent(caller(false), TARGET_ID, new WarningRequest(null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Warning message is required");
    }

    @Test
    void warnStudent_messageLongerThanTheColumn_isRejected() {
        assertThatThrownBy(() -> warnings().warnStudent(
                caller(false), TARGET_ID, new WarningRequest("x".repeat(2_001))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Warning message is required");
    }

    /**
     * The other side of the length limit. The test above pins 2001 as refused; nothing pinned
     * 2000 as accepted, so the boundary itself was free to move -- `> 2_000` could become
     * `>= 2_000` and the whole suite stayed green. Found by reading the mutation report: the
     * surviving ConditionalsBoundaryMutator on this line and on the updateWarning copy of it.
     *
     * <p>2000 is the column width. A message of exactly that length has to be stored, or the
     * limit the message text advertises is off by one against the limit the code enforces.
     */
    @Test
    void warnStudent_messageExactlyAtTheColumnWidth_isAccepted() {
        Student target = student(TARGET_ID, "ada", "ada@student.kit.edu");
        when(studentRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));
        when(adminRepository.existsByStudent(target)).thenReturn(false);
        when(warningRepository.countByStudent(target)).thenReturn(0L);

        String exactly = "x".repeat(2_000);

        assertThat(warnings().warnStudent(caller(false), TARGET_ID, new WarningRequest(exactly))
                .success()).isTrue();

        ArgumentCaptor<Warning> warning = ArgumentCaptor.forClass(Warning.class);
        verify(warningRepository).save(warning.capture());
        assertThat(warning.getValue().getMessage()).hasSize(2_000);
    }

    /**
     * A warning nobody is told about is not a warning. The notification is the only
     * student-visible effect, and the account status is deliberately left alone.
     */
    @Test
    void warnStudent_notifiesTheStudentAndLeavesTheAccountStatusAlone() {
        Student target = student(TARGET_ID, "ada", "ada@student.kit.edu");
        // Not givenTarget: warning an ordinary student never reaches the superuser policy,
        // and Mockito is right to object to a stub the code cannot use.
        when(studentRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));
        when(adminRepository.existsByStudent(target)).thenReturn(false);
        when(warningRepository.countByStudent(target)).thenReturn(2L);

        assertThat(warnings().warnStudent(
                caller(false), TARGET_ID, new WarningRequest("  Please be civil  ")).success())
                .isTrue();

        ArgumentCaptor<Warning> warning = ArgumentCaptor.forClass(Warning.class);
        verify(warningRepository).save(warning.capture());
        assertThat(warning.getValue().getMessage())
                .as("stored trimmed, the way it was validated")
                .isEqualTo("Please be civil");

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertThat(notification.getValue().getRecipient()).isEqualTo(target);
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.WARNING);
        assertThat(notification.getValue().getMessage()).isEqualTo("Please be civil");

        assertThat(target.getStatus())
                .as("a warning restricts nothing")
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(before(changesWritten(), "warnings")).isEqualTo(2L);
        assertThat(after(changesWritten(), "warnings")).isEqualTo(3L);
    }

    @Test
    void updateWarning_messageResubmittedUnchanged_recordsNothing() {
        Student target = student(TARGET_ID, "ada", "ada@student.kit.edu");
        Warning warning = warning(target, "Please be civil");
        when(warningRepository.findById(warning.getId())).thenReturn(Optional.of(warning));

        assertThat(warnings().updateWarning(
                caller(false), TARGET_ID, warning.getId(), new WarningRequest("Please be civil"))
                .success()).isTrue();

        verify(warningRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }

    @Test
    void updateWarning_correctedMessage_isRecordedAndTheStudentIsNotNotifiedAgain() {
        Student target = student(TARGET_ID, "ada", "ada@student.kit.edu");
        Warning warning = warning(target, "Please be civil");
        when(warningRepository.findById(warning.getId())).thenReturn(Optional.of(warning));

        warnings().updateWarning(caller(false), TARGET_ID, warning.getId(),
                new WarningRequest("Please be civil to other students"));

        assertThat(warning.getMessage()).isEqualTo("Please be civil to other students");
        assertThat(before(changesWritten(), "message")).isEqualTo("Please be civil");
        assertThat(after(changesWritten(), "message"))
                .isEqualTo("Please be civil to other students");
        verifyNoInteractions(notificationRepository);
    }

    /**
     * A warning is addressed as a sub-resource of the student, so a warning that belongs to
     * somebody else is a wrong URL rather than somebody else's warning -- and answering 404
     * rather than 403 is what stops the endpoint confirming that the id exists at all.
     */
    /** The same boundary as warnStudent, on the copy of the check that lives in this method. */
    @Test
    void updateWarning_messageExactlyAtTheColumnWidth_isAccepted() {
        Student target = student(TARGET_ID, "ada", "ada@student.kit.edu");
        Warning warning = warning(target, "Please be civil");
        when(warningRepository.findById(warning.getId())).thenReturn(Optional.of(warning));

        String exactly = "x".repeat(2_000);

        assertThat(warnings().updateWarning(
                caller(false), TARGET_ID, warning.getId(), new WarningRequest(exactly))
                .success()).isTrue();

        assertThat(warning.getMessage()).hasSize(2_000);
    }

    @Test
    void updateWarning_warningBelongingToAnotherStudent_readsAsNotFound() {
        Student other = student(CALLER_ID, "bob", "bob@student.kit.edu");
        Warning warning = warning(other, "Please be civil");
        when(warningRepository.findById(warning.getId())).thenReturn(Optional.of(warning));

        assertThatThrownBy(() -> warnings().updateWarning(
                caller(false), TARGET_ID, warning.getId(), new WarningRequest("Reworded")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Warning not found")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteWarning_recordsTheWithdrawalWithTheTextItWithdrew() {
        Student target = student(TARGET_ID, "ada", "ada@student.kit.edu");
        Warning warning = warning(target, "Please be civil");
        when(warningRepository.findById(warning.getId())).thenReturn(Optional.of(warning));
        when(warningRepository.countByStudent(target)).thenReturn(3L);

        assertThat(warnings().deleteWarning(caller(false), TARGET_ID, warning.getId()).success())
                .isTrue();

        verify(warningRepository).delete(warning);
        assertThat(before(changesWritten(), "warnings")).isEqualTo(3L);
        assertThat(after(changesWritten(), "warnings")).isEqualTo(2L);
        assertThat(metadataWritten()).containsEntry("reason", "Please be civil");
    }

    /**
     * {@code createdFrom} names who issued the warning and when. A warning whose issuing admin
     * row is gone still has to list: the history is the student's, not the administrator's.
     */
    @Test
    void getWarningsForStudent_warningWithNoIssuerStillLists() {
        Student target = student(TARGET_ID, "ada", "ada@student.kit.edu");
        Warning warning = warning(target, "Please be civil");
        warning.setAdmin(null);
        when(studentRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(warningRepository.findByStudentOrderByCreatedAtDesc(target))
                .thenReturn(List.of(warning));

        WarningHistoryResponse response = warnings().getWarningsForStudent(TARGET_ID);

        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().getFirst().createdFrom()).isNull();
    }

    /**
     * Inverted, not deleted, and the rename is the point of it. The old name said
     * {@code deletedStudent}, but what it stubbed was an empty {@code Optional} -- so what it
     * actually pinned was "nothing came back from the repository, answer 404", which is the
     * unknown-id rule and is still true. It only read as coverage of the deleted case because
     * the query it stubbed happened to be the one excluding them.
     *
     * <p>The two rules are separate now and are pinned separately, below and here: a deleted
     * account exists and lists, an unknown id does not and is 404.
     */
    @Test
    void getWarningsForStudent_unknownStudent_readsAsNotFound() {
        when(studentRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> warnings().getWarningsForStudent(TARGET_ID))
                .isInstanceOf(ApiException.class)
                .hasMessage("User not found");
    }

    /**
     * A deleted account keeps its warning history and now answers with it. The history is the
     * record of why the account was moderated, and it outlives the account for the same reason
     * the audit entry does -- the panel opens this to find out what happened, at exactly the
     * moment the account itself can no longer say.
     */
    @Test
    void getWarningsForStudent_deletedStudent_stillListsItsHistory() {
        Student target = student(TARGET_ID, "Deleted user 11111111", "deleted-x@invalid.local");
        target.setStatus(UserStatus.DELETED);
        Warning warning = warning(target, "Please be civil");
        warning.getAdmin().setStudent(student(CALLER_ID, "operator", "operator@student.kit.edu"));
        when(studentRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(warningRepository.findByStudentOrderByCreatedAtDesc(target))
                .thenReturn(List.of(warning));

        WarningHistoryResponse response = warnings().getWarningsForStudent(TARGET_ID);

        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().getFirst().message()).isEqualTo("Please be civil");
    }
}

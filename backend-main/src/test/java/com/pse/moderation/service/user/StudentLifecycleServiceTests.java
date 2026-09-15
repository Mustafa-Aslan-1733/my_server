package com.pse.moderation.service.user;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Delete, block and unblock -- the three lifecycle changes an administrator can make to an
 * account.
 *
 * <p>The class had no unit test: it is reached through {@code AdminApiIntegrationTests}, which
 * proves the routes work but says nothing about which mutations survive a mutant, and that is
 * what {@code docs/TODO.md} named it for. What is pinned here is the part an HTTP assertion
 * cannot see -- that a delete anonymises rather than removes, that the audit label is captured
 * BEFORE the scrub, and that the early return of an already-blocked account writes nothing.
 */
@ExtendWith(MockitoExtension.class)
class StudentLifecycleServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID STUDENT_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Mock private StudentRepository studentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private AuditWriter auditWriter;
    @Mock private ModeratedStudents students;

    private StudentLifecycleService service() {
        return new StudentLifecycleService(
                studentRepository, adminRepository, auditWriter, CLOCK, students);
    }

    private static AuthenticatedUser principal() {
        return new AuthenticatedUser(null, null, new Admin());
    }

    private Student target(UserStatus status) {
        Student student = new Student();
        student.setId(STUDENT_ID);
        student.setUsername("Real Name");
        student.setKitEmail("real@student.kit.edu");
        student.setBiography("something they wrote");
        student.setStatus(status);
        when(students.findMutable(STUDENT_ID)).thenReturn(student);
        return student;
    }

    // ---------- delete ----------

    /**
     * The row survives; the identity does not. Every comment, rating and vote keeps a valid
     * foreign key, which is the whole reason this is an anonymisation and not a DELETE.
     */
    @Test
    void deleteStudent_anonymisesTheAccountRatherThanRemovingIt() {
        Student student = target(UserStatus.ACTIVE);

        BasicResponse response = service().deleteStudent(principal(), STUDENT_ID);

        assertThat(response.success()).isTrue();
        assertThat(student.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(student.getUsername()).isEqualTo("Deleted user " + STUDENT_ID.toString().substring(0, 8));
        assertThat(student.getKitEmail()).isEqualTo("deleted-" + STUDENT_ID + "@invalid.local");
        assertThat(student.getBiography()).isEmpty();
        assertThat(student.getDeletedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(studentRepository).save(student);
        verify(studentRepository, never()).delete(any(Student.class));
    }

    /**
     * The label is read before the username is overwritten. Get the order wrong and every
     * deletion in the audit log reads "Deleted user 7c9e6679" -- a record of the deletion that
     * cannot say who was deleted, which is the one thing it is for.
     */
    @Test
    void deleteStudent_recordsTheIdentityTheAccountHadBeforeTheScrub() {
        target(UserStatus.ACTIVE);

        service().deleteStudent(principal(), STUDENT_ID);

        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).write(
                any(), eq(AuditAction.USER_DELETED), eq(AuditTargetType.USER), eq(STUDENT_ID),
                label.capture(), any(), any());
        assertThat(label.getValue()).contains("Real Name").doesNotContain("Deleted user");
    }

    /** A deletion ends the account's sessions; leaving them live would keep it usable. */
    @Test
    void deleteStudent_revokesTheAccountsTokens() {
        Student student = target(UserStatus.ACTIVE);

        service().deleteStudent(principal(), STUDENT_ID);

        verify(students).revokeTokens(student);
    }

    /**
     * Recorded as a lifecycle event -- {@code exists: true -> false} -- which is what makes
     * AuditRevertService refuse to invert it. The inverse of a deletion is a creation, and
     * there is nothing left to re-create.
     */
    @Test
    void deleteStudent_recordsTheChangeAsALifecycleEventSoItCannotBeReverted() {
        target(UserStatus.BLOCKED);

        service().deleteStudent(principal(), STUDENT_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> changes =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(auditWriter).write(
                any(), eq(AuditAction.USER_DELETED), any(), any(), any(), changes.capture(), any());

        assertThat(changes.getValue())
                .containsEntry("exists", Map.of("before", true, "after", false))
                .containsEntry("anonymized", Map.of("before", false, "after", true))
                .containsEntry("status",
                        Map.of("before", "BLOCKED", "after", "DELETED"));
    }

    @Test
    void deleteStudent_refusesBeforeChangingAnythingWhenTheTargetIsProtected() {
        Student student = target(UserStatus.ACTIVE);
        doThrowOnProtect(student);

        assertThatRefused(() -> service().deleteStudent(principal(), STUDENT_ID));

        assertThat(student.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(studentRepository, never()).save(any());
        verify(auditWriter, never()).write(any(), any(), any(), any(), any(), any(), any());
    }

    // ---------- block ----------

    @Test
    void blockStudent_blocksAnActiveAccountAndStampsTheTime() {
        Student student = target(UserStatus.ACTIVE);

        BasicResponse response = service().blockStudent(principal(), STUDENT_ID);

        assertThat(response.success()).isTrue();
        assertThat(student.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(student.getBlockedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(studentRepository).save(student);
        verify(students).revokeTokens(student);
        verify(auditWriter).write(
                any(), eq(AuditAction.USER_BLOCKED), any(), any(), any(), any(), any());
    }

    /**
     * Blocking an already-blocked account answers success and writes nothing. The early return
     * is what keeps the audit log free of entries recording no change -- and it also means
     * blockedAt keeps the time of the FIRST block rather than being pushed forward by a second
     * click.
     */
    @Test
    void blockStudent_alreadyBlocked_succeedsWithoutWritingOrRevokingAnything() {
        Student student = target(UserStatus.BLOCKED);
        student.setBlockedAt(LocalDateTime.now(CLOCK).minusDays(3));

        BasicResponse response = service().blockStudent(principal(), STUDENT_ID);

        assertThat(response.success()).isTrue();
        assertThat(student.getBlockedAt()).isEqualTo(LocalDateTime.now(CLOCK).minusDays(3));
        verify(studentRepository, never()).save(any());
        verify(students, never()).revokeTokens(any());
        verify(auditWriter, never()).write(any(), any(), any(), any(), any(), any(), any());
    }

    // ---------- unblock ----------

    @Test
    void unblockStudent_clearsTheBlockAndItsReason() {
        Student student = target(UserStatus.BLOCKED);
        student.setBlockedAt(LocalDateTime.now(CLOCK));
        student.setBlockedReason("spam");

        BasicResponse response = service().unblockStudent(principal(), STUDENT_ID);

        assertThat(response.success()).isTrue();
        assertThat(student.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(student.getBlockedAt()).isNull();
        assertThat(student.getBlockedReason()).isNull();
        verify(auditWriter).write(
                any(), eq(AuditAction.USER_UNBLOCKED), any(), any(), any(), any(), any());
    }

    @Test
    void unblockStudent_alreadyActive_succeedsWithoutWritingAnything() {
        target(UserStatus.ACTIVE);

        BasicResponse response = service().unblockStudent(principal(), STUDENT_ID);

        assertThat(response.success()).isTrue();
        verify(studentRepository, never()).save(any());
        verify(auditWriter, never()).write(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Unblocking does NOT revoke tokens, unlike blocking and deleting. Restoring an account and
     * then logging it out would be a contradiction, and it is easy to "tidy up" by adding the
     * call -- so the absence is asserted rather than left to be noticed.
     */
    @Test
    void unblockStudent_doesNotRevokeTokens() {
        target(UserStatus.BLOCKED);

        service().unblockStudent(principal(), STUDENT_ID);

        verify(students, never()).revokeTokens(any());
    }

    private void doThrowOnProtect(Student student) {
        org.mockito.Mockito.doThrow(new com.pse.shared.error.ApiException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Not allowed"))
                .when(students).protectAdministrator(any(), eq(student));
    }

    private static void assertThatRefused(Runnable call) {
        org.assertj.core.api.Assertions.assertThatThrownBy(call::run)
                .isInstanceOf(com.pse.shared.error.ApiException.class);
    }
}

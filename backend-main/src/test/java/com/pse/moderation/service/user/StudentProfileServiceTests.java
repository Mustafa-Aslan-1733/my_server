package com.pse.moderation.service.user;

import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import com.pse.auth.service.AdminSuperuserPolicy;
import com.pse.moderation.dto.request.UserUpdateRequest;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
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
 * The administrator's edit of a user account, and the protection matrix over it.
 *
 * <p>Split out of {@code ModerationUserServiceTests} with the code it covers.
 */
@ExtendWith(MockitoExtension.class)class StudentProfileServiceTests {

    @Mock private StudentRepository studentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private AuditWriter auditWriter;
    @Mock private AdminSuperuserPolicy superuserPolicy;


    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-07T10:15:30Z"), ZoneOffset.UTC);

    private static final UUID TARGET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CALLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private StudentProfileService profiles() {
        return new StudentProfileService(studentRepository, adminRepository, auditWriter,
                superuserPolicy, CLOCK, moderatedStudents());
    }

    /**
     * Real, not mocked: it only forwards to the repositories already mocked above, and every
     * assertion here is about what reaches those.
     */
    private ModeratedStudents moderatedStudents() {
        return new ModeratedStudents(
                studentRepository, adminRepository, tokenRepository, superuserPolicy);
    }

    // ------------------------------------------------------------------- fixtures

    private void givenTarget(Student target, boolean isAdmin, boolean isElevated) {
        givenTarget(target, isAdmin, isElevated, TARGET_ID);
    }

    private void givenTarget(Student target, boolean isAdmin, boolean isElevated, UUID id) {
        when(studentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(target));
        when(adminRepository.existsByStudent(target)).thenReturn(isAdmin);
        when(superuserPolicy.isSuperuser(target.getKitEmail())).thenReturn(isElevated);
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

    // ------------------------------------------------------------- updateStudent

    @Test
    void updateStudent_nullRequest_isRejectedBeforeTheTargetIsLookedUp() {
        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied");

        verifyNoInteractions(studentRepository, auditWriter);
    }

    @Test
    void updateStudent_everyFieldNull_isRejectedAsNoUpdate() {
        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied");

        verifyNoInteractions(studentRepository, auditWriter);
    }

    @Test
    void updateStudent_deletedTarget_readsAsNotFound() {
        Student target = student(TARGET_ID, "victim", "victim@student.kit.edu");
        target.setStatus(UserStatus.DELETED);
        when(studentRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest("newname", null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("User not found")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void updateStudent_blankUsername_isRejected(String username) {
        givenTarget(student(TARGET_ID, "victim", "victim@student.kit.edu"), false, false);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(username, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid username");
    }

    @Test
    void updateStudent_usernameAlreadyTaken_isAConflict() {
        givenTarget(student(TARGET_ID, "victim", "victim@student.kit.edu"), false, false);
        when(studentRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest("taken", null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Username is already taken")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * The uniqueness query is skipped when the username is unchanged. Worth pinning: asking
     * {@code existsByUsername} for the name the target already holds answers "taken" and
     * would refuse a request that changes nothing else about the name.
     */
    @Test
    void updateStudent_usernameResubmittedUnchanged_doesNotAskWhetherItIsTaken() {
        givenTarget(student(TARGET_ID, "victim", "victim@student.kit.edu"), false, false);

        BasicResponse response = profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest("  victim  ", null, null, null, null, null));

        assertThat(response.success()).isTrue();
        verify(studentRepository, never()).existsByUsername(any());
        verifyNoInteractions(auditWriter);
    }

    @Test
    void updateStudent_addressThatIsNotAKitAddress_isRejected() {
        givenTarget(student(TARGET_ID, "victim", "victim@student.kit.edu"), false, false);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, "victim@gmail.com", null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid KIT email");
    }

    /**
     * Elevation is keyed on the address, so moving one is how an elevated account would be
     * taken over: free the address with one edit, claim it with the next. Refused for
     * everybody, the elevated caller included — which is the one guard an elevated operator
     * does not lift, and therefore the one worth a test of its own.
     */
    @Test
    void updateStudent_addressOfAnElevatedOperator_isRefusedEvenForAnElevatedCaller() {
        Student target = student(TARGET_ID, "super", "super@student.kit.edu");
        givenTarget(target, true, true);

        assertThatThrownBy(() -> profiles().updateStudent(caller(true), TARGET_ID,
                new UserUpdateRequest(null, "moved@student.kit.edu", null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("The address of an elevated administrator cannot be changed");

        assertThat(target.getKitEmail()).isEqualTo("super@student.kit.edu");
    }

    @Test
    void updateStudent_addressOfAnAdministrator_isRefusedForAnOrdinaryCaller() {
        givenTarget(student(TARGET_ID, "admin", "admin@student.kit.edu"), true, false);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, "moved@student.kit.edu", null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Administrator accounts cannot be modified");
    }

    @Test
    void updateStudent_addressAlreadyHeldBySomebodyElse_isAConflict() {
        givenTarget(student(TARGET_ID, "victim", "victim@student.kit.edu"), false, false);
        when(studentRepository.findByKitEmail("taken@student.kit.edu"))
                .thenReturn(Optional.of(student(UUID.randomUUID(), "other", "taken@student.kit.edu")));

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, "taken@student.kit.edu", null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Email is already taken");
    }

    /**
     * A changed address revokes the sessions, because the address is the identity the login
     * flow keys on. Asserted through the token rows rather than through the response, which
     * says nothing about it.
     */
    @Test
    void updateStudent_changedAddress_revokesEverySessionAndSaysSoInTheAudit() {
        Student target = student(TARGET_ID, "victim", "victim@student.kit.edu");
        givenTarget(target, false, false);
        when(studentRepository.findByKitEmail("moved@student.kit.edu")).thenReturn(Optional.empty());
        Token token = new Token();
        when(tokenRepository.findByStudent(target)).thenReturn(List.of(token));

        profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, "  MOVED@student.KIT.edu  ", null, null, null, null));

        assertThat(target.getKitEmail()).isEqualTo("moved@student.kit.edu");
        assertThat(token.isRevoked()).isTrue();
        verify(tokenRepository).saveAll(List.of(token));
        assertThat(metadataWritten()).containsEntry("sessionsRevoked", true);
    }

    @Test
    void updateStudent_biographyLongerThanTheColumn_isRejected() {
        givenTarget(student(TARGET_ID, "victim", "victim@student.kit.edu"), false, false);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, "x".repeat(2001), null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Biography is too long");
    }

    /**
     * A null biography is recorded as {@code ""} rather than null, the same normalization
     * {@code UserRevertHandler} depends on: reporting null would make an untouched biography
     * look changed and refuse a legitimate revert.
     */
    @Test
    void updateStudent_biographyOnAnAccountThatHadNone_recordsTheBeforeValueAsEmpty() {
        Student target = student(TARGET_ID, "victim", "victim@student.kit.edu");
        target.setBiography(null);
        givenTarget(target, false, false);

        profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, "Hello", null, null, null));

        assertThat(before(changesWritten(), "biography")).isEqualTo("");
        assertThat(after(changesWritten(), "biography")).isEqualTo("Hello");
    }

    @Test
    void updateStudent_deletingThroughTheStatusField_isRefusedAndPointsAtTheRightEndpoint() {
        givenTarget(student(TARGET_ID, "victim", "victim@student.kit.edu"), false, false);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, UserStatus.DELETED, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Use DELETE /users/{id} to delete a user")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStudent_changingYourOwnStatus_isRefused() {
        Student self = student(CALLER_ID, "me", "me@student.kit.edu");
        givenTarget(self, true, false, CALLER_ID);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), CALLER_ID,
                new UserUpdateRequest(null, null, null, UserStatus.BLOCKED, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("You cannot change your own status");
    }

    @Test
    void updateStudent_blockingAnAdministrator_isRefusedForAnOrdinaryCaller() {
        givenTarget(student(TARGET_ID, "admin", "admin@student.kit.edu"), true, false);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, UserStatus.BLOCKED, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Administrator accounts cannot be modified");
    }

    @Test
    void updateStudent_blocking_stampsTheClockAndRevokesTheSessions() {
        Student target = student(TARGET_ID, "victim", "victim@student.kit.edu");
        target.setStatus(UserStatus.ACTIVE);
        givenTarget(target, false, false);
        when(tokenRepository.findByStudent(target)).thenReturn(List.of());

        profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, UserStatus.BLOCKED, null, null));

        assertThat(target.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(target.getBlockedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(metadataWritten()).containsEntry("sessionsRevoked", true);
    }

    /**
     * Unblocking is the other arm of the same branch, and it has to clear the bookkeeping
     * block set: an account back to ACTIVE that still carries a blockedAt reads as blocked
     * to anything that looks at the timestamp rather than the status.
     */
    @Test
    void updateStudent_unblocking_clearsTheBlockBookkeeping() {
        Student target = student(TARGET_ID, "victim", "victim@student.kit.edu");
        target.setStatus(UserStatus.BLOCKED);
        target.setBlockedAt(LocalDateTime.now(CLOCK).minusDays(3));
        target.setBlockedReason("spam");
        givenTarget(target, false, false);

        profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, UserStatus.ACTIVE, null, null));

        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(target.getBlockedAt()).isNull();
        assertThat(target.getBlockedReason()).isNull();
    }

    @Test
    void updateStudent_changingYourOwnRole_isRefused() {
        Student self = student(CALLER_ID, "me", "me@student.kit.edu");
        givenTarget(self, true, false, CALLER_ID);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), CALLER_ID,
                new UserUpdateRequest(null, null, null, null, UserRole.STUDENT, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("You cannot change your own role");
    }

    /**
     * Demoting the elevated operator is a lockout the API has no way back from, which is why
     * it is the one demotion an ordinary administrator cannot perform — every other
     * administrator has always been demotable by any administrator, and stays that way.
     */
    @Test
    void updateStudent_demotingTheElevatedOperator_isRefusedForAnOrdinaryCaller() {
        givenTarget(student(TARGET_ID, "super", "super@student.kit.edu"), true, true);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, null, UserRole.STUDENT, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Administrator accounts cannot be modified");
    }

    @Test
    void updateStudent_promotingToAdministrator_savesTheAdminRowAndRevokesTheSessions() {
        Student target = student(TARGET_ID, "victim", "victim@student.kit.edu");
        givenTarget(target, false, false);
        when(tokenRepository.findByStudent(target)).thenReturn(List.of());

        profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, null, UserRole.ADMIN, null));

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(saved.capture());
        assertThat(saved.getValue().getStudent()).isSameAs(target);
        assertThat(after(changesWritten(), "role")).isEqualTo("ADMIN");
        assertThat(metadataWritten()).containsEntry("sessionsRevoked", true);
    }

    /**
     * Warnings and reviewed reports point at the admin row, so the database refuses to
     * delete one that has been used. Checked up front rather than left to the constraint,
     * which would surface as an unexplained "State conflict".
     */
    @Test
    void updateStudent_demotingAnAdministratorWithModerationHistory_isRefusedWithAReason() {
        Student target = student(TARGET_ID, "admin", "admin@student.kit.edu");
        givenTarget(target, true, false);
        Admin admin = new Admin();
        when(adminRepository.findByStudent(target)).thenReturn(Optional.of(admin));
        when(adminRepository.hasModerationHistory(admin)).thenReturn(true);

        assertThatThrownBy(() -> profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, null, UserRole.STUDENT, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Administrator has moderation history and cannot be demoted");

        verify(adminRepository, never()).delete(any());
    }

    @Test
    void updateStudent_demotingAnAdministratorWithNoHistory_deletesTheAdminRow() {
        Student target = student(TARGET_ID, "admin", "admin@student.kit.edu");
        givenTarget(target, true, false);
        Admin admin = new Admin();
        when(adminRepository.findByStudent(target)).thenReturn(Optional.of(admin));
        when(adminRepository.hasModerationHistory(admin)).thenReturn(false);
        when(tokenRepository.findByStudent(target)).thenReturn(List.of());

        profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, null, UserRole.STUDENT, null));

        verify(adminRepository).delete(admin);
        assertThat(after(changesWritten(), "role")).isEqualTo("STUDENT");
    }

    @Test
    void updateStudent_credibilityScoreResubmittedUnchanged_recordsNothing() {
        Student target = student(TARGET_ID, "victim", "victim@student.kit.edu");
        target.setCredibilityScore(7);
        givenTarget(target, false, false);

        BasicResponse response = profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, null, null, null, 7));

        assertThat(response.success()).isTrue();
        verify(studentRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }

    /**
     * An edit that changes nothing must not save, must not revoke, and must not write an
     * audit entry. Every field is supplied here and every one of them matches what the
     * account already holds.
     */
    @Test
    void updateStudent_everyFieldResubmittedUnchanged_writesNothingAtAll() {
        Student target = student(TARGET_ID, "victim", "victim@student.kit.edu");
        target.setBiography("Hello");
        target.setStatus(UserStatus.ACTIVE);
        target.setCredibilityScore(3);
        givenTarget(target, false, false);

        BasicResponse response = profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest("victim", "victim@student.kit.edu", "Hello",
                        UserStatus.ACTIVE, UserRole.STUDENT, 3));

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Updated user successfully");
        verify(studentRepository, never()).save(any());
        verify(tokenRepository, never()).saveAll(any());
        verifyNoInteractions(auditWriter);
    }

    /**
     * An elevated caller acting on an administrator is marked in the audit metadata, which
     * is how the elevated path is told apart afterwards.
     */
    @Test
    void updateStudent_elevatedCallerEditingAnAdministrator_marksTheEntryAsElevated() {
        Student target = student(TARGET_ID, "admin", "admin@student.kit.edu");
        givenTarget(target, true, false);

        profiles().updateStudent(caller(true), TARGET_ID,
                new UserUpdateRequest(null, null, "Edited by an operator", null, null, null));

        assertThat(metadataWritten()).containsEntry("elevated", true);
    }

    @Test
    void updateStudent_ordinaryCallerEditingAnOrdinaryUser_doesNotMarkTheEntryAsElevated() {
        givenTarget(student(TARGET_ID, "victim", "victim@student.kit.edu"), false, false);

        profiles().updateStudent(caller(false), TARGET_ID,
                new UserUpdateRequest(null, null, "Edited", null, null, null));

        assertThat(metadataWritten()).doesNotContainKey("elevated");
    }
}

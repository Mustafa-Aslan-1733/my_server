package com.pse.auth.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * The one decision this class owns: what a request for a login code does to an account that
 * deleted itself.
 *
 * <p>Everything here is a <b>characterization</b> of behaviour that predates the class — the
 * revival itself is F-48 and is deliberately unfixed. What is new is only that the event is now
 * recorded; the tests below say what the record contains and what it deliberately does not
 * claim.
 */
@ExtendWith(MockitoExtension.class)
class AccountReactivatorTests {

    private static final UUID ACCOUNT_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AuditWriter auditWriter;

    @InjectMocks
    private AccountReactivator reactivator;

    private static Student deletedAccount() {
        Student account = new Student();
        account.setId(ACCOUNT_ID);
        account.setUsername("gone");
        account.setKitEmail("gone@student.kit.edu");
        account.setStatus(UserStatus.DELETED);
        return account;
    }

    /**
     * The instance handed in is mutated as well as saved, which is not an implementation detail:
     * {@code LoginCodeService.requestLogin} reads {@code getStatus()} off the same object below
     * the branch to decide whether a code may be issued, so an implementation that only wrote to
     * the database would leave the caller refusing to send to an account it had just revived.
     */
    @Test
    void reactivateSetsTheAccountBackToActiveAndSavesIt() {

        Student account = deletedAccount();


        reactivator.reactivate(account);


        assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(studentRepository).save(account);
    }

    /**
     * The entry is {@code USER_SELF_REACTIVATED}, not a {@code USER_UPDATED} field diff, and that
     * is a decision rather than a naming preference: {@code USER_UPDATED} is in
     * {@code AuditRevertService.REVERTIBLE_ACTIONS}, so recording the revival that way would have
     * handed an administrator a working undo that flips the account back to {@code DELETED} —
     * a restore route arriving through the back door of the revert machinery, which
     * {@code docs/adr/0015-deletion-not-split-from-anonymisation.md} refuses.
     *
     * <p>The actor is the account itself. Nobody identified themselves — that is F-48 — so this
     * is the closest true statement available, and {@code callerAuthenticated: false} says the
     * rest in every row rather than in a comment. The label is the one
     * {@code UserResponseMapper.label} produces, because the admin panel recovers a deleted
     * account's identity from {@code target_label}.
     */
    @Test
    void reactivateRecordsTheRevivalAgainstTheAccountAndSaysNobodyProvedTheyOwnedIt() {

        Student account = deletedAccount();


        reactivator.reactivate(account);


        verify(auditWriter).writeStudentAction(
                eq(account),
                eq(AuditAction.USER_SELF_REACTIVATED),
                eq(AuditTargetType.USER),
                eq(ACCOUNT_ID),
                eq("gone (gone@student.kit.edu)"),
                eq(Map.of("trigger", "LOGIN_CODE_REQUEST", "callerAuthenticated", false))
        );
    }
}

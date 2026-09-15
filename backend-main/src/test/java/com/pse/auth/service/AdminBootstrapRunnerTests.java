package com.pse.auth.service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The startup provisioning of configured administrators.
 *
 * <p>Covered only through {@code AdminApiIntegrationTests} until now, which starts a context
 * with one configuration and therefore exercises one path through this loop. The properties
 * worth stating are the ones a single happy start cannot show: that it is idempotent, that it
 * only ever ADDS -- never demoting or reactivating -- and that an empty configuration warns
 * instead of quietly leaving an API nobody can reach.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTests {

    @Mock private AdminBootstrapPolicy policy;
    @Mock private StudentRepository studentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private StudentProvisioning studentProvisioning;

    private AdminBootstrapRunner runner() {
        return new AdminBootstrapRunner(
                policy, studentRepository, adminRepository, studentProvisioning);
    }

    private static Student student(String email) {
        Student student = new Student();
        student.setKitEmail(email);
        return student;
    }

    /**
     * No configuration is not an error the runner can fix, so it says so and stops. Falling
     * through would be worse than useless: it would log a successful bootstrap of nobody.
     */
    @Test
    void run_noConfiguredEmails_provisionsNothing() {
        when(policy.bootstrapEmails()).thenReturn(Set.of());

        runner().run(null);

        verifyNoInteractions(studentRepository, adminRepository, studentProvisioning);
    }

    @Test
    void run_emailWithNoAccount_createsTheAccountAndPromotesIt() {
        when(policy.bootstrapEmails()).thenReturn(Set.of("chief@student.kit.edu"));
        when(policy.displayName("chief@student.kit.edu")).thenReturn("Chief");
        when(studentRepository.findByKitEmail("chief@student.kit.edu")).thenReturn(Optional.empty());
        Student created = student("chief@student.kit.edu");
        when(studentProvisioning.create("chief@student.kit.edu", "Chief")).thenReturn(created);
        when(adminRepository.existsByStudent(created)).thenReturn(false);

        runner().run(null);

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(saved.capture());
        assertThat(saved.getValue().getStudent()).isSameAs(created);
    }

    /**
     * The account already exists -- somebody signed up before being configured as an admin --
     * so it is promoted in place. Creating a second one would collide on the unique email and
     * fail the whole startup.
     */
    @Test
    void run_existingAccountThatIsNotYetAnAdmin_promotesItWithoutCreatingAnAccount() {
        Student existing = student("already@student.kit.edu");
        when(policy.bootstrapEmails()).thenReturn(Set.of("already@student.kit.edu"));
        when(studentRepository.findByKitEmail("already@student.kit.edu"))
                .thenReturn(Optional.of(existing));
        when(adminRepository.existsByStudent(existing)).thenReturn(false);

        runner().run(null);

        verify(studentProvisioning, never()).create(anyString(), anyString());
        verify(adminRepository).save(any(Admin.class));
    }

    /** Idempotent: a second start over the same database must write nothing. */
    @Test
    void run_accountThatIsAlreadyAnAdmin_writesNothing() {
        Student existing = student("admin@student.kit.edu");
        when(policy.bootstrapEmails()).thenReturn(Set.of("admin@student.kit.edu"));
        when(studentRepository.findByKitEmail("admin@student.kit.edu"))
                .thenReturn(Optional.of(existing));
        when(adminRepository.existsByStudent(existing)).thenReturn(true);

        runner().run(null);

        verify(studentProvisioning, never()).create(anyString(), anyString());
        verify(adminRepository, never()).save(any());
    }

    /**
     * It never reactivates. A blocked or deleted account that is still in the configuration
     * keeps the status an administrator gave it -- the runner adds an {@code admins} row and
     * touches nothing on the student, which is what stops a redeploy from quietly undoing a
     * moderation decision.
     */
    @Test
    void run_blockedAccountStillConfigured_isNotReactivated() {
        Student blocked = student("blocked@student.kit.edu");
        blocked.setStatus(com.pse.shared.enums.UserStatus.BLOCKED);
        when(policy.bootstrapEmails()).thenReturn(Set.of("blocked@student.kit.edu"));
        when(studentRepository.findByKitEmail("blocked@student.kit.edu"))
                .thenReturn(Optional.of(blocked));
        when(adminRepository.existsByStudent(blocked)).thenReturn(true);

        runner().run(null);

        assertThat(blocked.getStatus()).isEqualTo(com.pse.shared.enums.UserStatus.BLOCKED);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void run_severalEmails_provisionsEveryOneOfThem() {
        Set<String> emails = new LinkedHashSet<>(Set.of("a@student.kit.edu", "b@student.kit.edu"));
        when(policy.bootstrapEmails()).thenReturn(emails);
        for (String email : emails) {
            Student existing = student(email);
            when(studentRepository.findByKitEmail(email)).thenReturn(Optional.of(existing));
            when(adminRepository.existsByStudent(existing)).thenReturn(false);
        }

        runner().run(null);

        verify(adminRepository, org.mockito.Mockito.times(2)).save(any(Admin.class));
    }
}

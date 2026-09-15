package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.moderation.dto.request.UserUpdateRequest;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.service.user.StudentProfileService;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
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
 * The widest handler: six fields, and the only one whose reported value set is assembled
 * from two tables -- the student row plus the admin id list, because a student's role is
 * not a column on the student.
 */
@ExtendWith(MockitoExtension.class)
class UserRevertHandlerTests {

    private static final UUID FIRST = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final UUID SECOND = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(null, null, null);

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private StudentProfileService profiles;

    private UserRevertHandler handler() {
        return new UserRevertHandler(studentRepository, adminRepository, profiles);
    }

    private static Student student(UUID id) {
        Student student = new Student();
        student.setId(id);
        student.setUsername("ada");
        student.setKitEmail("ada@kit.edu");
        student.setBiography("Writes notes.");
        student.setStatus(UserStatus.ACTIVE);
        student.setCredibilityScore(7);
        return student;
    }

    private UserUpdateRequest replayed(UUID targetId, Map<String, Object> before) {
        handler().applyInverse(PRINCIPAL, targetId, before);
        ArgumentCaptor<UserUpdateRequest> request =
                ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(profiles).updateStudent(eq(PRINCIPAL), eq(targetId), request.capture());
        return request.getValue();
    }

    @Test
    void targetType_isUser() {
        // When / Then
        assertThat(handler().targetType()).isEqualTo(AuditTargetType.USER);
    }

    // ---------- currentValues ----------

    @Test
    void currentValues_studentThatExists_mapsEveryFieldTheHandlerCanRevert() {
        // Given
        when(adminRepository.findAllAdminStudentIds()).thenReturn(List.of());
        when(studentRepository.findAllById(List.of(FIRST))).thenReturn(List.of(student(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST));

        // Then
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(
                entry("username", "ada"),
                entry("kitEmail", "ada@kit.edu"),
                entry("biography", "Writes notes."),
                entry("status", "ACTIVE"),
                entry("role", "STUDENT"),
                entry("credibilityScore", 7)
        );
    }

    /**
     * A role is not a column on the student, so it is reconstructed from the admin id list.
     * Getting this arm wrong would report every admin as a plain student, and a revert of a
     * role change would then quietly demote them.
     */
    @Test
    void currentValues_studentWhoIsAlsoAnAdmin_reportsTheAdminRole() {
        // Given
        when(adminRepository.findAllAdminStudentIds()).thenReturn(List.of(FIRST));
        when(studentRepository.findAllById(List.of(FIRST))).thenReturn(List.of(student(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST));

        // Then
        assertThat(values.get(FIRST)).containsEntry("role", UserRole.ADMIN.name());
    }

    /**
     * An empty biography is recorded as {@code ""} rather than null, and that normalisation
     * is load-bearing: the audit entry stores {@code ""} too, so reporting null here would
     * make an untouched biography compare as a changed value and refuse the revert.
     */
    @Test
    void currentValues_nullBiography_isReportedAsAnEmptyString() {
        // Given
        Student student = student(FIRST);
        student.setBiography(null);
        when(adminRepository.findAllAdminStudentIds()).thenReturn(List.of());
        when(studentRepository.findAllById(List.of(FIRST))).thenReturn(List.of(student));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST));

        // Then
        assertThat(values.get(FIRST)).containsEntry("biography", "");
    }

    /**
     * A soft-deleted account is anonymised -- its recorded username and address are already
     * gone -- so it has to read as a missing target rather than a stale one. Reporting it
     * would offer a revert that writes the anonymised values back as if they were real.
     */
    @Test
    void currentValues_softDeletedStudent_isAbsentRatherThanReportedStale() {
        // Given
        Student deleted = student(SECOND);
        deleted.setStatus(UserStatus.DELETED);
        when(adminRepository.findAllAdminStudentIds()).thenReturn(List.of());
        when(studentRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(student(FIRST), deleted));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values).doesNotContainKey(SECOND);
    }

    /** An id with no surviving row is how {@code TARGET_MISSING} is detected upstream. */
    @Test
    void currentValues_idWithNoSurvivingRow_isAbsentFromTheResult() {
        // Given
        when(adminRepository.findAllAdminStudentIds()).thenReturn(List.of());
        when(studentRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(student(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(FIRST);
    }

    /**
     * F-12. The admin id list is only needed to label the rows, so asking for it before
     * knowing whether there are any was a query issued for nothing. It was harmless -- the
     * revertibility pass only calls this with a non-empty id set -- which is why it stayed
     * open as long as it did; the assertion is inverted rather than deleted so the order of
     * the two queries cannot drift back.
     */
    @Test
    void currentValues_noTargets_doesNotQueryTheAdminIdList() {
        // Given
        when(studentRepository.findAllById(List.of())).thenReturn(List.of());

        // When / Then
        assertThat(handler().currentValues(List.of())).isEmpty();
        verifyNoInteractions(adminRepository);
        verifyNoInteractions(profiles);
    }

    // ---------- applyInverse ----------

    /**
     * The handler does not write fields itself. It calls the method the panel calls, so the
     * revert inherits that path's validation -- including the guard that stops an admin
     * being demoted by accident -- and writes its own audit event.
     */
    @Test
    void applyInverse_recordedValues_replaysThemThroughUpdateStudent() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("username", "ada.l");
        before.put("kitEmail", "ada.l@kit.edu");
        before.put("biography", "");
        before.put("status", "BLOCKED");
        before.put("role", "ADMIN");
        before.put("credibilityScore", 3);

        // When
        UserUpdateRequest request = replayed(FIRST, before);

        // Then
        assertThat(request.username()).isEqualTo("ada.l");
        assertThat(request.kitEmail()).isEqualTo("ada.l@kit.edu");
        assertThat(request.biography()).isEmpty();
        assertThat(request.status()).isEqualTo(UserStatus.BLOCKED);
        assertThat(request.role()).isEqualTo(UserRole.ADMIN);
        assertThat(request.credibilityScore()).isEqualTo(3);
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
        before.put("biography", "Original bio.");

        // When
        UserUpdateRequest request = replayed(FIRST, before);

        // Then
        assertThat(request.biography()).isEqualTo("Original bio.");
        assertThat(request.username()).isNull();
        assertThat(request.kitEmail()).isNull();
        assertThat(request.status()).isNull();
        assertThat(request.role()).isNull();
        assertThat(request.credibilityScore()).isNull();
    }
}

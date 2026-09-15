package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.moderation.dto.request.WarningRequest;
import com.pse.moderation.model.Warning;
import com.pse.moderation.repository.WarningRepository;
import com.pse.moderation.service.user.WarningService;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The only handler that does a lookup of its own inside {@code applyInverse}: a warning is
 * addressed as a sub-resource of the student it was issued to, so the owning student has to
 * be resolved before the correction can be applied. That makes it also the only handler
 * that can refuse.
 */
@ExtendWith(MockitoExtension.class)
class WarningRevertHandlerTests {

    private static final UUID FIRST = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final UUID SECOND = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID STUDENT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(null, null, null);

    @Mock
    private WarningRepository warningRepository;

    @Mock
    private WarningService warnings;

    private WarningRevertHandler handler() {
        return new WarningRevertHandler(warningRepository, warnings);
    }

    private static Warning warning(UUID id) {
        Student student = new Student();
        student.setId(STUDENT);
        Warning warning = new Warning();
        warning.setId(id);
        warning.setMessage("Please keep it civil.");
        warning.setStudent(student);
        return warning;
    }

    @Test
    void targetType_isWarning() {
        // When / Then
        assertThat(handler().targetType()).isEqualTo(AuditTargetType.WARNING);
    }

    // ---------- currentValues ----------

    @Test
    void currentValues_warningThatExists_mapsOnlyTheMessage() {
        // Given
        when(warningRepository.findAllById(List.of(FIRST))).thenReturn(List.of(warning(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST));

        // Then -- the text is the only revertible field; who it was issued to is not editable
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(entry("message", "Please keep it civil."));
    }

    /** An id with no surviving row is how {@code TARGET_MISSING} is detected upstream. */
    @Test
    void currentValues_withdrawnWarning_isAbsentFromTheResult() {
        // Given
        when(warningRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(warning(SECOND)));

        // When
        Map<UUID, Map<String, Object>> values = handler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(SECOND);
        assertThat(values).doesNotContainKey(FIRST);
    }

    @Test
    void currentValues_noTargets_returnsAnEmptyMap() {
        // Given
        when(warningRepository.findAllById(List.of())).thenReturn(List.of());

        // When / Then
        assertThat(handler().currentValues(List.of())).isEmpty();
        verifyNoInteractions(warnings);
    }

    // ---------- applyInverse ----------

    /**
     * The student id in the call comes from the warning row, not from the audit entry -- the
     * entry records the warning's fields, and the owner is not one of them.
     */
    @Test
    void applyInverse_recordedMessage_replaysItAgainstTheOwningStudent() {
        // Given
        when(warningRepository.findById(FIRST)).thenReturn(Optional.of(warning(FIRST)));
        Map<String, Object> before = new HashMap<>();
        before.put("message", "Original wording.");

        // When
        handler().applyInverse(PRINCIPAL, FIRST, before);

        // Then
        ArgumentCaptor<WarningRequest> request = ArgumentCaptor.forClass(WarningRequest.class);
        verify(warnings).updateWarning(
                eq(PRINCIPAL), eq(STUDENT), eq(FIRST), request.capture());
        assertThat(request.getValue().message()).isEqualTo("Original wording.");
    }

    /**
     * A warning withdrawn between the revertibility check and the revert itself -- F-11.
     * Nothing holds a lock in between, and this is the only handler that runs its own query
     * inside {@code applyInverse}, so the row can be gone by the time the revert runs.
     *
     * <p>Refusing was always the right answer; what the finding was about is what the
     * refusal said. It carries the same code and the same wording the revertibility check
     * uses for this situation now, so the panel can tell the operator the entry can no
     * longer be reverted rather than reporting a missing record on an entry it had just
     * shown as revertible.
     */
    @Test
    void applyInverse_warningWithdrawnSinceTheEntry_refusesAsAMissingTarget() {
        // Given
        when(warningRepository.findById(FIRST)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> handler().applyInverse(PRINCIPAL, FIRST, new HashMap<>()))
                .isInstanceOf(ApiException.class)
                .hasMessage("The record this entry is about no longer exists")
                .satisfies(exception -> {
                    assertThat(((ApiException) exception).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(((ApiException) exception).getCode())
                            .isEqualTo(AuditRevertRefusal.TARGET_MISSING.name());
                });
        verifyNoInteractions(warnings);
    }
}

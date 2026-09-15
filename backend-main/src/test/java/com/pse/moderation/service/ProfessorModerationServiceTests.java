package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.dto.request.ProfessorUpdateRequest;
import com.pse.moderation.model.Admin;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.model.LectureType;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Editing a professor, including the lecture reassignment that comes with it.
 *
 * <p>Split out of {@code ModerationCatalogServiceTests} with the code it covers.
 */
@ExtendWith(MockitoExtension.class)
class ProfessorModerationServiceTests {

    private static final UUID LECTURE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID PROFESSOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private LectureRepository lectureRepository;
    @Mock private ProfessorRepository professorRepository;
    @Mock private AuditWriter auditWriter;

    private ProfessorModerationService service() {
        return new ProfessorModerationService(
                professorRepository, lectureRepository, auditWriter);
    }


    private void givenProfessor(Professor professor) {
        when(professorRepository.findById(PROFESSOR_ID)).thenReturn(Optional.of(professor));
    }


    private static Lecture lecture() {
        Lecture lecture = new Lecture();
        lecture.setId(LECTURE_ID);
        lecture.setName("Algorithmen 1");
        lecture.setCode("ALG1");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setLectureType(LectureType.LECTURE_ONLY);
        lecture.setActive(true);
        lecture.setProfessors(new LinkedHashSet<>());
        return lecture;
    }


    private static Professor professor(UUID id, String firstName, String lastName) {
        Professor professor = new Professor();
        professor.setId(id);
        professor.setFirstName(firstName);
        professor.setLastName(lastName);
        professor.setLectures(new LinkedHashSet<>());
        return professor;
    }


    private static AuthenticatedUser principal() {
        return new AuthenticatedUser(null, null, new Admin());
    }


    /** The changes map handed to the audit writer, which is where the field diff lands. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> changesWritten() {
        ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter).write(any(), any(), any(), any(), any(), changes.capture(), any());
        return changes.getValue();
    }


    @SuppressWarnings("unchecked")
    private static Object before(Map<String, Object> changes, String field) {
        return ((Map<String, Object>) changes.get(field)).get("before");
    }


    @SuppressWarnings("unchecked")
    private static Object after(Map<String, Object> changes, String field) {
        return ((Map<String, Object>) changes.get(field)).get("after");
    }


    // -------------------------------------------------------------- updateProfessor

    @Test
    void updateProfessor_nullRequest_isRejectedBeforeTheProfessorIsLookedUp() {
        assertThatThrownBy(() -> service().updateProfessor(principal(), PROFESSOR_ID, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied");

        verifyNoInteractions(professorRepository, auditWriter);
    }


    @Test
    void updateProfessor_everyFieldNull_isRejectedAsNoUpdate() {
        assertThatThrownBy(() -> service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest(null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied");

        verifyNoInteractions(professorRepository, auditWriter);
    }


    @Test
    void updateProfessor_unknownProfessor_isNotFound() {
        when(professorRepository.findById(PROFESSOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest("Peter", null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Professor not found");
    }


    @Test
    void updateProfessor_blankOrOverlongNames_areRejectedPerField() {
        givenProfessor(professor(PROFESSOR_ID, "Peter", "Sanders"));

        assertThatThrownBy(() -> service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest("  ", null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid first name");

        assertThatThrownBy(() -> service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest(null, "x".repeat(201), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid last name");
    }


    @Test
    void updateProfessor_namesResubmittedUnchanged_recordNothing() {
        givenProfessor(professor(PROFESSOR_ID, "Peter", "Sanders"));

        BasicResponse response = service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest("Peter", "Sanders", null, null));

        assertThat(response.success()).isTrue();
        verify(professorRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }


    @Test
    void updateProfessor_changedNamesAndActiveFlag_areRecordedAndSaved() {
        Professor professor = professor(PROFESSOR_ID, "Peter", "Sanders");
        professor.setActive(true);
        givenProfessor(professor);

        service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest("Dorothea", "Wagner", false, null));

        Map<String, Object> changes = changesWritten();
        assertThat(changes).containsOnlyKeys("firstName", "lastName", "active");
        assertThat(after(changes, "firstName")).isEqualTo("Dorothea");
        assertThat(after(changes, "lastName")).isEqualTo("Wagner");
        assertThat(after(changes, "active")).isEqualTo(false);
        verify(professorRepository).save(professor);
    }


    @Test
    void updateProfessor_lectureIdThatDoesNotResolve_isNotFound() {
        givenProfessor(professor(PROFESSOR_ID, "Peter", "Sanders"));
        UUID missing = UUID.randomUUID();
        when(lectureRepository.findAllById(List.of(missing))).thenReturn(List.of());

        assertThatThrownBy(() -> service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest(null, null, null, List.of(missing))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Lecture not found");
    }


    @Test
    void updateProfessor_sameLectureAssignment_recordsNothingAndTouchesNoLecture() {
        Professor professor = professor(PROFESSOR_ID, "Peter", "Sanders");
        Lecture held = lecture();
        professor.setLectures(new LinkedHashSet<>(List.of(held)));
        givenProfessor(professor);
        when(lectureRepository.findAllById(List.of(LECTURE_ID))).thenReturn(List.of(held));

        BasicResponse response = service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest(null, null, null, List.of(LECTURE_ID)));

        assertThat(response.success()).isTrue();
        verify(lectureRepository, never()).save(any());
        verify(professorRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }


    /**
     * The join table is owned by {@code Lecture}, so a reassignment has to be written from the
     * lecture side: the lecture being dropped is saved without the professor, the lecture being
     * added is saved with it. Verified on both lectures rather than only on the outcome,
     * because a reassignment that updates the professor's own collection and forgets the owning
     * side leaves the database unchanged while every in-memory assertion passes.
     */
    @Test
    void updateProfessor_reassignedLectures_areWrittenFromTheOwningSide() {
        Professor professor = professor(PROFESSOR_ID, "Peter", "Sanders");
        Lecture dropped = lecture();
        dropped.setProfessors(new LinkedHashSet<>(List.of(professor)));
        Lecture added = lecture();
        added.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        added.setName("Algorithmen 2");

        professor.setLectures(new LinkedHashSet<>(List.of(dropped)));
        givenProfessor(professor);
        when(lectureRepository.findAllById(List.of(added.getId()))).thenReturn(List.of(added));

        service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest(null, null, null, List.of(added.getId())));

        assertThat(dropped.getProfessors()).doesNotContain(professor);
        assertThat(added.getProfessors()).contains(professor);
        assertThat(professor.getLectures()).containsExactly(added);
        verify(lectureRepository).save(dropped);
        verify(lectureRepository).save(added);

        assertThat(changesWritten()).containsKey("lectures");
        assertThat(before(changesWritten(), "lectures")).isEqualTo(List.of(LECTURE_ID.toString()));
        assertThat(after(changesWritten(), "lectures")).isEqualTo(List.of(added.getId().toString()));
    }


    @Test
    void updateProfessor_lectureAlreadyCarryingTheProfessor_isNotSavedTwice() {
        Professor professor = professor(PROFESSOR_ID, "Peter", "Sanders");
        Lecture kept = lecture();
        kept.setProfessors(new LinkedHashSet<>(List.of(professor)));
        Lecture added = lecture();
        added.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        // Already holds the professor, so the attach loop must skip it.
        added.setProfessors(new LinkedHashSet<>(List.of(professor)));

        professor.setLectures(new LinkedHashSet<>(List.of(kept)));
        givenProfessor(professor);
        when(lectureRepository.findAllById(anyList())).thenReturn(List.of(kept, added));

        service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest(null, null, null, List.of(kept.getId(), added.getId())));

        verify(lectureRepository, never()).save(added);
        assertThat(professor.getLectures()).containsExactly(kept, added);
    }


    @Test
    void updateProfessor_changedFields_areAuditedAsOneProfessorUpdatedEntry() {
        givenProfessor(professor(PROFESSOR_ID, "Peter", "Sanders"));

        service().updateProfessor(principal(), PROFESSOR_ID,
                new ProfessorUpdateRequest("Dorothea", null, null, null));

        ArgumentCaptor<AuditAction> action = ArgumentCaptor.forClass(AuditAction.class);
        ArgumentCaptor<AuditTargetType> targetType = ArgumentCaptor.forClass(AuditTargetType.class);
        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).write(any(), action.capture(), targetType.capture(),
                any(), label.capture(), any(), any());

        assertThat(action.getValue()).isEqualTo(AuditAction.PROFESSOR_UPDATED);
        assertThat(targetType.getValue()).isEqualTo(AuditTargetType.PROFESSOR);
        assertThat(label.getValue()).isEqualTo("Dorothea Sanders");
    }
}

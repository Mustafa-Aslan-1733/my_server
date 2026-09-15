package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.dto.request.LectureUpdateRequest;
import com.pse.moderation.dto.request.ProfessorUpdateRequest;
import com.pse.moderation.service.LectureModerationService;
import com.pse.moderation.service.ProfessorModerationService;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.model.LectureType;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.SemesterSeason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lectures and professors are separate target types sharing one source file, and they are
 * the only handlers that report a *collection* field -- the mutual assignment. That is
 * where the interesting failure lives: the entity declares the assignment as an unordered
 * {@link Set}, the handler reports it as an ordered {@code List}, and
 * {@link RevertValues#sameValue} compares lists position by position. See BUG-3.
 */
@ExtendWith(MockitoExtension.class)
class CatalogueRevertHandlerTests {

    private static final UUID FIRST = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    private static final UUID SECOND = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID PROFESSOR_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFESSOR_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID LECTURE_A = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(null, null, null);

    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private ProfessorRepository professorRepository;

    @Mock
    private LectureModerationService lectureService;

    @Mock
    private ProfessorModerationService professorService;

    private CatalogueRevertHandler.LectureHandler lectureHandler() {
        return new CatalogueRevertHandler.LectureHandler(lectureRepository, lectureService);
    }

    private CatalogueRevertHandler.ProfessorHandler professorHandler() {
        return new CatalogueRevertHandler.ProfessorHandler(professorRepository, professorService);
    }

    private static Professor professor(UUID id) {
        Professor professor = new Professor();
        professor.setId(id);
        professor.setFirstName("Ada");
        professor.setLastName("Lovelace");
        professor.setActive(true);
        return professor;
    }

    private static Lecture lecture(UUID id) {
        Lecture lecture = new Lecture();
        lecture.setId(id);
        lecture.setName("Programmierparadigmen");
        lecture.setCode("IN1234");
        lecture.setSemesterYear(2025);
        lecture.setSemesterSeason(SemesterSeason.WS);
        lecture.setActive(true);
        lecture.setLectureType(LectureType.LECTURE_AND_EXERCISE);
        return lecture;
    }

    /** An ordered set, so the order the handler reports is the order the fixture declares. */
    private static Set<Professor> orderedProfessors(UUID... ids) {
        Set<Professor> professors = new LinkedHashSet<>();
        for (UUID id : ids) {
            professors.add(professor(id));
        }
        return professors;
    }

    private LectureUpdateRequest replayedLecture(UUID targetId, Map<String, Object> before) {
        lectureHandler().applyInverse(PRINCIPAL, targetId, before);
        ArgumentCaptor<LectureUpdateRequest> request =
                ArgumentCaptor.forClass(LectureUpdateRequest.class);
        verify(lectureService).updateLecture(eq(PRINCIPAL), eq(targetId), request.capture());
        return request.getValue();
    }

    private ProfessorUpdateRequest replayedProfessor(UUID targetId, Map<String, Object> before) {
        professorHandler().applyInverse(PRINCIPAL, targetId, before);
        ArgumentCaptor<ProfessorUpdateRequest> request =
                ArgumentCaptor.forClass(ProfessorUpdateRequest.class);
        verify(professorService).updateProfessor(eq(PRINCIPAL), eq(targetId), request.capture());
        return request.getValue();
    }

    // ---------- LectureHandler.targetType ----------

    @Test
    void lectureHandler_targetType_isLecture() {
        // When / Then
        assertThat(lectureHandler().targetType()).isEqualTo(AuditTargetType.LECTURE);
    }

    // ---------- LectureHandler.currentValues ----------

    @Test
    void lectureHandler_currentValues_mapsEveryFieldTheHandlerCanRevert() {
        // Given
        Lecture lecture = lecture(FIRST);
        lecture.setProfessors(orderedProfessors(PROFESSOR_A));
        when(lectureRepository.findAllById(List.of(FIRST))).thenReturn(List.of(lecture));

        // When
        Map<UUID, Map<String, Object>> values = lectureHandler().currentValues(List.of(FIRST));

        // Then -- enums by name, the assignment as UUID strings, because that is how they
        // survived the JSON column
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(
                entry("name", "Programmierparadigmen"),
                entry("code", "IN1234"),
                entry("semesterYear", 2025),
                entry("semesterSeason", "WS"),
                entry("active", true),
                entry("lectureType", "LECTURE_AND_EXERCISE"),
                entry("professors", List.of(PROFESSOR_A.toString()))
        );
    }

    @Test
    void lectureHandler_currentValues_lectureWithNoProfessors_recordsAnEmptyList() {
        // Given
        when(lectureRepository.findAllById(List.of(FIRST))).thenReturn(List.of(lecture(FIRST)));

        // When
        Map<UUID, Map<String, Object>> values = lectureHandler().currentValues(List.of(FIRST));

        // Then -- an empty list, not a missing key: the field was recorded, it is just empty
        assertThat(values.get(FIRST)).containsEntry("professors", List.of());
    }

    /** An id with no surviving row is how {@code TARGET_MISSING} is detected upstream. */
    @Test
    void lectureHandler_currentValues_deletedLecture_isAbsentFromTheResult() {
        // Given
        when(lectureRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(lecture(SECOND)));

        // When
        Map<UUID, Map<String, Object>> values =
                lectureHandler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(SECOND);
        assertThat(values).doesNotContainKey(FIRST);
    }

    @Test
    void lectureHandler_currentValues_noTargets_returnsAnEmptyMap() {
        // Given
        when(lectureRepository.findAllById(List.of())).thenReturn(List.of());

        // When / Then
        assertThat(lectureHandler().currentValues(List.of())).isEmpty();
        verifyNoInteractions(lectureService, professorService);
    }

    /**
     * BUG-3, first half. {@code Lecture.professors} is a {@link Set} -- a type that does not
     * define an order -- but the handler reports it as an ordered list, so whatever order
     * the collection happens to iterate in becomes part of the recorded value.
     *
     * <p>The fixture uses a {@link LinkedHashSet} on purpose. {@link Professor} overrides
     * neither {@code equals} nor {@code hashCode}, so a real {@code HashSet} buckets by
     * identity hash and its order varies from run to run; a test that tried to force that
     * order would be flaky, and the flakiness would be read as a broken test rather than a
     * broken handler. Reporting an order at all is the defect, and that is what this pins.
     */
    @Test
    void lectureHandler_currentValues_professorAssignment_carriesTheOrderOfAnUnorderedSet() {
        // Given -- the same two professors, declared in the two possible orders
        Lecture forward = lecture(FIRST);
        forward.setProfessors(orderedProfessors(PROFESSOR_A, PROFESSOR_B));
        Lecture reversed = lecture(SECOND);
        reversed.setProfessors(orderedProfessors(PROFESSOR_B, PROFESSOR_A));
        when(lectureRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(forward, reversed));

        // When
        Map<UUID, Map<String, Object>> values =
                lectureHandler().currentValues(List.of(FIRST, SECOND));

        // Then -- two different recorded values for the same assignment
        assertThat(values.get(FIRST)).containsEntry(
                "professors", List.of(PROFESSOR_A.toString(), PROFESSOR_B.toString()));
        assertThat(values.get(SECOND)).containsEntry(
                "professors", List.of(PROFESSOR_B.toString(), PROFESSOR_A.toString()));
    }

    /**
     * BUG-3, second half: the handler still hands an ordered list to the staleness check --
     * the test above pins that -- but the check no longer reads anything into the order. The
     * two readings above are the same recorded value, so a lecture whose assignment nobody
     * touched can be reverted whichever way its {@code Set} happened to iterate.
     *
     * <p>This is the assertion that made the defect deterministic to begin with, which is
     * why it stays: it does not depend on real {@code HashSet} order, so it could not be
     * flaky in either direction.
     */
    @Test
    void lectureHandler_professorAssignmentInAnotherOrder_comparesAsUnchangedByRevertValues() {
        // Given
        List<String> recorded = List.of(PROFESSOR_A.toString(), PROFESSOR_B.toString());
        List<String> current = List.of(PROFESSOR_B.toString(), PROFESSOR_A.toString());

        // When / Then -- equal as sets, and now equal to the check that actually runs
        assertThat(Set.copyOf(recorded)).isEqualTo(Set.copyOf(current));
        assertThat(RevertValues.sameValue(recorded, current)).isTrue();
    }

    // ---------- LectureHandler.applyInverse ----------

    /**
     * The handler does not write fields itself. It calls the method the panel calls, so the
     * revert inherits that path's validation and writes its own audit event.
     */
    @Test
    void lectureHandler_applyInverse_recordedValues_replaysThemThroughUpdateLecture() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("name", "Programmieren");
        before.put("code", "IN1000");
        before.put("semesterYear", 2024);
        before.put("semesterSeason", "SS");
        before.put("active", false);
        before.put("lectureType", "LECTURE_ONLY");
        before.put("professors", List.of(PROFESSOR_A.toString(), PROFESSOR_B.toString()));

        // When
        LectureUpdateRequest request = replayedLecture(FIRST, before);

        // Then -- note the recorded key is "professors" while the request field is professorIds
        assertThat(request.name()).isEqualTo("Programmieren");
        assertThat(request.code()).isEqualTo("IN1000");
        assertThat(request.semesterYear()).isEqualTo(2024);
        assertThat(request.semesterSeason()).isEqualTo(SemesterSeason.SS);
        assertThat(request.active()).isFalse();
        assertThat(request.lectureType()).isEqualTo(LectureType.LECTURE_ONLY);
        assertThat(request.professorIds()).containsExactly(PROFESSOR_A, PROFESSOR_B);
    }

    /**
     * Only the edited fields are in an entry's {@code before} half. The rest arrive as null,
     * which the update service reads as "leave alone" -- so a revert touches exactly the
     * fields the original edit touched.
     */
    @Test
    void lectureHandler_applyInverse_beforeHalfCarryingOneField_leavesTheOthersNull() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("name", "Programmieren");

        // When
        LectureUpdateRequest request = replayedLecture(FIRST, before);

        // Then
        assertThat(request.name()).isEqualTo("Programmieren");
        assertThat(request.code()).isNull();
        assertThat(request.semesterYear()).isNull();
        assertThat(request.semesterSeason()).isNull();
        assertThat(request.active()).isNull();
        assertThat(request.lectureType()).isNull();
        assertThat(request.professorIds()).isNull();
    }

    // ---------- ProfessorHandler ----------

    @Test
    void professorHandler_targetType_isProfessor() {
        // When / Then
        assertThat(professorHandler().targetType()).isEqualTo(AuditTargetType.PROFESSOR);
    }

    @Test
    void professorHandler_currentValues_mapsEveryFieldTheHandlerCanRevert() {
        // Given
        Professor professor = professor(FIRST);
        Lecture lecture = lecture(LECTURE_A);
        professor.setLectures(new LinkedHashSet<>(List.of(lecture)));
        when(professorRepository.findAllById(List.of(FIRST))).thenReturn(List.of(professor));

        // When
        Map<UUID, Map<String, Object>> values = professorHandler().currentValues(List.of(FIRST));

        // Then
        assertThat(values).containsOnlyKeys(FIRST);
        assertThat(values.get(FIRST)).containsExactly(
                entry("firstName", "Ada"),
                entry("lastName", "Lovelace"),
                entry("active", true),
                entry("lectures", List.of(LECTURE_A.toString()))
        );
    }

    @Test
    void professorHandler_currentValues_deletedProfessor_isAbsentFromTheResult() {
        // Given
        when(professorRepository.findAllById(List.of(FIRST, SECOND)))
                .thenReturn(List.of(professor(SECOND)));

        // When
        Map<UUID, Map<String, Object>> values =
                professorHandler().currentValues(List.of(FIRST, SECOND));

        // Then
        assertThat(values).containsOnlyKeys(SECOND);
    }

    @Test
    void professorHandler_currentValues_noTargets_returnsAnEmptyMap() {
        // Given
        when(professorRepository.findAllById(List.of())).thenReturn(List.of());

        // When / Then
        assertThat(professorHandler().currentValues(List.of())).isEmpty();
        verifyNoInteractions(lectureService, professorService);
    }

    /** The professor handler has to reach updateProfessor, not updateLecture. */
    @Test
    void professorHandler_applyInverse_recordedValues_replaysThemThroughUpdateProfessor() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("firstName", "Grace");
        before.put("lastName", "Hopper");
        before.put("active", false);
        before.put("lectures", List.of(LECTURE_A.toString()));

        // When
        ProfessorUpdateRequest request = replayedProfessor(FIRST, before);

        // Then
        assertThat(request.firstName()).isEqualTo("Grace");
        assertThat(request.lastName()).isEqualTo("Hopper");
        assertThat(request.active()).isFalse();
        assertThat(request.lectureIds()).containsExactly(LECTURE_A);
    }

    @Test
    void professorHandler_applyInverse_beforeHalfWithoutTheAssignment_leavesLectureIdsNull() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("firstName", "Grace");

        // When
        ProfessorUpdateRequest request = replayedProfessor(FIRST, before);

        // Then
        assertThat(request.firstName()).isEqualTo("Grace");
        assertThat(request.lastName()).isNull();
        assertThat(request.active()).isNull();
        assertThat(request.lectureIds()).isNull();
    }
}

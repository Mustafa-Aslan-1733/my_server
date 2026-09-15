package com.pse.moderation.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.dto.request.LectureUpdateRequest;
import com.pse.moderation.model.Admin;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.model.LectureType;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.error.ApiException;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.service.AnswerCleanup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
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
 * Editing a lecture: what is validated, what counts as a change, and what is recorded.
 *
 * <p>Split out of {@code ModerationCatalogServiceTests} with the code it covers.
 */
@ExtendWith(MockitoExtension.class)
class LectureModerationServiceTests {

    private static final UUID LECTURE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID PROFESSOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private LectureRepository lectureRepository;
    @Mock private ProfessorRepository professorRepository;
    @Mock private AnswerRepository answerRepository;
    @Mock private AnswerCleanup answerCleanup;
    @Mock private AuditWriter auditWriter;

    private LectureModerationService service() {
        return new LectureModerationService(
                lectureRepository, professorRepository, answerRepository, answerCleanup, auditWriter);
    }


    // ------------------------------------------------------------------- fixtures

    private void givenLecture(Lecture lecture) {
        when(lectureRepository.findById(LECTURE_ID)).thenReturn(Optional.of(lecture));
    }


    private static LectureUpdateRequest nameChange(String name) {
        return new LectureUpdateRequest(name, null, null, null, null, null, null);
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


    // ---------------------------------------------------------------- updateLecture

    @Test
    void updateLecture_nullRequest_isRejectedBeforeTheLectureIsEvenLookedUp() {
        assertThatThrownBy(() -> service().updateLecture(principal(), LECTURE_ID, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(lectureRepository, auditWriter);
    }


    @Test
    void updateLecture_everyFieldNull_isRejectedAsNoUpdate() {
        LectureUpdateRequest empty = new LectureUpdateRequest(null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().updateLecture(principal(), LECTURE_ID, empty))
                .isInstanceOf(ApiException.class)
                .hasMessage("No update supplied");

        verifyNoInteractions(lectureRepository, auditWriter);
    }


    @Test
    void updateLecture_unknownLecture_isNotFound() {
        when(lectureRepository.findById(LECTURE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateLecture(principal(), LECTURE_ID, nameChange("Analysis 1")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Lecture not found")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(auditWriter);
    }


    @Test
    void updateLecture_blankName_isRejected() {
        givenLecture(lecture());

        assertThatThrownBy(() -> service().updateLecture(principal(), LECTURE_ID, nameChange("   ")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid lecture name");

        verify(lectureRepository, never()).save(any());
    }


    @Test
    void updateLecture_nameLongerThanTheColumn_isRejected() {
        givenLecture(lecture());

        assertThatThrownBy(() ->
                service().updateLecture(principal(), LECTURE_ID, nameChange("x".repeat(301))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid lecture name");
    }


    @Test
    void updateLecture_nameAtTheLengthLimit_isAccepted() {
        Lecture lecture = lecture();
        givenLecture(lecture);
        String name = "x".repeat(300);

        service().updateLecture(principal(), LECTURE_ID, nameChange(name));

        assertThat(lecture.getName()).isEqualTo(name);
    }


    @Test
    void updateLecture_nameIsTrimmedBeforeItIsCompared() {
        Lecture lecture = lecture();
        lecture.setName("Analysis 1");
        givenLecture(lecture);

        BasicResponse response = service().updateLecture(principal(), LECTURE_ID, nameChange("  Analysis 1  "));

        // Trimmed to the value it already has: no change, so nothing is written.
        assertThat(response.success()).isTrue();
        verify(lectureRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }


    @Test
    void updateLecture_codeLongerThanTheColumn_isRejected() {
        givenLecture(lecture());
        LectureUpdateRequest request =
                new LectureUpdateRequest(null, "x".repeat(101), null, null, null, null, null);

        assertThatThrownBy(() -> service().updateLecture(principal(), LECTURE_ID, request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid lecture code");
    }


    @Test
    void updateLecture_codeOnALectureThatHadNone_recordsTheBeforeValueAsEmptyRatherThanNull() {
        Lecture lecture = lecture();
        lecture.setCode(null);
        givenLecture(lecture);
        LectureUpdateRequest request =
                new LectureUpdateRequest(null, "LA1", null, null, null, null, null);

        service().updateLecture(principal(), LECTURE_ID, request);

        assertThat(changesWritten()).containsKey("code");
        assertThat(before(changesWritten(), "code")).isEqualTo("");
        assertThat(after(changesWritten(), "code")).isEqualTo("LA1");
    }


    @Test
    void updateLecture_semesterYearOutOfRange_isRejectedAtBothEnds() {
        givenLecture(lecture());

        assertThatThrownBy(() -> service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, 1899, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid semester year");

        assertThatThrownBy(() -> service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, 2201, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid semester year");
    }


    @Test
    void updateLecture_semesterYearAtBothBounds_isAccepted() {
        Lecture lecture = lecture();
        givenLecture(lecture);

        service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, 1900, null, null, null, null));
        assertThat(lecture.getSemesterYear()).isEqualTo(1900);

        service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, 2200, null, null, null, null));
        assertThat(lecture.getSemesterYear()).isEqualTo(2200);
    }


    @Test
    void updateLecture_semesterYearUnchanged_isNotValidatedAndNotRecorded() {
        Lecture lecture = lecture();
        lecture.setSemesterYear(2026);
        givenLecture(lecture);

        BasicResponse response = service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, 2026, null, null, null, null));

        assertThat(response.success()).isTrue();
        verifyNoInteractions(auditWriter);
    }


    @Test
    void updateLecture_seasonTypeAndActiveFlag_areEachRecordedByName() {
        Lecture lecture = lecture();
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setLectureType(LectureType.LECTURE_ONLY);
        lecture.setActive(true);
        givenLecture(lecture);

        service().updateLecture(principal(), LECTURE_ID, new LectureUpdateRequest(
                null, null, null, SemesterSeason.WS, false, LectureType.LECTURE_AND_EXERCISE, null));

        Map<String, Object> changes = changesWritten();
        assertThat(changes).containsOnlyKeys("semesterSeason", "active", "lectureType");
        assertThat(after(changes, "semesterSeason")).isEqualTo("WS");
        assertThat(after(changes, "active")).isEqualTo(false);
        assertThat(after(changes, "lectureType")).isEqualTo("LECTURE_AND_EXERCISE");
        assertThat(lecture.getSemesterSeason()).isEqualTo(SemesterSeason.WS);
        assertThat(lecture.isActive()).isFalse();
        assertThat(lecture.getLectureType()).isEqualTo(LectureType.LECTURE_AND_EXERCISE);
    }


    @Test
    void updateLecture_seasonTypeAndActiveFlagResubmittedUnchanged_recordNothing() {
        Lecture lecture = lecture();
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setLectureType(LectureType.LECTURE_ONLY);
        lecture.setActive(true);
        givenLecture(lecture);

        BasicResponse response = service().updateLecture(principal(), LECTURE_ID, new LectureUpdateRequest(
                null, null, null, SemesterSeason.SS, true, LectureType.LECTURE_ONLY, null));

        assertThat(response.success()).isTrue();
        verify(lectureRepository, never()).save(any());
        verifyNoInteractions(auditWriter);
    }


    @Test
    void updateLecture_professorIdThatDoesNotResolve_isNotFound() {
        givenLecture(lecture());
        UUID missing = UUID.randomUUID();
        when(professorRepository.findAllById(List.of(missing))).thenReturn(List.of());

        assertThatThrownBy(() -> service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, null, null, null, null, List.of(missing))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Professor not found")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }


    @Test
    void updateLecture_emptyProfessorList_clearsTheAssignmentAndRecordsIt() {
        Professor held = professor(PROFESSOR_ID, "Peter", "Sanders");
        Lecture lecture = lecture();
        lecture.setProfessors(new LinkedHashSet<>(List.of(held)));
        givenLecture(lecture);
        when(professorRepository.findAllById(List.of())).thenReturn(List.of());

        service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, null, null, null, null, List.of()));

        assertThat(lecture.getProfessors()).isEmpty();
        assertThat(after(changesWritten(), "professors")).isEqualTo(List.of());
    }


    /**
     * BUG-3, the write side, fixed.
     *
     * <p>{@code Lecture.professors} is a {@code Set}, which defines no order, but
     * {@code updateLecture} built {@code before} from the set's iteration order and
     * {@code after} from the order {@code findAllById} happened to return, then compared
     * them position by position. So an assignment that did not change was recorded as a
     * change and wrote an audit event nobody performed. The two are compared as multisets
     * now, so the same members in another order are the same assignment.
     *
     * <p>Real {@code HashSet} order is not used here: {@code Professor} overrides neither
     * {@code equals} nor {@code hashCode}, so ordering depends on object addresses and varies
     * per run. A test built on that would be flaky, and the flakiness would sit in the test
     * instead of in the defect. A {@code LinkedHashSet} plus a stubbed repository order states
     * the same fact and states it every time.
     */
    @Test
    void updateLecture_sameProfessorsInAnotherOrder_recordsNoChange() {
        Professor first = professor(PROFESSOR_ID, "Peter", "Sanders");
        Professor second = professor(UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "Dorothea", "Wagner");

        Lecture lecture = lecture();
        lecture.setProfessors(new LinkedHashSet<>(List.of(first, second)));
        givenLecture(lecture);

        List<UUID> requested = List.of(first.getId(), second.getId());
        // The repository is free to return them in any order; this one is reversed.
        when(professorRepository.findAllById(requested)).thenReturn(List.of(second, first));

        BasicResponse response = service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, null, null, null, null, requested));

        assertThat(response.success()).isTrue();

        // Nothing changed, so nothing is recorded -- and with no other field in the request
        // there is no audit event at all, which is the second half of what the bogus change
        // used to produce.
        verifyNoInteractions(auditWriter);
        assertThat(lecture.getProfessors()).containsExactlyInAnyOrder(first, second);
    }


    /**
     * The other half of BUG-3, which its fix did not reach: {@code sameAssignment} sorts
     * before comparing, so the *decision* is order-insensitive -- but the value written into
     * the audit entry came from {@code labels()}, which did not sort. The recorded
     * {@code before} therefore carried the {@code HashSet}'s identity-hash order straight
     * into the {@code jsonb} column, which preserves array order, and out again through
     * {@code GET /admin/audit-logs}. Two servers answering the same request could disagree
     * about the order of a list nobody changed. F-21's shape, on the audit record.
     *
     * <p>Deterministic for the same reason the test above is: a {@code LinkedHashSet} in a
     * known-wrong order rather than a real {@code HashSet}.
     */
    @Test
    void updateLecture_recordsTheProfessorAssignmentInAStableOrder() {
        Professor kept = professor(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "Dorothea", "Wagner");
        Professor dropped = professor(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Peter", "Sanders");

        Lecture lecture = lecture();
        // Held in descending id order, which is the order a HashSet is free to produce.
        lecture.setProfessors(new LinkedHashSet<>(List.of(kept, dropped)));
        givenLecture(lecture);

        List<UUID> requested = List.of(kept.getId());
        when(professorRepository.findAllById(requested)).thenReturn(List.of(kept));

        service().updateLecture(principal(), LECTURE_ID,
                new LectureUpdateRequest(null, null, null, null, null, null, requested));

        assertThat(before(changesWritten(), "professors")).isEqualTo(List.of(
                dropped.getId().toString(), kept.getId().toString()));
    }

    @Test
    void updateLecture_changedFields_areSavedAndAuditedAsOneLectureUpdatedEntry() {
        Lecture lecture = lecture();
        lecture.setName("Analysis 1");
        lecture.setCode("ANA1");
        givenLecture(lecture);

        BasicResponse response = service().updateLecture(principal(), LECTURE_ID, nameChange("Analysis I"));

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Updated lecture successfully");
        verify(lectureRepository).save(lecture);

        ArgumentCaptor<AuditAction> action = ArgumentCaptor.forClass(AuditAction.class);
        ArgumentCaptor<AuditTargetType> targetType = ArgumentCaptor.forClass(AuditTargetType.class);
        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(auditWriter).write(any(), action.capture(), targetType.capture(),
                any(), label.capture(), any(), any());

        assertThat(action.getValue()).isEqualTo(AuditAction.LECTURE_UPDATED);
        assertThat(targetType.getValue()).isEqualTo(AuditTargetType.LECTURE);
        // The label is built from the lecture after the edit, code first.
        assertThat(label.getValue()).isEqualTo("ANA1 — Analysis I");
    }
}

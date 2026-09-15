package com.pse.lecture;


import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.dto.request.AddLectureRequest;
import com.pse.lecture.dto.response.LectureDetailResponse;
import com.pse.lecture.dto.response.LectureResponse;
import com.pse.lecture.dto.response.LecturesResponse;
import com.pse.lecture.model.Lecture;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.moderation.model.Admin;
import com.pse.lecture.repository.LectureRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.error.ApiException;
import com.pse.lecture.service.LectureService;
import com.pse.professor.model.Professor;
import com.pse.shared.enums.SemesterSeason;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.user.model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LectureServiceTests {


    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private ProfessorRepository professorRepository;

    @Mock
    private AuditWriter auditWriter;

    private LectureService lectureService;



    @BeforeEach
    void setUp() {
        lectureService = new LectureService(
                lectureRepository,
                professorRepository,
                auditWriter
        );
    }


    /** The principal the controller would hand in. */
    private static AuthenticatedUser principalFor(Student student) {
        return new AuthenticatedUser(student, null, mock(Admin.class));
    }

    @Test
    void getLectureUnknownIdIsNotFoundRatherThanASuccessfulFailureBody() {

        UUID lectureId = UUID.randomUUID();

        when(lectureRepository.findById(lectureId)).thenReturn(Optional.empty());

        ApiException thrown = catchThrowableOfType(
                ApiException.class,
                () -> lectureService.getLecture(lectureId));

        assertThat(thrown).as("nothing was thrown").isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(thrown.getMessage()).isEqualTo("Lecture not found");

        verify(lectureRepository).findById(lectureId);
    }

    @Test
    void getLectureReturnsLectureWhenLectureExists() {

        UUID lectureId = UUID.randomUUID();

        Lecture lecture = mock(Lecture.class, RETURNS_DEEP_STUBS);

        when(lecture.getId()).thenReturn(lectureId);
        when(lecture.getName()).thenReturn("Lineare Algebra 1");
        when(lecture.getCode()).thenReturn("LA1");

        when(lectureRepository.findById(lectureId))
                .thenReturn(Optional.of(lecture));


        LectureDetailResponse response = lectureService.getLecture(lectureId);

        assertThat(response.success()).isTrue();
        assertThat(response.lecture()).isNotNull();

        assertThat(response.lecture().id()).isEqualTo(lectureId);
        assertThat(response.lecture().name()).isEqualTo("Lineare Algebra 1");
        assertThat(response.lecture().code()).isEqualTo("LA1");

        verify(lectureRepository).findById(lectureId);
    }

    @Test
    void getLecturesLoadsOnlyActiveLectures() {

        Lecture lecture = mock(Lecture.class, RETURNS_DEEP_STUBS);

        when(lectureRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(lecture));

        LecturesResponse response = lectureService.getLectures();

        assertThat(response.success()).isTrue();
        assertThat(response.lectures().size()).isEqualTo(1);

        verify(lectureRepository).findByActiveTrueOrderByNameAsc();

        verify(lectureRepository, never()).findAllByOrderByNameAsc();
    }


    @Test
    void getAllLecturesLoadsActiveAndInactiveLectures() {

        // Arrange
        Lecture firstLecture = mock(Lecture.class, RETURNS_DEEP_STUBS);
        Lecture secondLecture = mock(Lecture.class, RETURNS_DEEP_STUBS);


        when(lectureRepository.findAllByOrderByNameAsc()).thenReturn(List.of(firstLecture, secondLecture));

        LecturesResponse response = lectureService.getAllLectures();

        assertThat(response.success()).isTrue();
        assertThat(response.lectures().size()).isEqualTo(2);

        verify(lectureRepository).findAllByOrderByNameAsc();

        verify(lectureRepository, never()).findByActiveTrueOrderByNameAsc();


    }


    @Test
    void addLectureFailsWhenLectureAlreadyExists() {

        Student student = mock(Student.class);
        AddLectureRequest request = mock(AddLectureRequest.class);

        when(request.name()).thenReturn("Lineare Algebra 1");
        when(lectureRepository.findByName("Lineare Algebra 1")).thenReturn(Optional.of(mock(Lecture.class)));

        assertThatThrownBy(() -> lectureService.addLecture(principalFor(student), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Lecture already exists: Lineare Algebra 1")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(lectureRepository).findByName("Lineare Algebra 1");
        verify(lectureRepository, never()).save(any());

        verifyNoInteractions(professorRepository);
    }

    @Test
    void addLectureFailsWhenProfessorDoesNotExist() {

        Student student = mock(Student.class);
        AddLectureRequest request = mock(AddLectureRequest.class);

        UUID professorId = UUID.randomUUID();

        when(request.name()).thenReturn("Lineare Algebra 1");
        when(request.professorIds()).thenReturn(
                java.util.List.of(professorId)
        );

        when(lectureRepository.findByName("Lineare Algebra 1")).thenReturn(Optional.empty());

        when(professorRepository.findById(professorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lectureService.addLecture(principalFor(student), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Professor not found: " + professorId)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(professorRepository).findById(professorId);

        verify(lectureRepository, never()).save(any());
    }

    @Test
    void addLectureSuccessfullyCreatesLecture() {

        Student student = mock(Student.class);
        AddLectureRequest request = mock(AddLectureRequest.class);

        UUID professorId = UUID.randomUUID();

        Professor professor = mock(Professor.class, RETURNS_DEEP_STUBS);

        when(request.name()).thenReturn("Lineare Algebra 1");

        when(request.professorIds()).thenReturn(java.util.List.of(professorId));

        when(lectureRepository.findByName("Lineare Algebra 1")).thenReturn(Optional.empty());

        when(professorRepository.findById(professorId)).thenReturn(Optional.of(professor));

        BasicResponse response = lectureService.addLecture(principalFor(student), request);

        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Successfully added Lectures");

        verify(professorRepository).save(professor);

        verify(lectureRepository).save(any(Lecture.class));
    }

    @Test
    void addLectureSuccessfullyCreatesLectureWithCorrectValues() {


        Student student = mock(Student.class);
        AddLectureRequest request = mock(AddLectureRequest.class);

        UUID professorId = UUID.randomUUID();

        Professor professor =
                mock(Professor.class, RETURNS_DEEP_STUBS);

        when(request.name()).thenReturn("Lineare Algebra 1");
        when(request.code()).thenReturn("LA1");
        when(request.semesterYear()).thenReturn(2026);
        when(request.professorIds()).thenReturn(java.util.List.of(professorId));

        when(lectureRepository.findByName("Lineare Algebra 1"))
                .thenReturn(Optional.empty());

        when(professorRepository.findById(professorId))
                .thenReturn(Optional.of(professor));

        ArgumentCaptor<Lecture> lectureCaptor =
                ArgumentCaptor.forClass(Lecture.class);



        BasicResponse response = lectureService.addLecture(principalFor(student), request);



        assertThat(response.success()).isTrue();

        verify(lectureRepository).save(lectureCaptor.capture());

        Lecture savedLecture = lectureCaptor.getValue();

        assertThat(savedLecture.getName()).isEqualTo("Lineare Algebra 1");

        assertThat(savedLecture.getCode()).isEqualTo("LA1");

        assertThat(savedLecture.getSemesterYear()).isEqualTo(2026);

        assertThat(savedLecture.isActive()).isTrue();

        assertThat(savedLecture.getProfessors().contains(professor)).isTrue();
    }

    /**
     * {@code addProfessor} trims both name parts before storing them and this did not, which
     * is the twin asymmetry the shape sweep was looking for. {@code @NotBlank} passes
     * {@code "  Algorithmen 1  "}, so the padding reached the column -- and then
     * {@code LectureModerationService} trims the incoming PATCH name before comparing it to
     * the stored one, so the very first admin edit of such a lecture recorded a name change
     * nobody made and wrote a LECTURE_UPDATED entry for it.
     */
    @Test
    void addLectureStoresTheNameAndCodeTrimmed() {

        Student student = mock(Student.class);
        AddLectureRequest request = mock(AddLectureRequest.class);

        when(request.name()).thenReturn("  Lineare Algebra 1  ");
        when(request.code()).thenReturn("  LA1  ");

        when(lectureRepository.findByName("Lineare Algebra 1")).thenReturn(Optional.empty());

        ArgumentCaptor<Lecture> lectureCaptor = ArgumentCaptor.forClass(Lecture.class);

        BasicResponse response = lectureService.addLecture(principalFor(student), request);

        assertThat(response.success()).isTrue();
        verify(lectureRepository).save(lectureCaptor.capture());

        assertThat(lectureCaptor.getValue().getName()).isEqualTo("Lineare Algebra 1");
        assertThat(lectureCaptor.getValue().getCode()).isEqualTo("LA1");
    }

    /**
     * The other half of the same rule: what is stored trimmed has to be checked trimmed, or
     * the uniqueness constraint is one space away from being bypassed.
     */
    @Test
    void addLectureRefusesANameThatDiffersFromAnExistingOneOnlyByWhitespace() {

        Student student = mock(Student.class);
        AddLectureRequest request = mock(AddLectureRequest.class);

        when(request.name()).thenReturn("  Lineare Algebra 1  ");

        when(lectureRepository.findByName("Lineare Algebra 1"))
                .thenReturn(Optional.of(new Lecture()));

        assertThatThrownBy(() -> lectureService.addLecture(principalFor(student), request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(lectureRepository, never()).save(any());
    }

    @Test
    void getByIdReturnsNullWhenLectureDoesNotExist() {

        UUID id = UUID.randomUUID();

        when(lectureRepository.findById(id))
                .thenReturn(Optional.empty());

        Lecture result = lectureService.getById(id);

        assertThat(result).isNull();
    }

    @Test
    void getByIdReturnsLectureWhenItExists() {

        UUID id = UUID.randomUUID();
        Lecture lecture = mock(Lecture.class);

        when(lectureRepository.findById(id))
                .thenReturn(Optional.of(lecture));

        Lecture result = lectureService.getById(id);

        assertThat(result).isSameAs(lecture);

        verify(lectureRepository).findById(id);
    }





    /**
     * The audit gap this method used to have: updating or deleting a lecture wrote an entry and
     * creating one wrote nothing, so the record could show a rename with no sign of where the
     * row came from.
     *
     * <p>Pinned as a lifecycle event, not a field diff. {@code changes} is keyed {@code exists},
     * which is what {@code AuditRevertService.isFieldDiff} looks for to refuse the revert -- the
     * inverse of a creation is a deletion, which is its own audited action with its own guards.
     */
    @Test
    void addLectureRecordsTheCreationAsALifecycleEvent() {

        Student student = mock(Student.class);
        AuthenticatedUser principal = principalFor(student);
        AddLectureRequest request = mock(AddLectureRequest.class);

        when(request.name()).thenReturn("Lineare Algebra 1");
        when(request.professorIds()).thenReturn(java.util.List.of());
        when(lectureRepository.findByName("Lineare Algebra 1")).thenReturn(Optional.empty());

        assertThat(lectureService.addLecture(principal, request).success()).isTrue();

        ArgumentCaptor<java.util.Map<String, Object>> changes =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditWriter).write(
                eq(principal.admin()),
                eq(AuditAction.LECTURE_CREATED),
                eq(AuditTargetType.LECTURE),
                any(),
                eq("Lineare Algebra 1"),
                changes.capture(),
                any());

        assertThat(changes.getValue().get("exists"))
                .isEqualTo(java.util.Map.of("before", false, "after", true));
    }

    /**
     * A refused creation must leave no trace: nothing was created, so nothing happened.
     *
     * <p>The refusal used here is a duplicate name. It used to be a non-admin principal, which
     * stopped being a refusal the service can reach at all once the unreachable role check came
     * out -- {@code SecurityConfig} refuses that request before the service is called.
     */
    @Test
    void addLectureRecordsNothingWhenItRefuses() {

        Student student = mock(Student.class);
        AddLectureRequest request = mock(AddLectureRequest.class);

        when(request.name()).thenReturn("Lineare Algebra 1");
        when(lectureRepository.findByName("Lineare Algebra 1"))
                .thenReturn(Optional.of(mock(Lecture.class)));

        assertThatThrownBy(() -> lectureService.addLecture(principalFor(student), request))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(auditWriter);
    }
}

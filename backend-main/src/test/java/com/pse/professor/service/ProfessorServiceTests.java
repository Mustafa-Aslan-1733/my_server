package com.pse.professor.service;

import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.professor.dto.request.ProfessorAddRequest;
import com.pse.professor.dto.response.ProfessorDetailResponse;
import com.pse.professor.dto.response.ProfessorResponse;
import com.pse.professor.dto.response.ProfessorsResponse;
import com.pse.professor.model.Professor;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.user.model.Student;
import com.pse.moderation.model.Admin;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.error.ApiException;

import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.model.RatingTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfessorServiceTests {

    @Mock
    private ProfessorRepository professorRepository;

    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private AuditWriter auditWriter;

    /**
     * A real record rather than a mock: {@link AuthenticatedUser} is a record with no behaviour
     * to stub, and the audit write only reads the admin off it.
     */
    private AuthenticatedUser principal;

    private ProfessorService professorService;

    @BeforeEach
    void setUp() {
        principal = new AuthenticatedUser(mock(Student.class), null, mock(Admin.class));
        professorService = new ProfessorService(
                professorRepository,
                lectureRepository,
                auditWriter
        );
    }


    @Test
    void getProfessorsReturnsActiveProfessors() {

        Professor professor1 = createProfessor(
                "Stefan",
                "Kühnlein",
                true
        );

        Professor professor2 = createProfessor(
                "Müller",
                "Quade",
                true
        );

        List<Professor> professors = new ArrayList<>();
        professors.add(professor1);
        professors.add(professor2);

        when(professorRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc())
                .thenReturn(professors);


        ProfessorsResponse response = professorService.getProfessors();


        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Found 2 professors");
        assertThat(response.professors().size()).isEqualTo(2);

        verify(professorRepository)
                .findByActiveTrueOrderByLastNameAscFirstNameAsc();
    }


    @Test
    void getProfessorsReturnsEmptyListWhenNoActiveProfessorExists() {

        when(professorRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc())
                .thenReturn(List.of());


        ProfessorsResponse response = professorService.getProfessors();


        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Found 0 professors");
        assertThat(response.professors().isEmpty()).isTrue();
    }



    @Test
    void getAllProfessorsReturnsAllProfessors() {

        Professor activeProfessor = createProfessor(
                "Stefan",
                "Kühnlein",
                true
        );

        Professor inactiveProfessor = createProfessor(
                "Müller",
                "Quade",
                false
        );

        List<Professor> professors = new ArrayList<>();
        professors.add(activeProfessor);
        professors.add(inactiveProfessor);

        when(professorRepository.findAllByOrderByLastNameAscFirstNameAsc())
                .thenReturn(professors);


        ProfessorsResponse response = professorService.getAllProfessors();


        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Found 2 professors");
        assertThat(response.professors().size()).isEqualTo(2);

        verify(professorRepository)
                .findAllByOrderByLastNameAscFirstNameAsc();
    }




    @Test
    void getProfessorReturnsProfessorWhenProfessorExists() {

        UUID professorId = UUID.randomUUID();

        Professor professor = createProfessor(
                "Stefan",
                "Kühnlein",
                true
        );

        professor.setId(professorId);

        when(professorRepository.findWithLecturesById(professorId))
                .thenReturn(Optional.of(professor));


        ProfessorDetailResponse response =
                professorService.getProfessor(professorId);


        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Found professor with uuid: " + professorId);

        assertThat(response.professor()).isNotNull();
        assertThat(response.professor().id()).isEqualTo(professorId);
        assertThat(response.professor().firstName()).isEqualTo("Stefan");
        assertThat(response.professor().lastName()).isEqualTo("Kühnlein");

        verify(professorRepository)
                .findWithLecturesById(professorId);
    }


    @Test
    void getProfessorUnknownIdIsNotFoundRatherThanASuccessfulFailureBody() {

        UUID professorId = UUID.randomUUID();

        when(professorRepository.findWithLecturesById(professorId))
                .thenReturn(Optional.empty());


        ApiException thrown = catchThrowableOfType(
                ApiException.class,
                () -> professorService.getProfessor(professorId));

        assertThat(thrown).as("nothing was thrown").isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(thrown.getMessage()).isEqualTo("Professor not found");
    }


    @Test
    void addProfessorDoesNotSaveWhenProfessorAlreadyExists() {

        UUID existingProfessorId = UUID.randomUUID();

        Professor existingProfessor = createProfessor(
                "Stefan",
                "Kühnlein",
                true
        );

        existingProfessor.setId(existingProfessorId);

        ProfessorAddRequest request = new ProfessorAddRequest(
                "Stefan",
                "Kühnlein",
                null
        );

        when(professorRepository.findByFirstNameAndLastName(
                "Stefan",
                "Kühnlein"
        )).thenReturn(Optional.of(existingProfessor));


        assertThatThrownBy(() -> professorService.addProfessor(principal, request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Professor already exists: Stefan Kühnlein")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(professorRepository, never()).save(any());
        verifyNoInteractions(lectureRepository);
    }


    /**
     * The mirror of {@code addLectureStoresTheNameAndCodeTrimmed}: this side already stored
     * the trimmed name but ran its uniqueness check on the raw one, so {@code " Stefan"} /
     * {@code "Kuehnlein "} walked past the 409 and created a second row holding exactly the
     * name the first one holds. Each twin had one half of "trim, then check, then store".
     */
    @Test
    void addProfessorRefusesANameThatDiffersFromAnExistingOneOnlyByWhitespace() {

        Professor existingProfessor = createProfessor("Stefan", "Kuehnlein", true);
        existingProfessor.setId(UUID.randomUUID());

        ProfessorAddRequest request = new ProfessorAddRequest(
                " Stefan",
                "Kuehnlein ",
                null
        );

        when(professorRepository.findByFirstNameAndLastName("Stefan", "Kuehnlein"))
                .thenReturn(Optional.of(existingProfessor));

        assertThatThrownBy(() -> professorService.addProfessor(principal, request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(professorRepository, never()).save(any());
    }


    @Test
    void addProfessorDoesNotSaveWhenLectureDoesNotExist() {

        UUID lectureId = UUID.randomUUID();

        ProfessorAddRequest request = new ProfessorAddRequest(
                "Stefan",
                "Kühnlein",
                List.of(lectureId)
        );

        when(professorRepository.findByFirstNameAndLastName(
                "Stefan",
                "Kühnlein"
        )).thenReturn(Optional.empty());

        when(lectureRepository.findById(lectureId))
                .thenReturn(Optional.empty());


        assertThatThrownBy(() -> professorService.addProfessor(principal, request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Couldn't find lecture with lectureID: " + lectureId)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(lectureRepository).findById(lectureId);

        verify(professorRepository, never()).save(any());
    }


    @Test
    void addProfessorSuccessfullyWithoutLectures() {

        ProfessorAddRequest request = new ProfessorAddRequest(
                "Stefan",
                "Kühnlein",
                null
        );

        when(professorRepository.findByFirstNameAndLastName(
                "Stefan",
                "Kühnlein"
        )).thenReturn(Optional.empty());


        BasicResponse response =
                professorService.addProfessor(principal, request);


        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Professor added successfully");

        ArgumentCaptor<Professor> professorCaptor =
                ArgumentCaptor.forClass(Professor.class);

        verify(professorRepository)
                .save(professorCaptor.capture());

        Professor savedProfessor = professorCaptor.getValue();

        assertThat(savedProfessor.getFirstName()).isEqualTo("Stefan");
        assertThat(savedProfessor.getLastName()).isEqualTo("Kühnlein");
        assertThat(savedProfessor.getLectures().isEmpty()).isTrue();

        verifyNoInteractions(lectureRepository);
    }


    @Test
    void addProfessorSuccessfullyWithLecture() {

        UUID lectureId = UUID.randomUUID();

        Lecture lecture = new Lecture();
        lecture.setId(lectureId);

        ProfessorAddRequest request = new ProfessorAddRequest(
                "Stefan",
                "Kühnlein",
                List.of(lectureId)
        );

        when(professorRepository.findByFirstNameAndLastName(
                "Stefan",
                "Kühnlein"
        )).thenReturn(Optional.empty());

        when(lectureRepository.findById(lectureId))
                .thenReturn(Optional.of(lecture));


        BasicResponse response =
                professorService.addProfessor(principal, request);


        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Professor added successfully");

        ArgumentCaptor<Professor> professorCaptor =
                ArgumentCaptor.forClass(Professor.class);

        verify(professorRepository)
                .save(professorCaptor.capture());

        Professor savedProfessor = professorCaptor.getValue();

        assertThat(savedProfessor.getFirstName()).isEqualTo("Stefan");
        assertThat(savedProfessor.getLastName()).isEqualTo("Kühnlein");

        assertThat(savedProfessor.getLectures().contains(lecture)).isTrue();

        assertThat(lecture.getProfessors().contains(savedProfessor)).isTrue();

        verify(lectureRepository).findById(lectureId);
    }


    @Test
    void addProfessorLoadsAllLectures() {

        UUID lectureId1 = UUID.randomUUID();
        UUID lectureId2 = UUID.randomUUID();

        Lecture lecture1 = new Lecture();
        lecture1.setId(lectureId1);

        Lecture lecture2 = new Lecture();
        lecture2.setId(lectureId2);

        ProfessorAddRequest request = new ProfessorAddRequest(
                "Stefan",
                "Kühnlein",
                List.of(lectureId1, lectureId2)
        );

        when(professorRepository.findByFirstNameAndLastName(
                "Stefan",
                "Kühnlein"
        )).thenReturn(Optional.empty());

        when(lectureRepository.findById(lectureId1))
                .thenReturn(Optional.of(lecture1));

        when(lectureRepository.findById(lectureId2))
                .thenReturn(Optional.of(lecture2));


        BasicResponse response =
                professorService.addProfessor(principal, request);


        assertThat(response.success()).isTrue();

        ArgumentCaptor<Professor> professorCaptor =
                ArgumentCaptor.forClass(Professor.class);

        verify(professorRepository)
                .save(professorCaptor.capture());

        Professor savedProfessor = professorCaptor.getValue();

        assertThat(savedProfessor.getLectures().size()).isEqualTo(2);

        assertThat(savedProfessor.getLectures().contains(lecture1)).isTrue();
        assertThat(savedProfessor.getLectures().contains(lecture2)).isTrue();

        assertThat(lecture1.getProfessors().contains(savedProfessor)).isTrue();
        assertThat(lecture2.getProfessors().contains(savedProfessor)).isTrue();
    }


    @Test
    void getProfessorIDReturnsIdWhenProfessorExists() {

        UUID professorId = UUID.randomUUID();

        Professor professor = createProfessor(
                "Stefan",
                "Kühnlein",
                true
        );

        professor.setId(professorId);

        when(professorRepository.findByFirstNameAndLastName(
                "Stefan",
                "Kühnlein"
        )).thenReturn(Optional.of(professor));


        UUID result =
                professorService.getProfessorID("Stefan", "Kühnlein");


        assertThat(result).isEqualTo(professorId);
    }


    @Test
    void getProfessorIDReturnsNullWhenProfessorDoesNotExist() {

        when(professorRepository.findByFirstNameAndLastName(
                "Stefan",
                "Kühnlein"
        )).thenReturn(Optional.empty());


        UUID result = professorService.getProfessorID("Stefan", "Kühnlein");


        assertThat(result).isNull();
    }



    private Professor createProfessor(
            String firstName,
            String lastName,
            boolean active
    ) {

        Professor professor = new Professor();

        professor.setId(UUID.randomUUID());
        professor.setFirstName(firstName);
        professor.setLastName(lastName);
        professor.setActive(active);

        return professor;
    }

    /**
     * The other half of the audit gap. See
     * {@code LectureServiceTests.addLectureRecordsTheCreationAsALifecycleEvent} for why the
     * entry is keyed {@code exists} rather than shaped as a field diff.
     */
    @Test
    void addProfessorRecordsTheCreationAsALifecycleEvent() {

        ProfessorAddRequest request = new ProfessorAddRequest("Stefan", "Kühnlein", List.of());

        when(professorRepository.findByFirstNameAndLastName("Stefan", "Kühnlein"))
                .thenReturn(Optional.empty());

        assertThat(professorService.addProfessor(principal, request).success()).isTrue();

        ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter).write(
                eq(principal.admin()),
                eq(AuditAction.PROFESSOR_CREATED),
                eq(AuditTargetType.PROFESSOR),
                any(),
                eq("Stefan Kühnlein"),
                changes.capture(),
                any());

        assertThat(changes.getValue().get("exists"))
                .isEqualTo(Map.of("before", false, "after", true));
    }

    /** A refused creation must leave no trace: nothing was created, so nothing happened. */
    @Test
    void addProfessorRecordsNothingWhenTheProfessorAlreadyExists() {

        ProfessorAddRequest request = new ProfessorAddRequest("Stefan", "Kühnlein", List.of());

        // With an id, because the duplicate guard is getProfessorID(...) != null and a
        // Professor with no id reads as "not found".
        Professor existing = new Professor();
        existing.setId(UUID.randomUUID());
        when(professorRepository.findByFirstNameAndLastName("Stefan", "Kühnlein"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> professorService.addProfessor(principal, request))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(auditWriter);
    }
}

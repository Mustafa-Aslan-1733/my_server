package com.pse.professor;

import com.pse.shared.dto.BasicResponse;
import com.pse.security.AuthenticatedUser;
import com.pse.professor.controller.ProfessorController;
import com.pse.professor.dto.request.ProfessorAddRequest;
import com.pse.professor.dto.response.ProfessorDetailResponse;
import com.pse.professor.dto.response.ProfessorsResponse;
import com.pse.professor.service.ProfessorService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProfessorControllerTests {


    @Mock
    private ProfessorService professorService;

    @InjectMocks
    private ProfessorController professorController;


    @Test
    void getProfessorsReturnsServiceResponse() {

        ProfessorsResponse expectedResponse = mock(ProfessorsResponse.class);

        when(professorService.getProfessors())
                .thenReturn(expectedResponse);


        ProfessorsResponse response = professorController.getProfessors();


        assertThat(response).isSameAs(expectedResponse);

        verify(professorService).getProfessors();
    }


    @Test
    void getProfessorReturnsServiceResponse() {

        UUID professorId = UUID.randomUUID();

        ProfessorDetailResponse expectedResponse =
                mock(ProfessorDetailResponse.class);

        when(professorService.getProfessor(professorId))
                .thenReturn(expectedResponse);


        ProfessorDetailResponse response =
                professorController.getProfessor(professorId);


        assertThat(response).isSameAs(expectedResponse);

        verify(professorService).getProfessor(professorId);
    }


    @Test
    void addProfessorPassesRequestToService() {

        ProfessorAddRequest request = mock(ProfessorAddRequest.class);

        BasicResponse expectedResponse =
                new BasicResponse(
                        "Professor added successfully",
                        true
                );

        AuthenticatedUser principal = mock(AuthenticatedUser.class);

        when(professorService.addProfessor(principal, request))
                .thenReturn(expectedResponse);


        BasicResponse response = professorController.addProfessor(principal, request);


        assertThat(response).isSameAs(expectedResponse);

        verify(professorService).addProfessor(principal, request);
    }

    // A companion test used to assert that this handler turned a non-admin away itself,
    // answering 200 {"success": false}. The handler no longer decides: POST /data/professor
    // is matched by the security chain, so a non-admin never reaches the method. The
    // refusal is asserted where it now happens -- ProfessorApiIntegrationTests
    // .nonAdminCannotAddProfessor and ApiAuthorizationMatrixTests.


    @Test
    void getProfessorIDPassesNamesToService() {

        UUID professorId = UUID.randomUUID();

        when(professorService.getProfessorID("Stefan", "Kühnlein"))
                .thenReturn(professorId);


        UUID response =
                professorController.getProfessorID(
                        "Stefan",
                        "Kühnlein"
                );


        assertThat(response).isSameAs(professorId);

        verify(professorService)
                .getProfessorID("Stefan", "Kühnlein");
    }
}
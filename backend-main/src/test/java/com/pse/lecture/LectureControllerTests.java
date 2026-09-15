package com.pse.lecture;

import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.controller.LectureController;
import com.pse.lecture.dto.request.AddLectureRequest;
import com.pse.lecture.dto.response.LectureDetailResponse;
import com.pse.lecture.dto.response.LecturesResponse;
import com.pse.lecture.service.LectureService;
import com.pse.security.AuthenticatedUser;
import com.pse.user.model.Student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LectureControllerTests {


    @Mock
    private LectureService lectureService;

    @InjectMocks
    private LectureController lectureController;




    @Test
    void getLecturesReturnsServiceResponse() {

        LecturesResponse expectedResponse = mock(LecturesResponse.class);

        when(lectureService.getLectures()).thenReturn(expectedResponse);


        LecturesResponse response = lectureController.getLectures();


        assertThat(response).isSameAs(expectedResponse);

        verify(lectureService).getLectures();
    }


    @Test
    void getLectureReturnsServiceResponse() {

        UUID lectureId = UUID.randomUUID();

        LectureDetailResponse expectedResponse = mock(LectureDetailResponse.class);

        when(lectureService.getLecture(lectureId))
                .thenReturn(expectedResponse);


        LectureDetailResponse response = lectureController.getLecture(lectureId);


        assertThat(response).isSameAs(expectedResponse);
        verify(lectureService).getLecture(lectureId);
    }


    @Test
    void addLecturePassesStudentAndRequestToService() {

        AuthenticatedUser principal = mock(AuthenticatedUser.class);

        AddLectureRequest request = mock(AddLectureRequest.class);

        BasicResponse expectedResponse = new BasicResponse("Successfully added Lectures", true);

        // The controller passes the principal through untouched now. It used to unwrap the
        // student here, which left the service unable to say which administrator acted -- and
        // so unable to write an audit entry at all.
        when(lectureService.addLecture(principal, request))
                .thenReturn(expectedResponse);


        BasicResponse response = lectureController.addLecture(principal, request);


        assertThat(response).isSameAs(expectedResponse);

        verify(lectureService).addLecture(principal, request);
    }



}

package com.pse.rating;

import com.pse.shared.dto.BasicResponse;
import com.pse.rating.controller.RatingController;
import com.pse.rating.dto.request.RatingRequest;
import com.pse.rating.dto.response.RatingCategoriesResponse;
import com.pse.rating.dto.response.RatingsAverageResponse;
import com.pse.rating.service.RatingService;
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
class RatingControllerTests {

    @Mock
    private RatingService ratingService;

    @Mock
    private AuthenticatedUser principal;

    @Mock
    private Student student;

    @InjectMocks
    private RatingController ratingController;


    @Test
    void submitRatingReturnsServiceResponse() {

        RatingRequest request = mock(RatingRequest.class);

        BasicResponse expected =
                new BasicResponse(
                        "Rating submitted successfully",
                        true
                );

        when(principal.student())
                .thenReturn(student);

        when(ratingService.submitRating(
                student,
                request
        )).thenReturn(expected);


        BasicResponse response =
                ratingController.submitRating(
                        principal,
                        request
                );


        assertThat(response).isSameAs(expected);

        verify(ratingService).submitRating(student, request);
    }


    @Test
    void getRatingsReturnsServiceResponse() {

        UUID lectureId = UUID.randomUUID();

        RatingsAverageResponse expected = mock(RatingsAverageResponse.class);

        when(ratingService.getRatings(lectureId))
                .thenReturn(expected);


        RatingsAverageResponse response = ratingController.getRatings(lectureId);


        assertThat(response).isSameAs(expected);

        verify(ratingService).getRatings(lectureId);
    }


    @Test
    void getRatingCategoriesReturnsServiceResponse() {

        UUID lectureId = UUID.randomUUID();

        RatingCategoriesResponse expected = mock(RatingCategoriesResponse.class);

        when(ratingService
                .getRatingCategories(lectureId))
                .thenReturn(expected);


        RatingCategoriesResponse response = ratingController.getRatingCategories(lectureId);


        assertThat(response).isSameAs(expected);

        verify(ratingService).getRatingCategories(lectureId);
    }


    @Test
    void getOwnRatingReturnsServiceResponse() {

        UUID lectureId = UUID.randomUUID();

        RatingsAverageResponse expected = mock(RatingsAverageResponse.class);

        when(principal.student()).thenReturn(student);

        when(ratingService.getOwnRating(
                student,
                lectureId
        )).thenReturn(expected);


        RatingsAverageResponse response =
                ratingController.getOwnRating(
                        principal,
                        lectureId
                );


        assertThat(response).isSameAs(expected);

        verify(ratingService)
                .getOwnRating(
                        student,
                        lectureId
                );
    }
}
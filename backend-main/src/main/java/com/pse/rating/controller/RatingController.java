package com.pse.rating.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import com.pse.rating.dto.response.RatingCategoriesResponse;
import com.pse.rating.dto.response.RatingsAverageResponse;
import com.pse.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pse.shared.dto.BasicResponse;
import com.pse.rating.dto.request.RatingRequest;
import com.pse.rating.service.RatingService;



/**
 * Provides RatingController.
 */
@RestController
@RequestMapping("/ratings")
public class RatingController {

    private final RatingService ratingService;


    /**
     * Creates RatingController.
     *
     * @param ratingService the ratingService
     */
    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /**
     * Returns submitRating.
     *
     * @param principal the principal
     * @param request the request
     * @return the result
     */
    @PostMapping("/rate")
    public BasicResponse submitRating(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody RatingRequest request) {
        return ratingService.submitRating(principal.student(), request);
    }

    /**
     * Returns getRatings.
     *
     * @param lectureId the lectureId
     * @return the result
     */
    @GetMapping("/{lectureId}")
    public RatingsAverageResponse getRatings(@PathVariable UUID lectureId) {
        return ratingService.getRatings(lectureId);

    }

    /**
     * Returns getRatingCategories.
     *
     * @param lectureId the lectureId
     * @return the result
     */
    @GetMapping("/{lectureId}/categories")
    public RatingCategoriesResponse getRatingCategories(@PathVariable UUID lectureId) {
        return ratingService.getRatingCategories(lectureId);
    }

    /**
     * Returns getOwnRating.
     *
     * @param principal the principal
     * @param lectureId the lectureId
     * @return the result
     */
    @GetMapping("/own/{lectureId}")
    public RatingsAverageResponse getOwnRating(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID lectureId) {
        return ratingService.getOwnRating(principal.student(), lectureId);
    }




}

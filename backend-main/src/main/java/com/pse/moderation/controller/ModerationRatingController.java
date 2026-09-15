package com.pse.moderation.controller;

import com.pse.shared.dto.BasicResponse;
import com.pse.moderation.dto.response.ManagedRatingsResponse;
import com.pse.moderation.service.RatingModerationService;
import com.pse.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sits on the same base path as the public {@code RatingController}: the reads there stay
 * public and aggregate-only, the exact {@code /ratings} listing and the delete-by-id are
 * admin-only, and the security rules separate them by path/method.
 */
@RestController
@RequestMapping({"/admin/ratings", "/ratings"})
public class ModerationRatingController {

    private final RatingModerationService contentService;

    /**
     * Creates ModerationRatingController.
     *
     * @param contentService the contentService
     */
    public ModerationRatingController(RatingModerationService contentService) {
        this.contentService = contentService;
    }

    /**
     * Returns getRatings.
     *
     * @param limit the limit
     * @param cursor the cursor
     * @param lectureId the lectureId
     * @return the result
     */
    @GetMapping
    public ManagedRatingsResponse getRatings(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String lectureId
    ) {
        return contentService.getRatings(limit, cursor, lectureId);
    }

    /**
     * Returns deleteRating.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @DeleteMapping("/{id}")
    public BasicResponse deleteRating(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return contentService.deleteRating(principal, id);
    }
}

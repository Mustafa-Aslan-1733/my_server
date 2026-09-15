package com.pse.lecture.controller;


import java.util.UUID;

import com.pse.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.dto.request.AddLectureRequest;
import com.pse.lecture.dto.response.LectureDetailResponse;
import com.pse.lecture.dto.response.LecturesResponse;
import com.pse.lecture.service.LectureService;


/**
 * REST controller for reading and adding lectures.
 *
 * Provides functionality for:
 * - Retrieving all lectures
 * - Retrieving a specific lecture by its ID
 * - Adding a new lecture (user needs administrator privileges)
 *
 */
@RestController
@RequestMapping("/data")
public class LectureController {

    private final LectureService lectureService;

    /**
     * Creates LectureController.
     *
     * @param lectureService the lectureService
     */
    public LectureController(LectureService lectureService) {
        this.lectureService = lectureService;
    }

    /**
     * Retrieving all lectures in a List.
     *
     * @return : See .success() and .message() for further information
     */
    @GetMapping("/lectures")
    public LecturesResponse getLectures() {
        return lectureService.getLectures();
    }

    /**
     * Retrieving one lecture.
     *
     * @param lecture_id ID of the retrieving Lecture
     * @return : See .success() and .message() for further information
     */
    @GetMapping("/lectures/{lecture_id}")
    public LectureDetailResponse getLecture(@PathVariable UUID lecture_id) {
        return lectureService.getLecture(lecture_id);
    }


    /**
     * Add one Lecture
     * AuthToken needs to be connected to an Admin.
     *
     * @param request (see wiki for details)
     * @return : See .success() and .message() for further information
     * @param principal the principal
     */
    @PostMapping("/lectures")
    public BasicResponse addLecture(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody AddLectureRequest request) {
        return lectureService.addLecture(principal, request);
    }


}

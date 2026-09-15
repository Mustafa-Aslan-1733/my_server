package com.pse.professor.controller;


import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pse.shared.dto.BasicResponse;
import com.pse.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.pse.professor.dto.request.ProfessorAddRequest;
import com.pse.professor.dto.response.ProfessorDetailResponse;
import com.pse.professor.dto.response.ProfessorsResponse;
import com.pse.professor.service.ProfessorService;

/**
 * Provides ProfessorController.
 */
@RestController
@RequestMapping("/data")
public class ProfessorController {


    private final ProfessorService professorService;


    /**
     * Creates ProfessorController.
     *
     * @param professorService the professorService
     */
    public ProfessorController(ProfessorService professorService) {
        this.professorService = professorService;
    }

    /**
     * Returns getProfessors.
     *
     * @return the result
     */
    @GetMapping("/professor")
    public ProfessorsResponse getProfessors() {
        return professorService.getProfessors();
    }

    /**
     * Returns getProfessor.
     *
     * @param professor_id the professor_id
     * @return the result
     */
    @GetMapping("/professor/{professor_id}")
    public ProfessorDetailResponse getProfessor(@PathVariable UUID professor_id) {
        return professorService.getProfessor(professor_id);
    }

    /**
     * SECURED [NEEDS ADMIN]
     *
     * <p>Authorization is the security chain's, not this method's. The isAdmin() check that
     * used to live here answered a non-admin {@code 200 {"success": false}} and an anonymous
     * caller a 500, because the principal it read was null.
     *
     * @param request
     * @return BasicResponse
     * @param principal the principal
     */
    @PostMapping("/professor")
    public BasicResponse addProfessor(@AuthenticationPrincipal AuthenticatedUser principal,
                                      @Valid @RequestBody ProfessorAddRequest request) {
        return professorService.addProfessor(principal, request);
    }


    /**
     * Used for adding Lecture.
     * First, add all professors, then add get IDs for them, and then add lectures
     *
     * @param firstName of Prof
     * @param lastName of Prof
     * @return the UUID of Prof
     */
    @GetMapping("/professor/id")
    public UUID getProfessorID(@RequestParam String firstName, @RequestParam String lastName) {
        return professorService.getProfessorID(firstName, lastName);
    }






}

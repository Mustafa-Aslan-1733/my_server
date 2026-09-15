package com.pse.user.controller;


import com.pse.security.AuthenticatedUser;
import com.pse.user.service.AccountService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pse.shared.dto.BasicResponse;
import com.pse.user.dto.UserRatingResponse;
import com.pse.user.dto.StudentProfileResponse;


/**
 * Handles all Account related interfaces 
 * (e.g. logout on all devices, validating account credentials...)
 */
@RestController
@RequestMapping("/account")
public class AccountController {


    private final AccountService accountService;

    /**
     * Creates AccountController.
     *
     * @param accountService the accountService
     */
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Returns profile information for the authenticated user.
     *
     * @param principal the authenticated user
     * @return the user's profile information
     */
    @GetMapping("/information")
    public StudentProfileResponse getUserInformation(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return accountService.getUserInformation(principal.student());
    }

    /**
     * Returns all ratings submitted by the authenticated user.
     *
     * @param principal the authenticated user
     * @return the user's ratings
     */
    @GetMapping("/ratings")
    public UserRatingResponse getUserRatings(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return accountService.getUserRatings(principal.student());
    }

    /**
     * Invalidates all authentication tokens belonging to the authenticated user.
     *
     * @param principal the authenticated user
     * @return a response indicating whether the logout was successful
     */
    @PostMapping("/logout")
    public BasicResponse requestLogout(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return accountService.requestLogout(principal.student());
    }

    /**
     * Deactivates the account of the authenticated user.
     *
     * @param principal the authenticated user
     * @return a response indicating whether the account was deactivated successfully
     */
    @PatchMapping("/deleteAccount")
    public BasicResponse deleteAccount(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return accountService.deleteAccount(principal.student());
    }


}

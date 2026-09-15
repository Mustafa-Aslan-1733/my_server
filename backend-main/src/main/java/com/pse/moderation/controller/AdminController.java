package com.pse.moderation.controller;


import com.pse.shared.dto.BasicResponse;
import com.pse.moderation.service.AdminService;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Provides AdminController.
 */
@RestController
@RequestMapping("/admins")
public class AdminController {

    private final AdminService adminService;


    /**
     * Creates AdminController.
     *
     * @param adminService the adminService
     */
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Returns validateAdmin.
     *
     * @param authHeader the authHeader
     * @return the result
     */
    @GetMapping("/validate")
    public BasicResponse validateAdmin(@RequestHeader("Authorization") String authHeader) {
        return adminService.validateAdmin(authHeader);
    }

}

package com.pse.moderation.service;

import com.pse.auth.service.IdentityService;
import com.pse.moderation.repository.AdminRepository;
import com.pse.shared.dto.BasicResponse;
import com.pse.user.model.Student;
import org.springframework.stereotype.Service;

/**
 * Answers {@code GET /admins/validate}: is the bearer of this header an administrator?
 *
 * <p>The check itself used to live in {@code ModerationUserService}, which is what gave that
 * class its one method taking a raw {@code Authorization} header and its dependency on the
 * auth module. It is two lines and this was its only caller, so it moved here rather than
 * being wrapped from a distance.
 */
@Service
public class AdminService {

    private final IdentityService identityService;
    private final AdminRepository adminRepository;

    /**
     * Creates AdminService.
     *
     * @param identityService the identityService
     * @param adminRepository the adminRepository
     */
    public AdminService(IdentityService identityService, AdminRepository adminRepository) {
        this.identityService = identityService;
        this.adminRepository = adminRepository;
    }

    /**
     * Returns validateAdmin.
     *
     * @param authHeader the authHeader
     * @return the result
     */
    public BasicResponse validateAdmin(String authHeader) {
        return isAdmin(authHeader)
                ? new BasicResponse("Is Admin", true)
                : new BasicResponse("Is Not Admin", false);
    }

    private boolean isAdmin(String authHeader) {
        Student student = identityService.verifyUser(authHeader);
        return student != null && adminRepository.existsByStudent(student);
    }
}

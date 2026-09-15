package com.pse.moderation.mapper;

import java.time.Clock;
import java.time.LocalDateTime;

import com.pse.moderation.dto.response.UserResponse;
import com.pse.moderation.dto.response.WarningIssuerResponse;
import com.pse.moderation.dto.response.WarningResponse;
import com.pse.moderation.model.Admin;
import com.pse.moderation.model.Warning;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.shared.enums.UserRole;
import com.pse.shared.util.UtcDates;
import com.pse.social.repository.CommentReportRepository;
import com.pse.user.model.Student;
import org.springframework.stereotype.Component;

/**
 * Students and warnings as the admin panel reads them.
 *
 * <p>A {@code @Component} because one of the two student mappings looks up its own counts.
 * That overload is the per-row one and costs three queries a row; the listing does not use
 * it, and passes the counts it has already batched.
 */
@Component
public class UserResponseMapper {

    private final AdminRepository adminRepository;
    private final WarningRepository warningRepository;
    private final CommentReportRepository commentReportRepository;
    private final Clock clock;

    /**
     * Creates UserResponseMapper.
     *
     * @param adminRepository the adminRepository
     * @param warningRepository the warningRepository
     * @param commentReportRepository the commentReportRepository
     * @param clock the clock
     */
    public UserResponseMapper(
            AdminRepository adminRepository,
            WarningRepository warningRepository,
            CommentReportRepository commentReportRepository,
            Clock clock
    ) {
        this.adminRepository = adminRepository;
        this.warningRepository = warningRepository;
        this.commentReportRepository = commentReportRepository;
        this.clock = clock;
    }

    /**
     * Returns toResponse.
     *
     * @param student the student
     * @return the result
     */
    public UserResponse toResponse(Student student) {
        return toResponse(
                student,
                adminRepository.existsByStudent(student),
                Math.toIntExact(warningRepository.countByStudent(student)),
                Math.toIntExact(commentReportRepository.countByCommentStudent(student))
        );
    }

    /**
     * Returns toResponse.
     *
     * @param student the student
     * @param isAdmin the isAdmin
     * @param warnings the warnings
     * @param reports the reports
     * @return the result
     */
    public UserResponse toResponse(
            Student student,
            boolean isAdmin,
            int warnings,
            int reports
    ) {
        LocalDateTime joined = student.getCreatedAt();
        if (joined == null) {
            joined = student.getEmailVerifiedAt() == null
                    ? LocalDateTime.now(clock)
                    : student.getEmailVerifiedAt();
        }
        LocalDateTime lastOnline = student.getLastSeenAt() == null ? joined : student.getLastSeenAt();
        return new UserResponse(
                student.getId(),
                student.getUsername(),
                student.getKitEmail(),
                isAdmin ? UserRole.ADMIN : UserRole.STUDENT,
                student.getStatus(),
                UtcDates.format(joined),
                UtcDates.format(lastOnline),
                student.getBiography() == null ? "" : student.getBiography(),
                warnings,
                reports,
                student.getCredibilityScore()
        );
    }

    /**
     * Returns toResponse.
     *
     * @param warning the warning
     * @return the result
     */
    public static WarningResponse toResponse(Warning warning) {
        Admin issuer = warning.getAdmin();
        // createdFrom.createdAt is the moment the warning was issued. It used to
        // report when the admin account itself was provisioned, which made every
        // warning from the same admin carry an identical, unrelated timestamp.
        WarningIssuerResponse createdFrom = issuer == null
                ? null
                : new WarningIssuerResponse(
                        issuer.getStudent().getId(),
                        issuer.getStudent().getUsername(),
                        UtcDates.format(warning.getCreatedAt())
                );
        return new WarningResponse(
                warning.getId(),
                warning.getStudent().getId(),
                warning.getMessage(),
                UtcDates.format(warning.getCreatedAt()),
                createdFrom
        );
    }

    /**
     * Returns label.
     *
     * @param student the student
     * @return the result
     */
    public static String label(Student student) {
        return student.getUsername() + " (" + student.getKitEmail() + ")";
    }
}

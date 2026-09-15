package com.pse.auth.service;

import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * Ensures every configured administrator exists as soon as the application starts.
 *
 * <p>Administrators were previously provisioned lazily, on the first successful
 * login. That left two gaps: an admin did not appear in {@code GET /users} with
 * {@code role: "ADMIN"} until they had logged in at least once, and on a database
 * where nobody had ever logged in the entire admin surface answered 403 with no
 * way to recover through the API.
 *
 * <p>This runner is idempotent and only ever adds. It never demotes an admin whose
 * email was removed from the configuration, and it never reactivates an account
 * that an administrator has blocked or deleted.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapPolicy policy;
    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final StudentProvisioning studentProvisioning;

    /**
     * Creates AdminBootstrapRunner.
     *
     * @param policy the policy
     * @param studentRepository the studentRepository
     * @param adminRepository the adminRepository
     * @param studentProvisioning the studentProvisioning
     */
    public AdminBootstrapRunner(
            AdminBootstrapPolicy policy,
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            StudentProvisioning studentProvisioning
    ) {
        this.policy = policy;
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.studentProvisioning = studentProvisioning;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (policy.bootstrapEmails().isEmpty()) {
            LOGGER.warn("No administrator emails configured; the admin API will reject every caller");
            return;
        }

        int created = 0;
        int promoted = 0;
        for (String email : policy.bootstrapEmails()) {
            Student student = studentRepository.findByKitEmail(email).orElse(null);
            if (student == null) {
                student = studentProvisioning.create(email, policy.displayName(email));
                created++;
            }
            if (!adminRepository.existsByStudent(student)) {
                Admin admin = new Admin();
                admin.setStudent(student);
                adminRepository.save(admin);
                promoted++;
            }
        }
        LOGGER.info(
                "Administrator bootstrap complete: {} configured, {} accounts created, {} promoted",
                policy.bootstrapEmails().size(),
                created,
                promoted
        );
    }

}

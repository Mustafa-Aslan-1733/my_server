package com.pse.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.pse.shared.enums.UserStatus;
import com.pse.shared.util.NameGenerator;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.springframework.stereotype.Component;

/**
 * Creates the {@code students} row for an address that has never signed in before.
 *
 * <p>Two places do this and must not drift: the first successful login of an unknown address,
 * and the startup runner that materialises the configured administrators. They set the same
 * five fields and both need a username no other account holds; they were two copies, and a
 * sixth field added to {@code Student} would have had to be remembered in both.
 */
@Component
public class StudentProvisioning {

    private final StudentRepository studentRepository;
    private final Clock clock;

    /**
     * Creates StudentProvisioning.
     *
     * @param studentRepository the studentRepository
     * @param clock the clock
     */
    public StudentProvisioning(StudentRepository studentRepository, Clock clock) {
        this.studentRepository = studentRepository;
        this.clock = clock;
    }

    /**
     * A new account under a generated display name.
     *
     * @param email the email
     * @return the result
     */
    public Student create(String email) {
        return create(email, null);
    }

    /**
     * A new account, under {@code preferredUsername} when that name is free.
     *
     * <p>Falls back to a generated one rather than failing, because {@code username} is
     * unique and a collision must not take the caller down -- for the bootstrap runner that
     * would mean the application not starting.
     *
     * @param email the email
     * @param preferredUsername the preferredUsername
     * @return the result
     */
    public Student create(String email, String preferredUsername) {
        Student student = new Student();
        student.setKitEmail(email);
        student.setUsername(uniqueUsername(preferredUsername));
        student.setEmailVerifiedAt(LocalDateTime.now(clock));
        student.setCredibilityScore(0);
        student.setStatus(UserStatus.ACTIVE);
        return studentRepository.save(student);
    }

    private String uniqueUsername(String preferred) {
        if (preferred != null && !preferred.isBlank()
                && !studentRepository.existsByUsername(preferred)) {
            return preferred;
        }

        String username;
        do {
            username = NameGenerator.generateUsername();
        } while (studentRepository.existsByUsername(username));

        return username;
    }
}

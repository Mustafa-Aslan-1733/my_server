package com.pse.security;

import com.pse.auth.model.Token;
import com.pse.moderation.model.Admin;
import com.pse.user.model.Student;

/**
 * Represents AuthenticatedUser.
 *
 * @param student the student
 * @param token the token
 * @param admin the admin
 */
public record AuthenticatedUser(Student student, Token token, Admin admin) {

    /**
     * Checks isAdmin.
     *
     * @return the result
     */
    public boolean isAdmin() {
        return admin != null;
    }
}

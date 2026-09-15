package com.pse.auth.service;

import java.util.Map;
import java.util.UUID;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.moderation.model.Admin;
import com.pse.security.AuthenticatedUser;
import com.pse.user.model.Student;
import org.springframework.stereotype.Component;

/**
 * Records a session being opened or closed, on the administrative log or the student one
 * depending on which it is.
 *
 * <p>The fork was written out three times -- once at login, once at logout, once at
 * logout-everywhere -- as the same eight lines with different action constants. It is not
 * only length: {@code AuditWriter.write} dereferences the {@code Admin}, so getting the
 * branch wrong is a null dereference on a student's logout rather than a wrong log line.
 *
 * <p><b>The two methods take their discriminator separately, and deliberately.</b> A login is
 * administrative when the session it minted is an {@code ADMIN} one; a logout is
 * administrative when the account holds an {@code admins} row. Those are not the same test --
 * an administrator signing in through the app API opens an {@code APP} session, and closing
 * it is recorded administratively while opening it was not. That asymmetry is inherited
 * behaviour, pinned by the tests, and is not this class's to decide.
 */
@Component
public class SessionAuditWriter {

    private final AuditWriter auditWriter;

    /**
     * Creates SessionAuditWriter.
     *
     * @param auditWriter the auditWriter
     */
    public SessionAuditWriter(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    /**
     * Records the login.
     * @param asAdmin whether the session that was minted is an administrator session
     * @param admin the admin
     * @param student the student
     * @param tokenId the tokenId
     * @param label the label
     * @param metadata the metadata
     */
    public void recordLogin(
            boolean asAdmin,
            Admin admin,
            Student student,
            UUID tokenId,
            String label,
            Map<String, Object> metadata
    ) {
        if (asAdmin) {
            auditWriter.write(
                    admin,
                    AuditAction.ADMIN_LOGIN,
                    AuditTargetType.ADMIN_SESSION,
                    tokenId,
                    label,
                    AuditWriter.change("authenticated", false, true),
                    metadata
            );
        } else {
            auditWriter.writeStudentAction(
                    student,
                    AuditAction.USER_LOGIN,
                    AuditTargetType.USER_SESSION,
                    tokenId,
                    label,
                    metadata
            );
        }
    }

    /**
     * Administrative when the principal holds an {@code admins} row -- see the class note.
     *
     * @param principal the principal
     * @param tokenId the tokenId
     * @param label the label
     * @param metadata the metadata
     */
    public void recordLogout(
            AuthenticatedUser principal,
            UUID tokenId,
            String label,
            Map<String, Object> metadata
    ) {
        if (principal.isAdmin()) {
            auditWriter.write(
                    principal.admin(),
                    AuditAction.ADMIN_LOGOUT,
                    AuditTargetType.ADMIN_SESSION,
                    tokenId,
                    label,
                    AuditWriter.change("authenticated", true, false),
                    metadata
            );
        } else {
            auditWriter.writeStudentAction(
                    principal.student(),
                    AuditAction.USER_LOGOUT,
                    AuditTargetType.USER_SESSION,
                    tokenId,
                    label,
                    metadata
            );
        }
    }
}

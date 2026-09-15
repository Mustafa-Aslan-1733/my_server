package com.pse.security;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Records requests the authorization rules turned down, so the admin panel can show
 * what the backend refused and to whom.
 *
 * <p>Only refusals of an authenticated caller are recorded. An anonymous request is
 * answered with 401 before it reaches an authorization rule, has no account to attribute
 * the attempt to, and can be repeated by anyone.
 */
@Component
public class AccessRefusalAuditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessRefusalAuditor.class);

    private final AuditWriter auditWriter;

    /**
     * Creates AccessRefusalAuditor.
     *
     * @param auditWriter the auditWriter
     */
    public AccessRefusalAuditor(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    /**
     * Executes record.
     *
     * @param request the request
     */
    public void record(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();
        try {
            auditWriter.writeRefusal(
                    principal.student(),
                    principal.isAdmin(),
                    AuditAction.ACCESS_REFUSED,
                    AuditTargetType.ENDPOINT,
                    null,
                    method + " " + path,
                    Map.of("method", method, "path", path, "status", 403)
            );
        } catch (RuntimeException exception) {
            // The caller is already being refused, and the 403 contract must hold even
            // when the record cannot be written. Turning this into a 500 would replace a
            // correct answer with a wrong one.
            LOGGER.warn("Could not record refused access to {} {}", method, path, exception);
        }
    }
}

package com.pse.audit.controller;

import com.pse.audit.dto.AuditLogPageResponse;
import com.pse.audit.dto.AuditMetaResponse;
import com.pse.audit.model.AuditActionScope;
import com.pse.audit.revert.AuditRevertService;
import com.pse.audit.service.AuditLogService;
import com.pse.shared.dto.BasicResponse;
import com.pse.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The administrative record: what admins did through the panel. Student activity and
 * refused requests live in the same table but are served by {@code /activity-logs}.
 */
@RestController
@RequestMapping({"/admin/audit-logs", "/audit-logs"})
public class AuditLogController {

    private final AuditLogService service;
    private final AuditRevertService revertService;

    /**
     * Creates AuditLogController.
     *
     * @param service the service
     * @param revertService the revertService
     */
    public AuditLogController(AuditLogService service, AuditRevertService revertService) {
        this.service = service;
        this.revertService = revertService;
    }

    /**
     * Returns getLogs.
     *
     * @param limit the limit
     * @param cursor the cursor
     * @param q the q
     * @param actorId the actorId
     * @param actorType the actorType
     * @param action the action
     * @param targetType the targetType
     * @param from the from
     * @param to the to
     * @return the result
     */
    @GetMapping
    public AuditLogPageResponse getLogs(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return service.getLogs(
                AuditActionScope.ADMINISTRATIVE,
                limit,
                cursor,
                q,
                actorId,
                actorType,
                action,
                targetType,
                from,
                to
        );
    }

    /**
     * Returns getMeta.
     *
     * @return the result
     */
    @GetMapping("/meta")
    public AuditMetaResponse getMeta() {
        return service.getMeta(AuditActionScope.ADMINISTRATIVE);
    }

    /**
     * Reverses the field edit one entry recorded.
     *
     * <p>Refuses rather than half-applies: an entry that is not a field edit, is outside the
     * revert window, has already been reversed, or whose value has changed since answers
     * {@code 409} with a {@code reason} the caller can branch on. The reversal is written as
     * its own audit event — the entry being reversed is never rewritten.
     *
     * @param principal the principal
     * @param id the id
     * @return the result
     */
    @PostMapping("/{id}/revert")
    public BasicResponse revert(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return revertService.revert(principal, id);
    }
}

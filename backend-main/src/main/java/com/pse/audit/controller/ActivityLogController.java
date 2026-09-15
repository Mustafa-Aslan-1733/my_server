package com.pse.audit.controller;

import com.pse.audit.dto.ActivityLogPageResponse;
import com.pse.audit.dto.AuditLogPageResponse;
import com.pse.audit.dto.AuditMetaResponse;
import com.pse.audit.model.AuditActionScope;
import com.pse.audit.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything people do in the app: who signed in, what they posted or reported, and what
 * the backend refused. Administrative actions are served by {@code /audit-logs}.
 */
@RestController
@RequestMapping({"/admin/activity-logs", "/activity-logs"})
public class ActivityLogController {

    private final AuditLogService service;

    /**
     * Creates ActivityLogController.
     *
     * @param service the service
     */
    public ActivityLogController(AuditLogService service) {
        this.service = service;
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
    public ActivityLogPageResponse getLogs(
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
        AuditLogPageResponse page = service.getLogs(
                AuditActionScope.ACTIVITY,
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
        return new ActivityLogPageResponse(
                page.message(),
                page.success(),
                page.auditLogs(),
                page.nextCursor()
        );
    }

    /**
     * Returns getMeta.
     *
     * @return the result
     */
    @GetMapping("/meta")
    public AuditMetaResponse getMeta() {
        return service.getMeta(AuditActionScope.ACTIVITY);
    }
}

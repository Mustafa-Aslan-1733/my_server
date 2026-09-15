package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.moderation.dto.request.BugReportUpdateRequest;
import com.pse.moderation.model.BugReport;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.service.ModerationBugReportService;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.BugSeverity;
import com.pse.shared.enums.ReportStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Undoes a bug report triage edit. */
@Component
public class BugReportRevertHandler implements AuditRevertHandler {

    private final BugReportRepository bugReportRepository;
    private final ModerationBugReportService bugReportService;

    /**
 * Creates BugReportRevertHandler.
 *
 * @param bugReportRepository the bugReportRepository
 * @param bugReportService the bugReportService
 */
    public BugReportRevertHandler(
            BugReportRepository bugReportRepository,
            ModerationBugReportService bugReportService
    ) {
        this.bugReportRepository = bugReportRepository;
        this.bugReportService = bugReportService;
    }

    @Override
    public AuditTargetType targetType() {
        return AuditTargetType.BUG_REPORT;
    }

    @Override
    public Map<UUID, Map<String, Object>> currentValues(Collection<UUID> targetIds) {
        Map<UUID, Map<String, Object>> values = new HashMap<>();
        for (BugReport report : bugReportRepository.findAllById(targetIds)) {
            Map<String, Object> current = new LinkedHashMap<>();
            current.put("status", report.getStatus().name());
            current.put("severity", report.getSeverity().name());
            current.put("title", report.getTitle());
            current.put("description", report.getDescription());
            values.put(report.getId(), current);
        }
        return values;
    }

    @Override
    public void applyInverse(AuthenticatedUser principal, UUID targetId, Map<String, Object> before) {
        bugReportService.updateBugReport(principal, targetId, new BugReportUpdateRequest(
                RevertValues.asEnum(before, "status", ReportStatus.class),
                RevertValues.asEnum(before, "severity", BugSeverity.class),
                RevertValues.asString(before, "title"),
                RevertValues.asString(before, "description")
        ));
    }
}

package com.pse.system.service;

import com.pse.audit.model.AuditLog;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.auth.service.LoginLocationService;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.rating.repository.RatingRepository;
import com.pse.shared.enums.ReportStatus;
import com.pse.shared.enums.UserStatus;
import com.pse.moderation.service.GitLabClient;
import com.pse.shared.util.UtcDates;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.system.dto.SystemCountsResponse;
import com.pse.system.dto.SystemDatabaseResponse;
import com.pse.system.dto.SystemLastWriteResponse;
import com.pse.system.dto.SystemStatusResponse;
import com.pse.user.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Provides SystemStatusService.
 */
@Service
public class SystemStatusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemStatusService.class);

    private static final String OK = "OK";
    private static final String DEGRADED = "DEGRADED";
    private static final String DOWN = "DOWN";

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationContext applicationContext;
    private final Clock clock;

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final LectureRepository lectureRepository;
    private final CommentRepository commentRepository;
    private final AnswerRepository answerRepository;
    private final RatingRepository ratingRepository;
    private final CommentReportRepository commentReportRepository;
    private final BugReportRepository bugReportRepository;
    private final AuditLogRepository auditLogRepository;
    private final GitLabClient gitLabClient;
    private final LoginLocationService loginLocationService;

    /**
     * Creates SystemStatusService.
     *
     * @param jdbcTemplate the jdbcTemplate
     * @param applicationContext the applicationContext
     * @param clock the clock
     * @param studentRepository the studentRepository
     * @param adminRepository the adminRepository
     * @param lectureRepository the lectureRepository
     * @param commentRepository the commentRepository
     * @param answerRepository the answerRepository
     * @param ratingRepository the ratingRepository
     * @param commentReportRepository the commentReportRepository
     * @param bugReportRepository the bugReportRepository
     * @param auditLogRepository the auditLogRepository
     * @param gitLabClient the gitLabClient
     * @param loginLocationService the loginLocationService
     */
    public SystemStatusService(
            JdbcTemplate jdbcTemplate,
            ApplicationContext applicationContext,
            Clock clock,
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            LectureRepository lectureRepository,
            CommentRepository commentRepository,
            AnswerRepository answerRepository,
            RatingRepository ratingRepository,
            CommentReportRepository commentReportRepository,
            BugReportRepository bugReportRepository,
            AuditLogRepository auditLogRepository,
            GitLabClient gitLabClient,
            LoginLocationService loginLocationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationContext = applicationContext;
        this.clock = clock;
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.lectureRepository = lectureRepository;
        this.commentRepository = commentRepository;
        this.answerRepository = answerRepository;
        this.ratingRepository = ratingRepository;
        this.commentReportRepository = commentReportRepository;
        this.bugReportRepository = bugReportRepository;
        this.auditLogRepository = auditLogRepository;
        this.gitLabClient = gitLabClient;
        this.loginLocationService = loginLocationService;
    }

    /**
     * Deliberately not transactional: a failing database is a documented outcome of this
     * endpoint, and opening a transaction first would turn it into a 500 before the
     * failure could be reported.
     *
     * @return the result
     */
    public SystemStatusResponse getStatus() {
        Instant now = Instant.now(clock);
        Instant startedAt = Instant.ofEpochMilli(applicationContext.getStartupDate());
        SystemDatabaseResponse database = probeDatabase();

        if (!database.reachable()) {
            return report(DOWN, "Database unreachable", now, startedAt, database, null, null);
        }

        SystemCountsResponse counts;
        SystemLastWriteResponse lastWrite;
        try {
            counts = readCounts();
            lastWrite = readLastWrite(now);
        } catch (DataAccessException exception) {
            // The connection answered but a read did not. Reporting OK here would claim
            // the panel's data is trustworthy when it is not.
            LOGGER.warn("System status reads failed against a reachable database", exception);
            return report(DEGRADED, "Database reachable but reads failed", now, startedAt, database, null, null);
        }

        return report(OK, "Success", now, startedAt, database, counts, lastWrite);
    }

    private SystemDatabaseResponse probeDatabase() {
        long startedAt = System.nanoTime();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return new SystemDatabaseResponse(true, latencyMs, null);
        } catch (DataAccessException exception) {
            LOGGER.warn("System status database probe failed", exception);
            return new SystemDatabaseResponse(false, null, exception.getClass().getSimpleName());
        }
    }

    private SystemCountsResponse readCounts() {
        return new SystemCountsResponse(
                studentRepository.countByStatusNot(UserStatus.DELETED),
                studentRepository.countByStatus(UserStatus.ACTIVE),
                adminRepository.count(),
                lectureRepository.count(),
                commentRepository.count(),
                answerRepository.count(),
                ratingRepository.count(),
                commentReportRepository.countByStatus(ReportStatus.OPEN),
                bugReportRepository.countByStatus(ReportStatus.OPEN),
                auditLogRepository.count()
        );
    }

    private SystemLastWriteResponse readLastWrite(Instant now) {
        AuditLog log = auditLogRepository.findFirstByOrderByCreatedAtDescIdDesc().orElse(null);
        if (log == null) {
            return null;
        }
        return new SystemLastWriteResponse(
                UtcDates.format(log.getCreatedAt()),
                Math.max(0, Duration.between(log.getCreatedAt(), now).toSeconds()),
                log.getAction().name(),
                log.getActorName()
        );
    }

    private SystemStatusResponse report(
            String status,
            String message,
            Instant now,
            Instant startedAt,
            SystemDatabaseResponse database,
            SystemCountsResponse counts,
            SystemLastWriteResponse lastWrite
    ) {
        return new SystemStatusResponse(
                message,
                true,
                status,
                UtcDates.format(now),
                UtcDates.format(startedAt),
                Math.max(0, Duration.between(startedAt, now).toSeconds()),
                database,
                counts,
                lastWrite,
                gitLabClient.isEnabled(),
                loginLocationService.isConfigured()
        );
    }
}

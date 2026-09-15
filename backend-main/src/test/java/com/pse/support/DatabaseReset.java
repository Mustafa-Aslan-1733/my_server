package com.pse.support;

import com.pse.audit.model.AuditLog;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.auth.model.AuthRateLimitBucket;
import com.pse.auth.model.OneTimePassword;
import com.pse.auth.model.Token;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.auth.repository.OneTimePasswordRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.model.Admin;
import com.pse.moderation.model.BugReport;
import com.pse.moderation.model.Warning;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingTopic;
import com.pse.rating.repository.RatingRepository;
import com.pse.social.model.Answer;
import com.pse.social.model.AnswerReport;
import com.pse.social.model.AnswerVote;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentReport;
import com.pse.social.model.CommentVote;
import com.pse.social.model.Notification;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.CommentVoteRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

/**
 * Empties every table, in an order the foreign keys allow.
 *
 * <p>This block used to live in eleven test classes, copied. P-4 in
 * {@code docs/test-findings.md} records what that costs: several places state the same thing and
 * nothing says which to edit when it changes -- and they had already drifted, four of the eleven
 * having quietly stopped deleting comment votes and one bug reports.
 *
 * <p><b>Repositories rather than SQL, deliberately.</b> {@code deleteAll()} loads each entity and
 * removes it, so JPA cascades come with it: {@code Rating.topics} is
 * {@code cascade = ALL, orphanRemoval = true} and the lecture/professor join rows go with their
 * lecture. A {@code DELETE FROM ratings} would leave {@code rating_topics} behind and break on
 * the foreign key. This is a literal extraction of what the eleven classes already did, which is
 * the point -- the order below is theirs, unchanged.
 *
 * <p>Reached as a bean rather than a static call because it needs eighteen repositories, and a
 * static method taking eighteen arguments is not an improvement on the copy it replaces. The
 * {@code @Import} lives inside {@link ApiIntegrationTest} and {@link PostgresE2ETest}, so every
 * class that gets the reset gets the same context as every other.
 */
@TestConfiguration
public class DatabaseReset {

    /**
     * One row per table, in deletion order: children before parents, and the tables a session
     * hangs off before the accounts that own it.
     *
     * <p>Entity and repository are held together on purpose. Kept as two lists they would drift,
     * and the completeness test below would then be checking one of them against the metamodel
     * while the deletions ran off the other.
     */
    private final List<Table> tables;

    DatabaseReset(
            AuditLogRepository auditLogs,
            NotificationRepository notifications,
            WarningRepository warnings,
            AnswerReportRepository answerReports,
            AnswerVoteRepository answerVotes,
            AnswerRepository answers,
            CommentReportRepository commentReports,
            CommentVoteRepository commentVotes,
            CommentRepository comments,
            BugReportRepository bugReports,
            RatingRepository ratings,
            TokenRepository tokens,
            OneTimePasswordRepository oneTimePasswords,
            AuthRateLimitBucketRepository rateLimitBuckets,
            AdminRepository admins,
            LectureRepository lectures,
            ProfessorRepository professors,
            StudentRepository students
    ) {
        tables = List.of(
                new Table(AuditLog.class, auditLogs),
                new Table(Notification.class, notifications),
                new Table(Warning.class, warnings),
                new Table(AnswerReport.class, answerReports),
                new Table(AnswerVote.class, answerVotes),
                new Table(Answer.class, answers),
                new Table(CommentReport.class, commentReports),
                new Table(CommentVote.class, commentVotes),
                new Table(Comment.class, comments),
                new Table(BugReport.class, bugReports),
                new Table(Rating.class, ratings),
                new Table(Token.class, tokens),
                new Table(OneTimePassword.class, oneTimePasswords),
                new Table(AuthRateLimitBucket.class, rateLimitBuckets),
                new Table(Admin.class, admins),
                new Table(Lecture.class, lectures),
                new Table(Professor.class, professors),
                new Table(Student.class, students));
    }

    /**
     * Entities with no repository of their own, removed by a cascade from one that has.
     * Named here so the completeness test can tell "covered by a cascade" apart from
     * "forgotten", which from the metamodel's side look identical.
     */
    public static final Set<Class<?>> DELETED_BY_CASCADE = Set.of(RatingTopic.class);

    /** Empties every table. Safe to call when they are already empty. */
    public void all() {
        tables.forEach(table -> table.repository().deleteAll());
    }

    /** The entities {@link #all()} deletes directly, for the completeness test. */
    public Set<Class<?>> deletedEntities() {
        return tables.stream().map(Table::entity).collect(java.util.stream.Collectors.toSet());
    }

    private record Table(Class<?> entity, JpaRepository<?, ?> repository) {
    }
}

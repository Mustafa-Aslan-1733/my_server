package com.pse;

import com.pse.auth.model.AuthRateLimitBucket;
import com.pse.auth.model.Token;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.auth.service.RateLimitService;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.rating.dto.request.RatingRequest;
import com.pse.rating.dto.request.RatingTopicRequest;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.repository.RatingRepository;
import com.pse.rating.service.RatingService;
import com.pse.shared.enums.ContentStatus;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.enums.VoteType;
import com.pse.security.TokenHasher;
import com.pse.social.dto.request.VoteCommentRequest;
import com.pse.social.model.Comment;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.CommentVoteRepository;
import com.pse.social.service.VoteService;
import com.pse.support.DatabaseReset;
import com.pse.support.PostgresIntegrationTest;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two requests at the same time, against a database that can actually make them collide.
 *
 * <p><b>Why this class exists.</b> Nothing else in the suite runs two requests concurrently --
 * there was no {@code ExecutorService} in {@code src/test} before this file. That left every
 * concurrency protection in the application verified against a <em>mocked</em> exception:
 * {@code RateLimitServiceTests} drives the retry loop by stubbing
 * {@code DataIntegrityViolationException} with {@code thenThrow}, which proves the handler and
 * says nothing about whether the race throws it. P-6 is the same shape of problem -- an
 * assertion that cannot fail -- and it is why a mocked collision is not evidence of anything
 * about a real one.
 *
 * <p><b>Why PostgreSQL.</b> The three protections under test here are the database's:
 * {@code SELECT ... FOR UPDATE} row locking, a unique constraint, and an optimistic
 * {@code @Version} column. H2 does not reproduce their timing, so a version of this class in
 * the main test job would be measuring its own fixture.
 *
 * <p><b>These tests drive services, not routes.</b> Every race here is inside a service, and
 * two of the three reasons to prefer the service are specific rather than stylistic.
 * {@code POST /auth/request-login} sends mail after the rate limiter, and this layer
 * deliberately has no {@code TestDeliveryConfig} -- see {@link PostgresIntegrationTest}, which
 * explains that adding one would fork the context for every class at once. And {@code MockMvc}
 * does not document itself as thread-safe, so driving it from two threads would put the test's
 * own fixture in the race alongside the code under test. The HTTP status each failure maps to
 * is {@code GlobalExceptionHandler}'s business and is covered where that mapping is.
 *
 * <p><b>Not written as characterization tests, which is a deliberate exception to the house
 * rule.</b> {@code CLAUDE.md} says to pin current behaviour and later invert the assertion. That
 * cannot be done here: a race that does not happen produces no failure, so an assertion of the
 * form "one of these two requests fails" is flaky by construction and would pass on a fixed
 * system and on a broken one that simply did not collide this run. What these tests assert is
 * the <em>invariant that has to hold either way</em> -- no unhandled failure escapes, and the
 * row count is what serialised execution would have produced.
 */
@PostgresIntegrationTest
class ConcurrencyPostgresTests {

    /**
     * The window the race has to open in. Both threads wait on a latch and are released
     * together; without the latch they would run in sequence and every test below would pass
     * while proving nothing. See docs/test-plan.md for how that is checked.
     */
    private static final int TIMEOUT_SECONDS = 30;

    @Autowired DatabaseReset databaseReset;

    @Autowired RateLimitService rateLimitService;
    @Autowired AuthRateLimitBucketRepository rateLimitBucketRepository;

    @Autowired VoteService voteService;
    @Autowired RatingService ratingService;

    @Autowired StudentRepository studentRepository;
    @Autowired TokenRepository tokenRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired CommentVoteRepository commentVoteRepository;
    @Autowired RatingRepository ratingRepository;

    @BeforeEach
    void emptyTheTables() {
        databaseReset.all();
    }

    // ---------- the rate limiter ----------

    /**
     * The bucket row exists and two callers increment it at once.
     *
     * <p>This is the case that reaches production: {@code AuthService.requestLogin} consumes
     * against an <em>IP</em> subject as well as an e-mail one, and an IP subject is a single row
     * shared by everyone behind the same egress address. On a university network that is not a
     * double-tap, it is two different students asking for a login code in the same second.
     *
     * <p>What has to hold: the limit is 20, three calls are made in total, so nothing may be
     * refused and nothing may escape as a failure the caller did not ask for. The count has to
     * end at 3 -- a lost update here does not just log oddly, it hands out free attempts against
     * a rate limiter.
     */
    @Test
    void twoSimultaneousIncrementsOfOneBucketNeitherFailNorLoseACount() throws Exception {
        String subject = rateLimitService.ipSubject("141.52.0.1");

        // First call in isolation, so the row exists and the race is an UPDATE rather than
        // an INSERT. They are different races with different protections; this is the one
        // the pessimistic lock is for.
        rateLimitService.consume(RateLimitService.REQUEST_CODE, subject, 20);
        assertThat(attemptsOf(subject)).isEqualTo(1);

        List<Throwable> failures = runTogether(
                () -> {
                    rateLimitService.consume(RateLimitService.REQUEST_CODE, subject, 20);
                    return null;
                },
                () -> {
                    rateLimitService.consume(RateLimitService.REQUEST_CODE, subject, 20);
                    return null;
                });

        assertThat(failures)
                .as("a concurrent increment must not surface as a failure to either caller")
                .isEmpty();

        assertThat(attemptsOf(subject))
                .as("three calls, three attempts -- a lost update gives away a free attempt")
                .isEqualTo(3);
    }

    /**
     * The other half: no row yet, and two callers create it at once.
     *
     * <p>{@code findByOperationAndSubjectHash} carries {@code @Lock(PESSIMISTIC_WRITE)}, but a
     * {@code SELECT ... FOR UPDATE} over a row that does not exist locks nothing, so this race
     * is not the lock's to win. It falls to {@code auth_rate_limit_operation_subject_key} and
     * then to the retry loop in {@code consume}, which catches
     * {@code DataIntegrityViolationException} and tries again -- and on the second attempt the
     * row exists, so the lock applies. That is the sequence {@code RateLimitServiceTests} drives
     * with a stubbed exception; this is it happening.
     */
    @Test
    void twoSimultaneousFirstIncrementsLeaveOneBucketWithBothCounted() throws Exception {
        String subject = rateLimitService.ipSubject("141.52.0.2");

        List<Throwable> failures = runTogether(
                () -> {
                    rateLimitService.consume(RateLimitService.REQUEST_CODE, subject, 20);
                    return null;
                },
                () -> {
                    rateLimitService.consume(RateLimitService.REQUEST_CODE, subject, 20);
                    return null;
                });

        assertThat(failures)
                .as("the retry loop exists for exactly this collision")
                .isEmpty();

        assertThat(rateLimitBucketRepository.findAll())
                .as("one subject is one row, whatever the timing")
                .hasSize(1);

        assertThat(attemptsOf(subject)).isEqualTo(2);
    }

    // ---------- the unique constraints ----------

    /**
     * One student, one comment, two votes cast at the same moment.
     *
     * <p>{@code voteComment} is a read-modify-write: look for an existing vote, update it or
     * insert one. Both threads can find nothing, so both insert, and
     * {@code uk_comment_votes_student_comment} is what stops the second -- there is no lock on
     * this path and no retry either. So a failure here is expected and correct; what the test
     * asks is that the row count is right and that the failure is the kind
     * {@code GlobalExceptionHandler} answers as a 409 rather than the kind it answers as a 500.
     */
    @Test
    void twoSimultaneousVotesOnOneCommentLeaveExactlyOneRow() throws Exception {
        Student student = createStudent("voter@student.kit.edu");
        Lecture lecture = createLecture("Lineare Algebra 1", "LA1");
        Comment comment = createComment(student, lecture);

        VoteCommentRequest request = new VoteCommentRequest(VoteType.UP);

        List<Throwable> failures = runTogether(
                () -> voteService.voteComment(comment.getId(), student, request),
                () -> voteService.voteComment(comment.getId(), student, request));

        assertThat(commentVoteRepository.findAll())
                .as("the unique constraint is the whole protection on this path")
                .hasSize(1);

        assertThat(failures)
                .as("at most one of the two may fail, and never both")
                .hasSizeLessThanOrEqualTo(1);

        failures.forEach(failure -> assertThat(failure)
                .as("a constraint collision has a 409 handler; anything else falls to the "
                        + "catch-all and reaches the caller as a 500")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class));
    }

    /**
     * The same shape on {@code uk_ratings_student_lecture}, and the reason it is worth a second
     * test rather than being assumed from the first: a rating is not a counter. A vote that goes
     * missing is one vote; a rating feeds the average every client reads, so a lost write here
     * leaves data that is quietly wrong rather than visibly absent.
     */
    @Test
    void twoSimultaneousRatingsOnOneLectureLeaveExactlyOneRow() throws Exception {
        Student student = createStudent("rater@student.kit.edu");
        Lecture lecture = createLecture("Analysis 1", "ANA1");

        RatingRequest request = new RatingRequest(
                lecture.getId(),
                List.of(new RatingTopicRequest(RatingCategory.LECTURE_STRUCTURE, 4.0)));

        List<Throwable> failures = runTogether(
                () -> ratingService.submitRating(student, request),
                () -> ratingService.submitRating(student, request));

        assertThat(ratingRepository.findAll())
                .as("one student rates one lecture once")
                .hasSize(1);

        assertThat(failures).hasSizeLessThanOrEqualTo(1);

        failures.forEach(failure -> assertThat(failure)
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class));
    }

    // ---------- the harness ----------

    /**
     * Runs both callables at the same time and returns whatever they threw.
     *
     * <p>The latch is the part that matters. Submitting two tasks to a pool does not make them
     * collide -- the first can finish before the second is scheduled, and then the test measures
     * sequential execution and passes for the wrong reason. Both threads block until the gate
     * opens, so the window is as narrow as the executor can make it.
     *
     * <p>Failures are returned rather than allowed to propagate because <em>which</em> call
     * failed is not the question and is not stable; how many and of what type is.
     */
    private List<Throwable> runTogether(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Future<Throwable>> results = List.of(
                    pool.submit(gated(gate, ready, first)),
                    pool.submit(gated(gate, ready, second)));

            if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("both threads never reached the gate");
            }
            gate.countDown();

            List<Throwable> failures = new java.util.ArrayList<>();
            for (Future<Throwable> result : results) {
                Throwable failure = result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the pool did not shut down");
            }
        }
    }

    private static Callable<Throwable> gated(
            CountDownLatch gate, CountDownLatch ready, Callable<?> body) {
        return () -> {
            ready.countDown();
            gate.await();
            try {
                body.call();
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        };
    }

    private int attemptsOf(String subject) {
        return rateLimitBucketRepository.findAll().stream()
                .filter(bucket -> bucket.getSubjectHash().equals(subject))
                .map(AuthRateLimitBucket::getAttempts)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no bucket for " + subject));
    }

    // ---------- fixtures ----------

    private Student createStudent(String email) {
        Student student = new Student();
        student.setKitEmail(email);
        student.setUsername(email.substring(0, email.indexOf('@')));
        student.setCredibilityScore(0);
        student.setStatus(UserStatus.ACTIVE);
        student.setEmailVerifiedAt(LocalDateTime.now());
        student = studentRepository.saveAndFlush(student);

        Token token = new Token();
        token.setEmail(email);
        token.setStudent(student);
        token.setHash(TokenHasher.hash(UUID.randomUUID().toString()));
        token.setExpiresAt(LocalDateTime.now().plusHours(12));
        tokenRepository.saveAndFlush(token);

        return student;
    }

    private Lecture createLecture(String name, String code) {
        Lecture lecture = new Lecture();
        lecture.setName(name);
        lecture.setCode(code);
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        return lectureRepository.saveAndFlush(lecture);
    }

    private Comment createComment(Student student, Lecture lecture) {
        Comment comment = new Comment();
        comment.setStudent(student);
        comment.setLecture(lecture);
        comment.setContent("A comment to vote on");
        comment.setStatus(ContentStatus.VISIBLE);
        return commentRepository.saveAndFlush(comment);
    }
}

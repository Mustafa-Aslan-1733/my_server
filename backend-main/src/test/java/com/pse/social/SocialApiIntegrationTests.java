package com.pse.social;

import com.pse.audit.model.AuditAction;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.auth.model.Token;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.auth.repository.OneTimePasswordRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.auth.service.TokenGenerator;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.repository.RatingRepository;
import com.pse.shared.enums.ContentStatus;
import com.pse.shared.enums.ReportReason;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.enums.VoteType;
import com.pse.security.TokenHasher;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.model.CommentVote;
import com.pse.social.model.Notification;
import com.pse.social.repository.*;
import com.pse.support.TestDeliveryConfig;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;

@ApiIntegrationTest
class SocialApiIntegrationTests {

    @Autowired DatabaseReset databaseReset;

    @Autowired MockMvc mockMvc;

    @Autowired StudentRepository studentRepository;
    @Autowired TokenRepository tokenRepository;

    @Autowired LectureRepository lectureRepository;
    @Autowired ProfessorRepository professorRepository;

    @Autowired CommentRepository commentRepository;
    @Autowired CommentVoteRepository commentVoteRepository;
    @Autowired CommentReportRepository commentReportRepository;

    @Autowired AnswerRepository answerRepository;
    @Autowired AnswerVoteRepository answerVoteRepository;
    @Autowired AnswerReportRepository answerReportRepository;

    @Autowired NotificationRepository notificationRepository;

    @Autowired AuditLogRepository auditLogRepository;
    @Autowired WarningRepository warningRepository;
    @Autowired BugReportRepository bugReportRepository;
    @Autowired RatingRepository ratingRepository;

    @Autowired OneTimePasswordRepository otpRepository;
    @Autowired AuthRateLimitBucketRepository rateLimitRepository;
    @Autowired AdminRepository adminRepository;


    @BeforeEach
    void cleanDatabase() {

        databaseReset.all();








    }


    @Test
    void studentCanSubmitCommentAndCommentCanBeLoaded() throws Exception {

        Session student =
                createSession(
                        "author@student.kit.edu"
                );

        Lecture lecture =
                createLecture();


        mockMvc.perform(
                        post("/social/comments")
                                .header(
                                        "Authorization",
                                        bearer(
                                                student.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "lectureID":"%s",
                                          "content":"Very helpful lecture"
                                        }
                                        """.formatted(
                                        lecture.getId()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Comment submitted successfully"
                                )
                );


        assertThat(commentRepository.count()).isEqualTo(1);

        Comment comment =
                commentRepository
                        .findAll()
                        .getFirst();

        assertThat(comment.getContent()).isEqualTo("Very helpful lecture");

        assertThat(comment.getStudent().getId()).isEqualTo(student.student().getId());

        assertThat(comment.getLecture().getId()).isEqualTo(lecture.getId());


        mockMvc.perform(
                        get(
                                "/social/comments/{lectureId}",
                                lecture.getId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.comments",
                                hasSize(1))
                )
                .andExpect(
                        jsonPath("$.comments[0].content")
                                .value(
                                        "Very helpful lecture"
                                )
                );
    }


    /**
     * F-3 end to end. {@code NONE} is accepted by the boundary -- it is a {@link VoteType}
     * value like the other two, so {@code @NotNull} passes -- and the row is deleted rather
     * than rewritten, which is what lets the {@code vote} column stay {@code nullable = false}.
     *
     * <p>The audit entry is the other half: F-1 means a withdrawal is recorded too, and a
     * withdrawal is the event a manipulation case most needs to see.
     */
    @Test
    void aVoteCanBeWithdrawnAndTheWithdrawalIsRecorded() throws Exception {

        Session author = createSession("author@student.kit.edu");
        Session voter = createSession("voter@student.kit.edu");
        Lecture lecture = createLecture();
        Comment comment = createComment(author.student(), lecture, "Original comment");

        mockMvc.perform(
                        post("/social/comments/vote/comment/{commentId}", comment.getId())
                                .header("Authorization", bearer(voter.rawToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "voteType":"UP"
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        assertThat(commentVoteRepository.count()).isEqualTo(1);

        mockMvc.perform(
                        post("/social/comments/vote/comment/{commentId}", comment.getId())
                                .header("Authorization", bearer(voter.rawToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "voteType":"NONE"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(commentVoteRepository.count()).isEqualTo(0);

        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.COMMENT_VOTED)
                        .count()).isEqualTo(2);
    }


    @Test
    void votingCommentTwiceUpdatesVoteInsteadOfCreatingDuplicate() throws Exception {

        Session author =
                createSession(
                        "author@student.kit.edu"
                );

        Session voter =
                createSession(
                        "voter@student.kit.edu"
                );

        Lecture lecture =
                createLecture();

        Comment comment =
                createComment(
                        author.student(),
                        lecture,
                        "Original comment"
                );


        mockMvc.perform(
                        post(
                                "/social/comments/vote/comment/{commentId}",
                                comment.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                voter.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "voteType":"UP"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );


        assertThat(commentVoteRepository.count()).isEqualTo(1);

        CommentVote vote =
                commentVoteRepository
                        .findAll()
                        .getFirst();

        assertThat(vote.getVote()).isEqualTo(VoteType.UP);


        mockMvc.perform(
                        post(
                                "/social/comments/vote/comment/{commentId}",
                                comment.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                voter.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "voteType":"DOWN"
                                        }
                                        """)
                )
                .andExpect(status().isOk());


        assertThat(commentVoteRepository.count()).isEqualTo(1);


        vote =
                commentVoteRepository
                        .findAll()
                        .getFirst();

        assertThat(vote.getVote()).isEqualTo(VoteType.DOWN);
    }


    @Test
    void answerCreatesNotificationForCommentAuthorAndNotificationCanBeSeen()
            throws Exception {

        Session commentAuthor =
                createSession(
                        "author@student.kit.edu"
                );

        Session answerAuthor =
                createSession(
                        "answerer@student.kit.edu"
                );

        Lecture lecture =
                createLecture();

        Comment comment =
                createComment(
                        commentAuthor.student(),
                        lecture,
                        "Can someone explain this?"
                );


        mockMvc.perform(
                        post("/social/answers")
                                .header(
                                        "Authorization",
                                        bearer(
                                                answerAuthor.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "commentID":"%s",
                                          "content":"Here is the explanation"
                                        }
                                        """.formatted(
                                        comment.getId()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );


        assertThat(answerRepository.count()).isEqualTo(1);

        assertThat(notificationRepository.count()).isEqualTo(1);


        Notification notification =
                notificationRepository
                        .findAll()
                        .getFirst();


        assertThat(notification
                        .getRecipient()
                        .getId()).isEqualTo(commentAuthor.student().getId());

        assertThat(notification.isSeen()).isFalse();


        mockMvc.perform(
                        get("/social/notifications")
                                .header(
                                        "Authorization",
                                        bearer(
                                                commentAuthor.rawToken()
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.notifications",
                                hasSize(1))
                )
                .andExpect(
                        jsonPath(
                                "$.notifications[0].type"
                        ).value("ANSWER")
                )
                .andExpect(
                        jsonPath(
                                "$.notifications[0].userName"
                        ).value("answerer")
                )
                .andExpect(
                        jsonPath(
                                "$.notifications[0].content"
                        ).value(
                                "Here is the explanation"
                        )
                );


        mockMvc.perform(
                        patch(
                                "/social/notifications/{notificationId}",
                                notification.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                commentAuthor.rawToken()
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );


        Notification stored =
                notificationRepository
                        .findById(
                                notification.getId()
                        )
                        .orElseThrow();

        assertThat(stored.isSeen()).isTrue();


        mockMvc.perform(
                        get("/social/notifications")
                                .header(
                                        "Authorization",
                                        bearer(
                                                commentAuthor.rawToken()
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.notifications",
                                hasSize(0))
                );
    }


    /**
     * A null voteType used to reach the vote row and only fail at flush, surfacing as a 409
     * "State conflict" for what is really a malformed request. The @NotNull on the request
     * record now stops it at the boundary. See F-2 in docs/test-findings.md.
     */
    @Test
    void voteWithoutAVoteTypeIsRejectedAsABadRequest() throws Exception {

        Session author =
                createSession(
                        "author@student.kit.edu"
                );

        Session voter =
                createSession(
                        "voter@student.kit.edu"
                );

        Lecture lecture =
                createLecture();

        Comment comment =
                createComment(
                        author.student(),
                        lecture,
                        "A comment to vote on"
                );


        mockMvc.perform(
                        post(
                                "/social/comments/vote/comment/{commentId}",
                                comment.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                voter.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "voteType":null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Invalid request")
                );


        // An absent field is the same case as an explicit null.
        mockMvc.perform(
                        post(
                                "/social/comments/vote/comment/{commentId}",
                                comment.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                voter.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("Invalid request")
                );
    }


    @Test
    void votingOnAnAnswerWithoutAVoteTypeIsRejectedAsABadRequest() throws Exception {

        Session author =
                createSession(
                        "author@student.kit.edu"
                );

        Session voter =
                createSession(
                        "voter@student.kit.edu"
                );

        Lecture lecture =
                createLecture();

        Comment comment =
                createComment(
                        author.student(),
                        lecture,
                        "A comment"
                );

        Answer answer =
                createAnswer(
                        author.student(),
                        comment,
                        "An answer to vote on"
                );


        mockMvc.perform(
                        post(
                                "/social/comments/vote/answer/{answerId}",
                                answer.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                voter.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "voteType":null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("Invalid request")
                );
    }


    /** The guard must not reject a legitimate vote. */
    @Test
    void aVoteCarryingAVoteTypeIsStillAccepted() throws Exception {

        Session author =
                createSession(
                        "author@student.kit.edu"
                );

        Session voter =
                createSession(
                        "voter@student.kit.edu"
                );

        Lecture lecture =
                createLecture();

        Comment comment =
                createComment(
                        author.student(),
                        lecture,
                        "A comment to vote on"
                );


        mockMvc.perform(
                        post(
                                "/social/comments/vote/comment/{commentId}",
                                comment.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                voter.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "voteType":"UP"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Comment vote saved successfully")
                );
    }


    @Test
    void studentCannotMarkAnotherStudentsNotificationAsSeen() throws Exception {

        Session target =
                createSession(
                        "target@student.kit.edu"
                );

        Session answerAuthor =
                createSession(
                        "answerer@student.kit.edu"
                );

        Session other =
                createSession(
                        "other@student.kit.edu"
                );

        Lecture lecture =
                createLecture();

        Comment comment =
                createComment(
                        target.student(),
                        lecture,
                        "Original comment"
                );


        mockMvc.perform(
                        post("/social/answers")
                                .header(
                                        "Authorization",
                                        bearer(
                                                answerAuthor.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "commentID":"%s",
                                          "content":"An answer"
                                        }
                                        """.formatted(
                                        comment.getId()
                                ))
                )
                .andExpect(status().isOk());


        Notification notification =
                notificationRepository
                        .findAll()
                        .getFirst();


        mockMvc.perform(
                        patch(
                                "/social/notifications/{notificationId}",
                                notification.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                other.rawToken()
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                );


        assertThat(notificationRepository
                        .findById(
                                notification.getId()
                        )
                        .orElseThrow()
                        .isSeen()).isFalse();


        mockMvc.perform(
                        get("/social/notifications")
                                .header(
                                        "Authorization",
                                        bearer(
                                                target.rawToken()
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.notifications",
                                hasSize(1))
                );
    }


    @Test
    void studentsCanReportCommentAndAnswer() throws Exception {

        Session author =
                createSession(
                        "author@student.kit.edu"
                );

        Session reporter =
                createSession(
                        "reporter@student.kit.edu"
                );

        Lecture lecture =
                createLecture();

        Comment comment =
                createComment(
                        author.student(),
                        lecture,
                        "Bad comment"
                );


        mockMvc.perform(
                        post("/social/comments/report")
                                .header(
                                        "Authorization",
                                        bearer(
                                                reporter.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "commentID":"%s",
                                          "reportReason":"HARASSMENT",
                                          "explanation":"Rude"
                                        }
                                        """.formatted(
                                        comment.getId()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );


        assertThat(commentReportRepository.count()).isEqualTo(1);

        assertThat(commentReportRepository
                        .findAll()
                        .getFirst()
                        .getReason()).isEqualTo(ReportReason.HARASSMENT);


        Answer answer =
                createAnswer(
                        author.student(),
                        comment,
                        "Bad answer"
                );


        mockMvc.perform(
                        post("/social/answers/report")
                                .header(
                                        "Authorization",
                                        bearer(
                                                reporter.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "answerID":"%s",
                                          "reportReason":"HARASSMENT",
                                          "explanation":"Also rude"
                                        }
                                        """.formatted(
                                        answer.getId()
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );


        assertThat(answerReportRepository.count()).isEqualTo(1);

        assertThat(answerReportRepository
                        .findAll()
                        .getFirst()
                        .getReason()).isEqualTo(ReportReason.HARASSMENT);
    }


    @Test
    void protectedSocialEndpointsRequireAuthentication() throws Exception {

        mockMvc.perform(
                        post("/social/comments")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "lectureID":"00000000-0000-0000-0000-000000000001",
                                          "content":"test"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                );


        mockMvc.perform(
                        get("/social/notifications")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }



    /**
     * The answer half of {@code aVoteCanBeWithdrawnAndTheWithdrawalIsRecorded}. Voting on an
     * answer is a separate implementation from voting on a comment -- its own repository, its
     * own entity, its own audit action -- and at this layer it only ever had the bad-request
     * case. So the route was known to reject a bad vote and not known to accept a good one.
     */
    @Test
    void anAnswerVoteCanBeCastAndWithdrawn() throws Exception {

        Session author = createSession("author@student.kit.edu");
        Session voter = createSession("voter@student.kit.edu");
        Lecture lecture = createLecture();
        Comment comment = createComment(author.student(), lecture, "Original comment");
        Answer answer = createAnswer(author.student(), comment, "An answer");

        mockMvc.perform(
                        post("/social/comments/vote/answer/{answerId}", answer.getId())
                                .header("Authorization", bearer(voter.rawToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "voteType":"UP"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Answer vote saved successfully"));

        assertThat(answerVoteRepository.count()).isEqualTo(1);

        mockMvc.perform(
                        post("/social/comments/vote/answer/{answerId}", answer.getId())
                                .header("Authorization", bearer(voter.rawToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "voteType":"NONE"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Answer vote withdrawn successfully"));

        assertThat(answerVoteRepository.count()).isEqualTo(0);

        assertThat(auditLogRepository.findAll().stream()
                        .filter(log -> log.getAction() == AuditAction.ANSWER_VOTED)
                        .count()).isEqualTo(2);
    }


    // ---------- GET /social/sync/comments ----------

    /**
     * The route the app syncs from. It had no functional test: the authorization sweep knew who
     * may call it, {@code LazyLoadingRegressionTests} knew it does not throw and the OpenAPI
     * suite knew its body matches the schema -- and none of the three knew what it answers.
     *
     * <p>It is public, takes no lecture, and passes {@code student = null} into the response
     * builder, so it is the one comment read with no per-viewer state in it.
     */
    @Test
    void syncReturnsAnEmptyListRatherThanNullWhenThereAreNoComments() throws Exception {

        mockMvc.perform(get("/social/sync/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Comments loaded successfully"))
                .andExpect(jsonPath("$.comments").isArray())
                .andExpect(jsonPath("$.comments", hasSize(0)));
    }


    /**
     * "Returns both Comments *AND* answers", says the handler. The answers are nested inside
     * each comment rather than served beside them, which is the part a client has to know.
     */
    @Test
    void syncNestsTheAnswersInsideTheirComment() throws Exception {

        Student author = createStudent("author@student.kit.edu");
        Student replier = createStudent("replier@student.kit.edu");
        Lecture lecture = createLecture();

        Comment comment = createComment(author, lecture, "Very helpful lecture");
        createAnswer(replier, comment, "Agreed");

        mockMvc.perform(get("/social/sync/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].content").value("Very helpful lecture"))
                .andExpect(jsonPath("$.comments[0].studentUsername").value("author"))
                .andExpect(jsonPath("$.comments[0].lectureID").value(lecture.getId().toString()))
                .andExpect(jsonPath("$.comments[0].answers", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].answers[0].content").value("Agreed"))
                .andExpect(jsonPath("$.comments[0].answers[0].studentUsername").value("replier"))
                // student is null on this route, so neither carries a vote of its own
                .andExpect(jsonPath("$.comments[0].userVote").doesNotExist())
                .andExpect(jsonPath("$.comments[0].answers[0].userVote").doesNotExist());
    }


    /**
     * The whole catalogue in one answer -- this is what separates the route from
     * {@code GET /social/comments/{lecture_id}}, which is scoped to one lecture.
     */
    @Test
    void syncCrossesLectures() throws Exception {

        Student author = createStudent("author@student.kit.edu");

        Lecture algebra = createLecture();

        Lecture analysis = new Lecture();
        analysis.setName("Analysis 1");
        analysis.setCode("ANA1");
        analysis.setSemesterYear(2026);
        analysis.setSemesterSeason(SemesterSeason.SS);
        analysis.setActive(true);
        analysis = lectureRepository.saveAndFlush(analysis);

        createComment(author, algebra, "On algebra");
        createComment(author, analysis, "On analysis");

        mockMvc.perform(get("/social/sync/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(2)));
    }


    /**
     * A moderated comment must not come back through the sync route either. The filter is
     * {@code findAllByStatusOrderByCreatedAtDesc(VISIBLE)}, so the answer to this is structural rather than a
     * per-row check -- which is exactly why it is worth stating: a later rewrite that pages or
     * joins this query has to keep it.
     */
    @Test
    void syncLeavesOutAHiddenComment() throws Exception {

        Student author = createStudent("author@student.kit.edu");
        Lecture lecture = createLecture();

        createComment(author, lecture, "Still visible");

        Comment hidden = new Comment();
        hidden.setStudent(author);
        hidden.setLecture(lecture);
        hidden.setContent("Hidden by a moderator");
        hidden.setStatus(ContentStatus.HIDDEN);
        commentRepository.saveAndFlush(hidden);

        mockMvc.perform(get("/social/sync/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].content").value("Still visible"));
    }


    /**
     * The other half of the same rule, and the half a status filter on the comment query cannot
     * cover: the comment is visible, one of its answers is not. {@code getAnswerResponses}
     * skips it in Java, so nothing about the SQL protects this one.
     */
    @Test
    void syncLeavesOutAHiddenAnswerUnderAVisibleComment() throws Exception {

        Student author = createStudent("author@student.kit.edu");
        Student replier = createStudent("replier@student.kit.edu");
        Lecture lecture = createLecture();

        Comment comment = createComment(author, lecture, "Very helpful lecture");
        createAnswer(replier, comment, "Agreed");

        Answer hidden = new Answer();
        hidden.setStudent(replier);
        hidden.setComment(comment);
        hidden.setContent("Hidden by a moderator");
        hidden.setStatus(ContentStatus.HIDDEN);
        answerRepository.saveAndFlush(hidden);

        mockMvc.perform(get("/social/sync/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].answers", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].answers[0].content").value("Agreed"));
    }


    /**
     * The unprefixed report path the Android client actually calls.
     *
     * <p>Every other route the app uses on this controller carries the {@code /social}
     * prefix; the client's own audit records this one as the exception
     * (docs/frontend-consumer-findings.md, F-3.8) and treats it as a naming inconsistency.
     * It was not: the path was never mapped. It matched {@code /answers/&#123;id&#125;} on the
     * moderation controller, which maps PATCH and DELETE and no POST, so the request was
     * answered 405 by {@code handleWrongMethod}.
     *
     * <p>Nobody saw it because the client dismisses the dialog and shows "report submitted"
     * immediately after {@code enqueue}, before the response arrives -- so reporting an
     * answer from the app has never worked and has always looked like it did.
     *
     * <p>Asserted against {@code /social/answers/report} rather than a literal so the alias
     * and the real path cannot drift. Deleted with the mapping when the app has migrated.
     */
    @Test
    void answerReportsAreAlsoServedOnTheUnprefixedPathTheAppCalls() throws Exception {
        Session author = createSession("unprefixed-author@student.kit.edu");
        Session reporter = createSession("unprefixed-reporter@student.kit.edu");
        Lecture lecture = createLecture();
        Comment comment = createComment(author.student(), lecture, "Root");
        Answer answer = createAnswer(author.student(), comment, "Bad answer");

        mockMvc.perform(post("/answers/report")
                        .header("Authorization", bearer(reporter.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answerID":"%s",
                                  "reportReason":"HARASSMENT",
                                  "explanation":"Also rude"
                                }
                                """.formatted(answer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(answerReportRepository.count()).isEqualTo(1);
    }

    /**
     * The alias carries the same session guard as the prefixed path.
     *
     * <p>{@code SecurityConfig} matches PATCH and DELETE on {@code /answers/**} for
     * administrators and nothing else, so a POST there falls through to the chain's closing
     * {@code anyRequest().permitAll()} unless a matcher says otherwise -- which would let an
     * anonymous caller file reports.
     */
    @Test
    void theUnprefixedAnswerReportPathRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(post("/answers/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answerID":"%s",
                                  "reportReason":"SPAM",
                                  "explanation":"x"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());

        assertThat(answerReportRepository.count()).isZero();
    }

    private Session createSession(String email) {

        Student student = createStudent(email);

        String raw = TokenGenerator.generateToken();

        Token token = new Token();

        token.setEmail(email);
        token.setStudent(student);

        token.setHash(TokenHasher.hash(raw));

        token.setExpiresAt(LocalDateTime.now().plusHours(12));

        token = tokenRepository.saveAndFlush(token);


        return new Session(student, token, raw);
    }


    private Student createStudent(String email) {

        Student student = new Student();

        student.setKitEmail(email);

        student.setUsername(
                email.substring(
                        0,
                        email.indexOf('@')
                )
        );

        student.setCredibilityScore(0);

        student.setStatus(UserStatus.ACTIVE);

        student.setEmailVerifiedAt(LocalDateTime.now());

        return studentRepository.saveAndFlush(student);
    }


    private Lecture createLecture() {

        Lecture lecture = new Lecture();

        lecture.setName("Lineare Algebra 1");
        lecture.setCode("LA1");

        lecture.setSemesterYear(2026);

        lecture.setSemesterSeason(SemesterSeason.SS);

        lecture.setActive(true);

        return lectureRepository.saveAndFlush(lecture);
    }


    private Comment createComment(Student student, Lecture lecture, String content) {

        Comment comment = new Comment();

        comment.setStudent(student);
        comment.setLecture(lecture);
        comment.setContent(content);

        comment.setStatus(ContentStatus.VISIBLE);

        return commentRepository.saveAndFlush(comment);
    }


    private Answer createAnswer(Student student, Comment comment, String content) {

        Answer answer = new Answer();

        answer.setStudent(student);
        answer.setComment(comment);
        answer.setContent(content);

        answer.setStatus(ContentStatus.VISIBLE);

        return answerRepository.saveAndFlush(answer);
    }


    private String bearer(String token) {
        return "Bearer " + token;
    }


    private record Session(
            Student student,
            Token token,
            String rawToken
    ) {
    }

    /**
     * The four submit routes take their id as a String in the body and used to hand it
     * straight to {@code UUID.fromString}. A malformed one threw IllegalArgumentException and
     * an omitted one threw NullPointerException; GlobalExceptionHandler maps neither, so both
     * became 500 "Unexpected backend error" on a request the caller got wrong.
     *
     * <p>The third value is the one worth keeping: it is UUID-shaped and truncated, so a
     * length-or-dashes check waves it through and only a real parse rejects it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1234", "11111111-1111-1111-1111"})
    void submittingACommentWithAMalformedLectureIdIsABadRequestRatherThanAServerError(
            String malformed
    ) throws Exception {
        Session student = createSession("malformed@student.kit.edu");

        mockMvc.perform(post("/social/comments")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lectureID\":\"" + malformed + "\",\"content\":\"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(commentRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1234", "11111111-1111-1111-1111"})
    void submittingAnAnswerWithAMalformedCommentIdIsABadRequestRatherThanAServerError(
            String malformed
    ) throws Exception {
        Session student = createSession("malformed-answer@student.kit.edu");

        mockMvc.perform(post("/social/answers")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentID\":\"" + malformed + "\",\"content\":\"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(answerRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1234", "11111111-1111-1111-1111"})
    void reportingWithAMalformedIdIsABadRequestRatherThanAServerError(String malformed)
            throws Exception {
        Session student = createSession("malformed-report@student.kit.edu");

        mockMvc.perform(post("/social/comments/report")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentID\":\"" + malformed
                                + "\",\"reportReason\":\"SPAM\",\"explanation\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/social/answers/report")
                        .header("Authorization", bearer(student.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerID\":\"" + malformed
                                + "\",\"reportReason\":\"SPAM\",\"explanation\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * The half the backlog did not name. {@code UUID.fromString(null)} throws NPE rather than
     * IllegalArgumentException, so a body that simply leaves the id out was a 500 too -- and
     * a fix that caught only IllegalArgumentException would have left this behind.
     */
    @Test
    void submittingWithNoIdAtAllIsABadRequestRatherThanAServerError() throws Exception {
        Session student = createSession("missing-id@student.kit.edu");
        String token = bearer(student.rawToken());

        mockMvc.perform(post("/social/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"no lecture id\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/social/answers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"no comment id\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/social/comments/report")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportReason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/social/answers/report")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportReason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(commentRepository.count()).isZero();
        assertThat(answerRepository.count()).isZero();
    }

    /**
     * Characterization of a half-built feature, found by the read-but-never-written sweep.
     *
     * <p>{@code SecurityConfig} declares {@code PATCH /social/notifications/all} as an
     * authenticated route and {@code NotificationRepository.markAllAsSeen} is written and
     * indexed -- but no controller maps it. So {@code /all} falls into
     * {@code PATCH /social/notifications/{notification_id}}, fails to bind as a UUID, and
     * answers 400. The security matcher and the query are the only evidence anyone intended
     * a "mark everything read" action.
     *
     * <p>Pinned rather than fixed: writing the handler would be adding a feature, and
     * deleting the query would throw away the record that one was planned. The choice is in
     * docs/worklog.md. Whichever way it goes, this test is the one to invert.
     */
    @Test
    void markingAllNotificationsAsSeenIsNotAnEndpointDespiteBeingDeclared() throws Exception {
        Session student = createSession("notify-all@student.kit.edu");

        mockMvc.perform(patch("/social/notifications/all")
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    /**
     * Both app-tier comment listings carried no ORDER BY while the admin listing beside them
     * did, and {@code Comment.answers} was a JPA bag with no {@code @OrderBy}. So the order a
     * reader saw was the database's choice on three lists at once, free to differ between two
     * requests for the same lecture -- F-21's defect, unswept.
     *
     * <p>Comments newest first, matching the admin listing; answers oldest first, because a
     * thread under a question reads forwards.
     */
    @Test
    void commentsComeBackNewestFirstAndTheirAnswersOldestFirst() throws Exception {
        Session author = createSession("ordering-author@student.kit.edu");
        Lecture lecture = createLecture();

        Comment older = new Comment();
        older.setContent("Asked first");
        older.setStudent(author.student());
        older.setLecture(lecture);
        older.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        older = commentRepository.saveAndFlush(older);

        Comment newer = new Comment();
        newer.setContent("Asked second");
        newer.setStudent(author.student());
        newer.setLecture(lecture);
        newer.setCreatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        commentRepository.saveAndFlush(newer);

        Answer firstAnswer = new Answer();
        firstAnswer.setContent("Answered first");
        firstAnswer.setStudent(author.student());
        firstAnswer.setComment(older);
        firstAnswer.setCreatedAt(LocalDateTime.of(2026, 1, 3, 10, 0));
        answerRepository.saveAndFlush(firstAnswer);

        Answer secondAnswer = new Answer();
        secondAnswer.setContent("Answered second");
        secondAnswer.setStudent(author.student());
        secondAnswer.setComment(older);
        secondAnswer.setCreatedAt(LocalDateTime.of(2026, 1, 4, 10, 0));
        answerRepository.saveAndFlush(secondAnswer);

        mockMvc.perform(get("/social/comments/{lectureId}", lecture.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].content").value("Asked second"))
                .andExpect(jsonPath("$.comments[1].content").value("Asked first"))
                .andExpect(jsonPath("$.comments[1].answers[0].content").value("Answered first"))
                .andExpect(jsonPath("$.comments[1].answers[1].content").value("Answered second"));
    }
}

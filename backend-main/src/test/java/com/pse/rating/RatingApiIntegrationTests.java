package com.pse.rating;

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
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.UserStatus;
import com.pse.security.TokenHasher;
import com.pse.social.repository.*;
import com.pse.support.TestDeliveryConfig;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
class RatingApiIntegrationTests {

    @Autowired
    DatabaseReset databaseReset;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    TokenRepository tokenRepository;

    @Autowired
    RatingRepository ratingRepository;

    @Autowired
    LectureRepository lectureRepository;

    @Autowired
    ProfessorRepository professorRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    WarningRepository warningRepository;

    @Autowired
    AnswerReportRepository answerReportRepository;

    @Autowired
    AnswerVoteRepository answerVoteRepository;

    @Autowired
    AnswerRepository answerRepository;

    @Autowired
    CommentReportRepository commentReportRepository;

    @Autowired
    CommentVoteRepository commentVoteRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    BugReportRepository bugReportRepository;

    @Autowired
    OneTimePasswordRepository otpRepository;

    @Autowired
    AuthRateLimitBucketRepository rateLimitRepository;

    @Autowired
    AdminRepository adminRepository;


    @BeforeEach
    void cleanDatabase() {

        databaseReset.all();









    }


    @Test
    void studentCanSubmitRatingAndPublicCanReadAverage() throws Exception {

        Session student =
                createSession(
                        "rater@student.kit.edu"
                );

        Lecture lecture =
                createLecture();


        mockMvc.perform(
                        post("/ratings/rate")
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
                                          "lectureId":"%s",
                                          "topics":[
                                            {
                                              "category":"ORGANIZATION",
                                              "value":4
                                            }
                                          ]
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
                                        "Rating submitted successfully"
                                )
                );


        assertThat(ratingRepository.count()).isEqualTo(1);


        mockMvc.perform(
                        get(
                                "/ratings/{lectureId}",
                                lecture.getId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.ratings",
                                hasSize(2))
                )
                .andExpect(
                        jsonPath(
                                "$.ratings[?(@.category == 'ORGANIZATION')].value"
                        ).value(4.0)
                )
                .andExpect(
                        jsonPath(
                                "$.ratings[?(@.category == 'OVERALL')].value"
                        ).value(4.0)
                );
    }


    @Test
    void submittingRatingAgainOverwritesExistingRating() throws Exception {

        Session student =
                createSession(
                        "rater@student.kit.edu"
                );

        Lecture lecture =
                createLecture();


        mockMvc.perform(
                        post("/ratings/rate")
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
                                          "lectureId":"%s",
                                          "topics":[
                                            {
                                              "category":"ORGANIZATION",
                                              "value":2
                                            }
                                          ]
                                        }
                                        """.formatted(
                                        lecture.getId()
                                ))
                )
                .andExpect(status().isOk());


        assertThat(ratingRepository.count()).isEqualTo(1);


        // Same student + same lecture again
        mockMvc.perform(
                        post("/ratings/rate")
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
                                          "lectureId":"%s",
                                          "topics":[
                                            {
                                              "category":"ORGANIZATION",
                                              "value":5
                                            }
                                          ]
                                        }
                                        """.formatted(
                                        lecture.getId()
                                ))
                )
                .andExpect(status().isOk());


        // Still only ONE Rating row
        assertThat(ratingRepository.count()).isEqualTo(1);


        mockMvc.perform(
                        get(
                                "/ratings/own/{lectureId}",
                                lecture.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                student.rawToken()
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.ratings",
                                hasSize(2))
                )
                .andExpect(
                        jsonPath(
                                "$.ratings[?(@.category == 'ORGANIZATION')].value"
                        ).value(5.0)
                )
                .andExpect(
                        jsonPath(
                                "$.ratings[?(@.category == 'OVERALL')].value"
                        ).value(5.0)
                );
    }


    /**
     * The exact request the app makes for a lecture nobody has rated -- and the assertion
     * that was missing when it crashed. This test already drove this path; it asserted
     * {@code success} and {@code message} and never looked at {@code ratings}, which is the
     * field the client dereferences. It came back {@code null}, and a null list field is a
     * crash in any caller that iterates the response without checking.
     *
     * <p>The lesson is narrower than "assert more": a response's *collection* fields are
     * worth asserting even on a failure path, because that is where a client's null check
     * is least likely to be.
     */
    @Test
    void ownRatingReturnsErrorWhenStudentHasNotRatedLecture() throws Exception {

        Session student =
                createSession(
                        "student@student.kit.edu"
                );

        Lecture lecture =
                createLecture();


        mockMvc.perform(
                        get(
                                "/ratings/own/{lectureId}",
                                lecture.getId()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                student.rawToken()
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Error, couldn't find rating for Lecture: "
                                                + lecture.getId()
                                )
                )
                .andExpect(
                        jsonPath("$.ratings")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.ratings")
                                .isEmpty()
                );
    }


    /** An id that resolves to no lecture is a 404, the same answer the categories route gives. */
    @Test
    void ownRatingForAnUnknownLectureIsNotFound() throws Exception {

        Session student =
                createSession(
                        "student@student.kit.edu"
                );

        mockMvc.perform(
                        get(
                                "/ratings/own/{lectureId}",
                                UUID.randomUUID()
                        )
                                .header(
                                        "Authorization",
                                        bearer(
                                                student.rawToken()
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                );
    }


    @Test
    void differentStudentsAreAveragedTogether() throws Exception {

        Session firstStudent =
                createSession(
                        "first@student.kit.edu"
                );

        Session secondStudent =
                createSession(
                        "second@student.kit.edu"
                );

        Lecture lecture =
                createLecture();


        submitOrganizationRating(
                firstStudent,
                lecture,
                4
        );

        submitOrganizationRating(
                secondStudent,
                lecture,
                2
        );


        assertThat(ratingRepository.count()).isEqualTo(2);


        mockMvc.perform(
                        get(
                                "/ratings/{lectureId}",
                                lecture.getId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.ratings[?(@.category == 'ORGANIZATION')].value"
                        ).value(3.0)
                )
                .andExpect(
                        jsonPath(
                                "$.ratings[?(@.category == 'OVERALL')].value"
                        ).value(3.0)
                );
    }


    @Test
    void submittingRatingRequiresAuthentication() throws Exception {

        Lecture lecture = createLecture();


        mockMvc.perform(
                        post("/ratings/rate")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "lectureId":"%s",
                                          "topics":[
                                            {
                                              "category":"ORGANIZATION",
                                              "value":4
                                            }
                                          ]
                                        }
                                        """.formatted(
                                        lecture.getId()
                                ))
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }


    @Test
    void ownRatingRequiresAuthentication() throws Exception {

        Lecture lecture = createLecture();


        mockMvc.perform(
                        get(
                                "/ratings/own/{lectureId}",
                                lecture.getId()
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }


    @Test
    void ratingAverageIsPublic() throws Exception {

        Lecture lecture = createLecture();


        mockMvc.perform(
                        get(
                                "/ratings/{lectureId}",
                                lecture.getId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );
    }



    private void submitOrganizationRating(Session session, Lecture lecture, int value) throws Exception {

        mockMvc.perform(
                        post("/ratings/rate")
                                .header(
                                        "Authorization",
                                        bearer(
                                                session.rawToken()
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "lectureId":"%s",
                                          "topics":[
                                            {
                                              "category":"ORGANIZATION",
                                              "value":%d
                                            }
                                          ]
                                        }
                                        """.formatted(
                                        lecture.getId(),
                                        value
                                ))
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );
    }


    private Session createSession(String email) {

        Student student = createStudent(email);

        String rawToken = TokenGenerator.generateToken();

        Token token = new Token();

        token.setEmail(email);
        token.setStudent(student);

        token.setHash(TokenHasher.hash(rawToken));

        token.setExpiresAt(LocalDateTime.now().plusHours(12));

        token = tokenRepository.saveAndFlush(token);


        return new Session(student, token, rawToken);
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

        lecture.setName("Algorithms");
        lecture.setCode("CS101");

        lecture.setSemesterYear(2026);

        lecture.setSemesterSeason(SemesterSeason.SS);

        lecture.setActive(true);


        return lectureRepository.saveAndFlush(lecture);
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
     * Neither field of this body was validated and the controller bound it with a bare
     * {@code @RequestBody}. A missing lectureId reached {@code findById(null)}, whose own
     * assertion throws IllegalArgumentException; a missing topics list reached a for-each.
     * GlobalExceptionHandler maps neither, so both were 500 on a malformed request.
     */
    @Test
    void ratingWithAMissingFieldIsABadRequestRatherThanAServerError() throws Exception {
        Session student = createSession("incomplete@student.kit.edu");
        Lecture lecture = createLecture();
        String token = bearer(student.rawToken());

        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topics\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lectureId\":\"" + lecture.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // A topic with no category would reach the database as a null column.
        mockMvc.perform(post("/ratings/rate")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lectureId\":\"" + lecture.getId()
                                + "\",\"topics\":[{\"value\":3.0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * The last surviving instance of F-5's shape, and deliberately left as it is.
     *
     * <p>A student who has not rated this lecture gets {@code 200} with
     * {@code "success": false} and an empty list. The 200 and the empty list are right, and
     * the code says why: "I have not rated this lecture" is the ordinary answer for any
     * lecture a student has not rated, and a 404 would make the ordinary case an error. What
     * does not follow is {@code success: false} — the request succeeded and the answer is
     * "none", so the flag reports a failure that did not happen.
     *
     * <p>Changing it is a client-visible change to a field the app reads, on a route where
     * the two readings ("no such lecture" vs "no rating yet") are already separated by the
     * 404 above. Pinned rather than changed; the decision belongs to whoever owns the app
     * contract, and this test is the one to invert when they make it.
     */
    @Test
    void ownRatingForAnUnratedLectureIsAnEmptyListReportedAsAFailure() throws Exception {
        Session student = createSession("unrated@student.kit.edu");
        Lecture lecture = createLecture();

        mockMvc.perform(get("/ratings/own/{lectureId}", lecture.getId())
                        .header("Authorization", bearer(student.rawToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.ratings").isArray())
                .andExpect(jsonPath("$.ratings", hasSize(0)));
    }
}

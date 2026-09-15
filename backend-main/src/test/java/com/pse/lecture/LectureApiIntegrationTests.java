package com.pse.lecture;

import com.pse.audit.repository.AuditLogRepository;
import com.pse.auth.model.Token;
import com.pse.auth.repository.AuthRateLimitBucketRepository;
import com.pse.auth.repository.OneTimePasswordRepository;
import com.pse.auth.repository.TokenRepository;
import com.pse.auth.service.TokenGenerator;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.BugReportRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.repository.RatingRepository;
import com.pse.shared.enums.SemesterSeason;

import com.pse.shared.enums.UserStatus;
import com.pse.security.TokenHasher;
import com.pse.social.repository.AnswerReportRepository;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.CommentReportRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pse.support.TestDeliveryConfig;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;

@ApiIntegrationTest
class LectureApiIntegrationTests {

    @Autowired
    DatabaseReset databaseReset;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private ProfessorRepository professorRepository;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    AdminRepository adminRepository;

    @Autowired
    TokenRepository tokenRepository;


    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    AnswerRepository answerRepository;

    @Autowired
    AnswerReportRepository answerReportRepository;

    @Autowired
    AnswerVoteRepository answerVoteRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    CommentReportRepository commentReportRepository;

    @Autowired
    RatingRepository ratingRepository;

    @Autowired
    WarningRepository warningRepository;

    @Autowired
    BugReportRepository bugReportRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    OneTimePasswordRepository otpRepository;

    @Autowired
    AuthRateLimitBucketRepository rateLimitRepository;


    @BeforeEach
    void setUp() {
        databaseReset.all();







    }

    @Test
    void getLecturesReturnsActiveLectures() throws Exception {

        Lecture lecture = new Lecture();
        lecture.setName("Lineare Algebra 1");
        lecture.setCode("LA1");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);

        lectureRepository.save(lecture);

        mockMvc.perform(get("/data/lectures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lectures", hasSize(1)))
                .andExpect(jsonPath("$.lectures[0].name")
                        .value("Lineare Algebra 1"))
                .andExpect(jsonPath("$.lectures[0].code")
                        .value("LA1"));
    }


    @Test
    void getLecturesDoesNotReturnInactiveLectures() throws Exception {

        Lecture activeLecture = new Lecture();
        activeLecture.setName("Lineare Algebra 1");
        activeLecture.setSemesterYear(2026);
        activeLecture.setSemesterSeason(SemesterSeason.SS);
        activeLecture.setActive(true);

        Lecture inactiveLecture = new Lecture();
        inactiveLecture.setName("Alte Vorlesung");
        inactiveLecture.setSemesterYear(2024);
        inactiveLecture.setSemesterSeason(SemesterSeason.SS);
        inactiveLecture.setActive(false);

        lectureRepository.save(activeLecture);
        lectureRepository.save(inactiveLecture);

        mockMvc.perform(get("/data/lectures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lectures", hasSize(1)))
                .andExpect(jsonPath("$.lectures[0].name")
                        .value("Lineare Algebra 1"));
    }

    /**
     * Characterization of F-47, first of three. <b>This pins current behaviour, not intended
     * behaviour.</b> Whether a deactivated lecture should still be readable by id is an open
     * product question -- "does {@code active = false} mean hidden from the catalogue, or
     * retired?" -- recorded as item 38 in {@code docs/TODO.md}, with the defect itself as item
     * 37, and answered after submission.
     *
     * <p>{@code GET /data/lectures} filters to {@code active = true}, which the test above
     * pins. {@code LectureService.getLecture} does not: it is a plain {@code findById}, and
     * the route is public. So the row the catalogue hides is served in full to a caller who
     * is not signed in at all and holds nothing but the id -- and ids outlive the list, on a
     * bookmark, on a cached screen, or as {@code lectureId} on a rating.
     *
     * <p>Written to be inverted rather than deleted. If the answer is "retired", the two
     * expectations below become {@code isNotFound()} and {@code $.lecture} absent, and that
     * is a client-visible change carrying a {@code CHANGELOG} entry.
     */
    @Test
    void getLectureByIdStillServesADeactivatedLectureToAnAnonymousCaller() throws Exception {

        Lecture inactive = new Lecture();
        inactive.setName("Alte Vorlesung");
        inactive.setCode("ALT");
        inactive.setSemesterYear(2024);
        inactive.setSemesterSeason(SemesterSeason.SS);
        inactive.setActive(false);

        inactive = lectureRepository.saveAndFlush(inactive);

        // No Authorization header on purpose: the anonymous half is the whole point.
        mockMvc.perform(get("/data/lectures/{id}", inactive.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lecture.name").value("Alte Vorlesung"))
                .andExpect(jsonPath("$.lecture.active").value(false));

        // The list it disappeared from, asserted in the same test so the pair is the finding
        // rather than two separate facts about two routes.
        mockMvc.perform(get("/data/lectures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectures", hasSize(0)));
    }



    @Test
    void getLectureReturnsLectureWhenItExists() throws Exception {

        Lecture lecture = new Lecture();
        lecture.setName("Lineare Algebra 1");
        lecture.setCode("LA1");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);

        lecture = lectureRepository.save(lecture);

        mockMvc.perform(
                        get("/data/lectures/{id}", lecture.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lecture.name")
                        .value("Lineare Algebra 1"))
                .andExpect(jsonPath("$.lecture.code")
                        .value("LA1"))
                .andExpect(jsonPath("$.lecture.id")
                        .value(lecture.getId().toString()));
    }

    @Test
    void getLectureUnknownIdIsNotFoundRatherThanASuccessfulFailureBody()
            throws Exception {

        UUID lectureId = UUID.randomUUID();

        // The error body is ApiErrorResponse -- {message, success} -- so "lecture" is absent
        // rather than null. That is the client-visible half of this change and the reason it
        // carries a CHANGELOG entry.
        mockMvc.perform(
                        get("/data/lectures/{id}", lectureId)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Lecture not found"))
                .andExpect(jsonPath("$.lecture").doesNotExist());
    }


    @Test
    void adminCanAddLecture() throws Exception {

        Session admin =
                createSession("admin@student.kit.edu", true);

        Professor professor = new Professor();
        professor.setFirstName("Peter");
        professor.setLastName("Müller");
        professor = professorRepository.saveAndFlush(professor);

        mockMvc.perform(
                        post("/data/lectures")
                                .header(
                                        "Authorization",
                                        bearer(admin.rawToken())
                                )
                                .contentType(MediaType.APPLICATION_JSON)

                                .content("""
        {
          "name": "Lineare Algebra 1",
          "code": "LA1",
          "semesterYear": 2026,
          "semesterSeason": "SS",
          "active": true,
          "professorIds": [
            "%s"
          ]
        }
        """.formatted(professor.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Lecture lecture = lectureRepository
                .findByName("Lineare Algebra 1")
                .orElseThrow();

        assertThat(lecture.getCode()).isEqualTo("LA1");
        assertThat(lecture.getSemesterYear()).isEqualTo(2026);
        assertThat(lecture.isActive()).isTrue();

        mockMvc.perform(
                        get("/data/lectures/{id}", lecture.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lecture.professors", hasSize(1)))
                .andExpect(jsonPath("$.lecture.professors[0].id")
                        .value(professor.getId().toString()));
    }

    /**
     * The same write on the admin API, which is where the panel moves to. The legacy
     * {@code /data/lectures} test above stays until it has.
     */
    @Test
    void adminCanAddLectureThroughAdminApi() throws Exception {

        Session admin = createSession("admin@student.kit.edu", true);

        Professor professor = new Professor();
        professor.setFirstName("Petra");
        professor.setLastName("Weber");
        professor = professorRepository.saveAndFlush(professor);

        mockMvc.perform(
                        post("/admin/data/lectures")
                                .header("Authorization", bearer(admin.rawToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
        {
          "name": "Analysis 1",
          "code": "ANA1",
          "semesterYear": 2026,
          "semesterSeason": "SS",
          "active": true,
          "professorIds": [
            "%s"
          ]
        }
        """.formatted(professor.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(lectureRepository.findByName("Analysis 1").isPresent()).isTrue();
    }

    /**
     * On the admin API the security chain answers first, so this is a plain 403 rather
     * than the legacy 200 with {@code success: false}.
     */
    @Test
    void nonAdminCannotAddLectureThroughAdminApi() throws Exception {

        Session student = createSession("student@student.kit.edu", false);

        mockMvc.perform(
                        post("/admin/data/lectures")
                                .header("Authorization", bearer(student.rawToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
        {
          "name": "Analysis 1",
          "code": "ANA1",
          "semesterYear": 2026,
          "semesterSeason": "SS",
          "active": true,
          "professorIds": []
        }
        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Could not authenticate Admin"));

        assertThat(lectureRepository.findByName("Analysis 1").isEmpty()).isTrue();
    }

    @Test
    void nonAdminCannotAddLecture() throws Exception {

        Session student =
                createSession("student@student.kit.edu", false);

        Professor professor = new Professor();
        professor.setFirstName("Peter");
        professor.setLastName("Müller");
        professor = professorRepository.saveAndFlush(professor);

        mockMvc.perform(
                        post("/data/lectures")
                                .header(
                                        "Authorization",
                                        bearer(student.rawToken())
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
        {
          "name": "Lineare Algebra 1",
          "code": "LA1",
          "semesterYear": 2026,
          "semesterSeason": "SS",
          "active": true,
          "professorIds": [
            "%s"
          ]
        }
        """.formatted(professor.getId()))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Could not authenticate Admin"));

        assertThat(lectureRepository
                        .findByName("Lineare Algebra 1")
                        .isEmpty()).isTrue();
    }


    @Test
    void cannotAddLectureWithExistingName() throws Exception {

        Session admin =
                createSession("admin@student.kit.edu", true);

        Lecture existingLecture = new Lecture();
        existingLecture.setName("Lineare Algebra 1");
        existingLecture.setCode("LA1");
        existingLecture.setSemesterYear(2026);
        existingLecture.setSemesterSeason(SemesterSeason.SS);

        lectureRepository.save(existingLecture);

        Professor professor = new Professor();
        professor.setFirstName("Peter");
        professor.setLastName("Müller");
        professor = professorRepository.saveAndFlush(professor);

        mockMvc.perform(
                        post("/data/lectures")
                                .header(
                                        "Authorization",
                                        bearer(admin.rawToken())
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
        {
          "name": "Lineare Algebra 1",
          "code": "LA1",
          "semesterYear": 2026,
          "semesterSeason": "SS",
          "active": true,
          "professorIds": [
            "%s"
          ]
        }
        """.formatted(professor.getId()))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(
                                "Lecture already exists: Lineare Algebra 1"
                        ));
    }


    @Test
    void cannotAddLectureWhenProfessorDoesNotExist()
            throws Exception {

        Session admin =
                createSession("admin@student.kit.edu", true);

        UUID unknownProfessorId =
                UUID.randomUUID();

        mockMvc.perform(
                        post("/data/lectures")
                                .header(
                                        "Authorization",
                                        bearer(admin.rawToken())
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
        {
          "name": "Lineare Algebra 1",
          "code": "LA1",
          "semesterYear": 2026,
          "semesterSeason": "SS",
          "active": true,
          "professorIds": [
            "%s"
          ]
        }
        """.formatted(unknownProfessorId))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(
                                "Professor not found: "
                                        + unknownProfessorId
                        ));

        assertThat(lectureRepository
                        .findByName("Lineare Algebra 1")
                        .isEmpty()).isTrue();
    }


    @Test
    void addLectureReturnsBadRequestForInvalidRequest()
            throws Exception {

        Session admin =
                createSession("admin@student.kit.edu", true);

        mockMvc.perform(
                        post("/data/lectures")
                                .header(
                                        "Authorization",
                                        bearer(admin.rawToken())
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": ""
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());
    }





    private Session createSession(String email, boolean adminRole) {

        Student student = createStudent(email);

        Admin admin = null;

        if (adminRole) {
            admin = new Admin();
            admin.setStudent(student);
            admin = adminRepository.saveAndFlush(admin);
        }

        String raw = TokenGenerator.generateToken();

        Token token = new Token();
        token.setEmail(email);
        token.setStudent(student);
        token.setHash(TokenHasher.hash(raw));
        token.setExpiresAt(LocalDateTime.now().plusHours(12));

        token = tokenRepository.saveAndFlush(token);

        return new Session(student, admin, token, raw);
    }

    private Student createStudent(String email) {

        Student student = new Student();

        student.setKitEmail(email);
        student.setUsername(email.substring(0, email.indexOf('@')));
        student.setCredibilityScore(0);
        student.setStatus(UserStatus.ACTIVE);
        student.setEmailVerifiedAt(LocalDateTime.now());

        return studentRepository.saveAndFlush(student);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(
            Student student,
            Admin admin,
            Token token,
            String rawToken
    ) {
    }


    /**
     * "professorIds" is the one field of this body @Valid was not checking -- the other four
     * carry constraints -- and omitting it reached the for-each in addLecture, which answered
     * 500. An omitted list means "no professors", the same reading addProfessor has always
     * given the mirror field.
     */
    @Test
    void addingALectureWithNoProfessorIdsFieldCreatesItWithoutProfessors() throws Exception {
        Session admin = createSession("admin@student.kit.edu", true);

        mockMvc.perform(post("/data/lectures")
                        .header("Authorization", bearer(admin.rawToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lecture With No Professors","code":"LWNP",
                                 "semesterYear":2026,"semesterSeason":"SS","active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UUID created = lectureRepository.findByName("Lecture With No Professors")
                .orElseThrow()
                .getId();

        // Read it back over the API rather than off the entity: the professor set is lazy, and
        // what this test is about is what the route answers.
        mockMvc.perform(get("/data/lectures/{id}", created))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecture.professors").isEmpty());
    }
}

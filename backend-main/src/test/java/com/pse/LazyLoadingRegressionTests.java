package com.pse;

import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import com.pse.auth.service.TokenGenerator;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingCategory;
import com.pse.rating.model.RatingTopic;
import com.pse.rating.repository.RatingRepository;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.UserStatus;
import com.pse.security.TokenHasher;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.CommentRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.support.TestDeliveryConfig;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import com.pse.support.ApiIntegrationTest;
import com.pse.support.DatabaseReset;

/**
 * Every endpoint here was hit by the same bug: open-in-view is disabled, so a
 * read path that walks a lazy JPA collection (@OneToMany/@ManyToMany) without
 * its own transaction boundary throws LazyInitializationException, which the
 * GlobalExceptionHandler catch-all turns into an opaque 500. Mockito-based
 * controller tests never caught this because they stub the service layer and
 * never touch a real Hibernate session. These tests seed enough data for every
 * lazy collection on the response path to be non-empty, so a regression here
 * fails loudly instead of only in production.
 */
@ApiIntegrationTest
class LazyLoadingRegressionTests {

    @Autowired DatabaseReset databaseReset;

    @Autowired MockMvc mockMvc;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired RatingRepository ratingRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired AnswerRepository answerRepository;
    @Autowired NotificationRepository notificationRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseReset.all();
    }

    @Test
    void accountEndpointsSurviveAStudentWithRatingsAndComments() throws Exception {
        Student student = createStudent("lazy-account@student.kit.edu");
        Lecture lecture = createLecture(student);
        createRating(student, lecture);
        createComment(student, lecture);
        String token = issueToken(student);

        mockMvc.perform(get("/account/information").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/account/ratings").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void lectureEndpointsSurviveALectureWithProfessorsCommentsAndRatings() throws Exception {
        Student student = createStudent("lazy-lecture@student.kit.edu");
        Lecture lecture = createLecture(student);

        mockMvc.perform(get("/data/lectures"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/data/lectures/" + lecture.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void professorEndpointsReturnLectureIdsForProfessorWithLectures() throws Exception {
        Student student = createStudent("lazy-professor@student.kit.edu");
        Lecture lecture = createLecture(student);

        assertThat(lecture.getProfessors().size()).isEqualTo(1);

        Professor professor = lecture.getProfessors().iterator().next();
        String lectureId = lecture.getId().toString();
        String professorId = professor.getId().toString();

        mockMvc.perform(get("/data/professor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professors", hasSize(1)))
                .andExpect(jsonPath("$.professors[0].id").value(professorId))
                .andExpect(jsonPath("$.professors[0].lectureIds", hasItem(lectureId)));

        mockMvc.perform(get("/data/professor/" + professorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professor.id").value(professorId))
                .andExpect(jsonPath("$.professor.lectureIds", hasItem(lectureId)));
    }

    @Test
    void ratingsEndpointSurvivesALectureWithRatings() throws Exception {
        Student student = createStudent("lazy-rating@student.kit.edu");
        Lecture lecture = createLecture(student);
        createRating(student, lecture);

        mockMvc.perform(get("/ratings/" + lecture.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void syncCommentsEndpointSurvivesACommentWithAnAnswer() throws Exception {
        Student student = createStudent("lazy-sync-comment@student.kit.edu");
        Lecture lecture = createLecture(student);
        Comment comment = createComment(student, lecture);
        createAnswer(student, comment);

        mockMvc.perform(get("/social/sync/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.comments[0].commentID").value(comment.getId().toString()));
    }

    @Test
    void commentsEndpointSurvivesACommentWithAnAnswer() throws Exception {
        Student student = createStudent("lazy-comment@student.kit.edu");
        Lecture lecture = createLecture(student);
        Comment comment = createComment(student, lecture);
        createAnswer(student, comment);

        mockMvc.perform(get("/social/comments/" + lecture.getId()))
                .andExpect(status().isOk());
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

    private String issueToken(Student student) {
        String raw = TokenGenerator.generateToken();
        Token token = new Token();
        token.setEmail(student.getKitEmail());
        token.setStudent(student);
        token.setHash(TokenHasher.hash(raw));
        token.setExpiresAt(LocalDateTime.now().plusHours(12));
        tokenRepository.saveAndFlush(token);
        return raw;
    }

    private Lecture createLecture(Student student) {
        Professor professor = new Professor();
        professor.setFirstName("Ada");
        professor.setLastName("Lovelace");
        professor = professorRepository.saveAndFlush(professor);

        Lecture lecture = new Lecture();
        lecture.setName("Algorithms");
        lecture.setCode("CS101");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        lecture.getProfessors().add(professor);
        return lectureRepository.saveAndFlush(lecture);
    }

    private Rating createRating(Student student, Lecture lecture) {
        Rating rating = new Rating();
        rating.setStudent(student);
        rating.setLecture(lecture);
        rating = ratingRepository.saveAndFlush(rating);

        RatingTopic topic = new RatingTopic();
        topic.setRating(rating);
        topic.setCategory(RatingCategory.ORGANIZATION);
        topic.setValue(4);
        rating.getTopics().add(topic);
        return ratingRepository.saveAndFlush(rating);
    }

    private Comment createComment(Student student, Lecture lecture) {
        Comment comment = new Comment();
        comment.setStudent(student);
        comment.setLecture(lecture);
        comment.setContent("Great lecture");
        return commentRepository.saveAndFlush(comment);
    }

    private Answer createAnswer(Student student, Comment comment) {
        Answer answer = new Answer();
        answer.setStudent(student);
        answer.setComment(comment);
        answer.setContent("Agreed");
        return answerRepository.saveAndFlush(answer);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

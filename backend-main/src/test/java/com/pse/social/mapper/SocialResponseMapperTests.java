package com.pse.social.mapper;

import com.pse.lecture.model.Lecture;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.enums.VoteType;
import com.pse.shared.util.ProfilePictureGenerator;
import com.pse.social.dto.request.*;
import com.pse.social.model.*;
import com.pse.social.repository.*;
import com.pse.user.model.Student;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import com.pse.social.dto.response.CommentResponse;
import com.pse.shared.enums.ContentStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)class SocialResponseMapperTests {

    @Mock
    private CommentVoteRepository commentVoteRepository;

    @Mock
    private AnswerVoteRepository answerVoteRepository;

    private SocialResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SocialResponseMapper(commentVoteRepository, answerVoteRepository);
    }


    private static Student student() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setUsername("ada");
        student.setStatus(UserStatus.ACTIVE);
        return student;
    }

    private static Student deletedStudent() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setUsername("ada");
        student.setStatus(UserStatus.DELETED);
        return student;
    }


    private static Comment comment(ContentStatus status) {
        Lecture lecture = new Lecture();
        lecture.setId(UUID.randomUUID());
        lecture.setName("Algorithmen 1");

        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setContent("Great lecture");
        comment.setStatus(status);
        comment.setStudent(student());
        comment.setLecture(lecture);
        comment.setCreatedAt(LocalDateTime.parse("2026-01-01T00:00:00"));
        comment.setAnswers(new ArrayList<>());
        return comment;
    }


    private static Answer answer(ContentStatus status) {
        Answer answer = new Answer();
        answer.setId(UUID.randomUUID());
        answer.setContent("Yes");
        answer.setStatus(status);
        answer.setStudent(student());
        answer.setCreatedAt(LocalDateTime.parse("2026-01-01T00:00:00"));
        return answer;
    }


    // ------------------------------------------------------- the response builders, batch 7

    /**
     * {@code userVote} is the field the app colours the vote arrows from, and it has three
     * states that look alike from the outside: nobody is signed in, the reader has not voted,
     * and the reader has. Only the third produces a value, and the first two must produce null
     * without asking the vote table anything it cannot answer.
     */
    @Test
    void aCommentReadAnonymouslyCarriesNoUserVote() {
        Comment comment = comment(ContentStatus.VISIBLE);

        CommentResponse response = mapper.toResponse(comment, null);

        assertThat(response.userVote()).isNull();
        verify(commentVoteRepository, never()).findByStudentAndComment(any(), any());
    }


    @Test
    void aCommentReadBySomeoneWhoHasNotVotedCarriesNoUserVote() {
        Comment comment = comment(ContentStatus.VISIBLE);
        Student reader = student();
        when(commentVoteRepository.findByStudentAndComment(reader, comment))
                .thenReturn(Optional.empty());

        assertThat(mapper.toResponse(comment, reader).userVote()).isNull();
    }


    @Test
    void aCommentReadBySomeoneWhoHasVotedCarriesTheirVote() {
        Comment comment = comment(ContentStatus.VISIBLE);
        Student reader = student();
        CommentVote vote = new CommentVote();
        vote.setVote(VoteType.UP);
        when(commentVoteRepository.findByStudentAndComment(reader, comment))
                .thenReturn(Optional.of(vote));

        assertThat(mapper.toResponse(comment, reader).userVote())
                .isEqualTo(VoteType.UP);
    }


    @Test
    void anAnswerReadAnonymouslyCarriesNoUserVote() {
        Answer answer = answer(ContentStatus.VISIBLE);

        assertThat(mapper.toResponse(answer, null).userVote()).isNull();
        verify(answerVoteRepository, never()).findByStudentAndAnswer(any(), any());
    }


    @Test
    void anAnswerReadBySomeoneWhoHasNotVotedCarriesNoUserVote() {
        Answer answer = answer(ContentStatus.VISIBLE);
        Student reader = student();
        when(answerVoteRepository.findByStudentAndAnswer(reader, answer))
                .thenReturn(Optional.empty());

        assertThat(mapper.toResponse(answer, reader).userVote()).isNull();
    }


    @Test
    void anAnswerReadBySomeoneWhoHasVotedCarriesTheirVote() {
        Answer answer = answer(ContentStatus.VISIBLE);
        Student reader = student();
        AnswerVote vote = new AnswerVote();
        vote.setVote(VoteType.DOWN);
        when(answerVoteRepository.findByStudentAndAnswer(reader, answer))
                .thenReturn(Optional.of(vote));

        assertThat(mapper.toResponse(answer, reader).userVote())
                .isEqualTo(VoteType.DOWN);
    }


    /**
     * An answer an administrator has hidden must not reach a student, the same way a hidden
     * comment does not. The filter is on the answer's own status, so a visible comment can still
     * carry hidden answers -- which is the case that would leak if this branch went untested.
     */
    @Test
    void hiddenAnswersAreLeftOutOfAVisibleComment() {
        Comment comment = comment(ContentStatus.VISIBLE);
        Answer visible = answer(ContentStatus.VISIBLE);
        Answer hidden = answer(ContentStatus.HIDDEN);
        comment.setAnswers(List.of(visible, hidden));

        CommentResponse response = mapper.toResponse(comment, null);

        assertThat(response.answers()).hasSize(1);
        // AnswerResponse names its first component commentID, but it is filled with the
        // answer's id -- a misleading name in a client-visible contract, left alone here.
        assertThat(response.answers().getFirst().commentID()).isEqualTo(visible.getId());
    }

    @Test
    void deletedCommentAuthorIsShownAsDeletedUser() {
        Comment comment = comment(ContentStatus.VISIBLE);
        comment.setStudent(deletedStudent());

        CommentResponse response = mapper.toResponse(comment, null);

        assertThat(response.studentUsername())
                .isEqualTo("Deleted User");
    }

    @Test
    void deletedCommentAuthorUsesDeletedUserProfilePicture() {
        Comment comment = comment(ContentStatus.VISIBLE);
        Student deleted = deletedStudent();
        comment.setStudent(deleted);

        CommentResponse response = mapper.toResponse(comment, null);

        assertThat(response.profilePicture())
                .isEqualTo(ProfilePictureGenerator.generateProfilePicture(deleted));

        assertThat(response.profilePicture())
                .contains("seed=DeletedUser");
    }

    @Test
    void deletedAnswerAuthorIsShownAsDeletedUser() {
        Answer answer = answer(ContentStatus.VISIBLE);
        answer.setStudent(deletedStudent());

        var response = mapper.toResponse(answer, null);

        assertThat(response.studentUsername())
                .isEqualTo("Deleted User");
    }

    @Test
    void deletedAnswerAuthorUsesDeletedUserProfilePicture() {
        Answer answer = answer(ContentStatus.VISIBLE);
        Student deleted = deletedStudent();
        answer.setStudent(deleted);

        var response = mapper.toResponse(answer, null);

        assertThat(response.profilePicture())
                .isEqualTo(ProfilePictureGenerator.generateProfilePicture(deleted));

        assertThat(response.profilePicture())
                .contains("seed=DeletedUser");
    }

    @Test
    void activeCommentAuthorKeepsUsername() {
        Comment comment = comment(ContentStatus.VISIBLE);

        CommentResponse response = mapper.toResponse(comment, null);

        assertThat(response.studentUsername())
                .isEqualTo("ada");
    }

    @Test
    void activeAnswerAuthorKeepsUsername() {
        Answer answer = answer(ContentStatus.VISIBLE);

        var response = mapper.toResponse(answer, null);

        assertThat(response.studentUsername())
                .isEqualTo("ada");
    }


}

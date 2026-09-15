package com.pse.user.service;

import com.pse.shared.dto.BasicResponse;
import com.pse.user.dto.UserRatingResponse;
import com.pse.auth.service.SessionRevoker;
import com.pse.user.dto.StudentProfileResponse;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A thin facade over the student service, with one piece of logic of its own: deleting an
 * account has to revoke the tokens issued to it. Without that the account is DELETED while
 * every issued token stays valid until it expires, which for a student is a full year --
 * so the tests that matter here are the one proving the revocation happens and the one
 * proving it is conditional on the deletion actually having succeeded.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTests {

    private static final UUID STUDENT_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

    @Mock
    private SessionRevoker sessionRevoker;

    @Mock
    private StudentService studentService;

    private AccountService service() {
        return new AccountService(sessionRevoker, studentService);
    }

    private static Student student() {
        Student student = new Student();
        student.setId(STUDENT_ID);
        student.setKitEmail("ada@kit.edu");
        return student;
    }

    // ---------- read-through delegations ----------

    @Test
    void getUserInformation_delegatesToTheStudentServiceWithTheStudentId() {
        // Given
        StudentProfileResponse expected =
                new StudentProfileResponse("Success", true, "ada", "url", 3.0, 2, 1, 4);
        when(studentService.getUserInformation(STUDENT_ID)).thenReturn(expected);

        // When
        StudentProfileResponse response = service().getUserInformation(student());

        // Then -- returned unchanged; the facade adds nothing on this path
        assertThat(response).isSameAs(expected);
        verifyNoInteractions(sessionRevoker);
    }

    @Test
    void getUserRatings_delegatesToTheStudentServiceWithTheStudentId() {
        // Given
        UserRatingResponse expected = new UserRatingResponse("Success", true, List.of());
        when(studentService.getUserRatings(STUDENT_ID)).thenReturn(expected);

        // When
        UserRatingResponse response = service().getUserRatings(student());

        // Then
        assertThat(response).isSameAs(expected);
        verifyNoInteractions(sessionRevoker);
    }

    // ---------- deleteAccount ----------

    @Test
    void deleteAccount_successfulDeletion_revokesEveryIssuedToken() {
        // Given
        Student student = student();
        when(studentService.deleteAccount(student))
                .thenReturn(new BasicResponse("Deleted", true));

        // When
        BasicResponse response = service().deleteAccount(student);

        // Then
        assertThat(response.success()).isTrue();
        verify(sessionRevoker).invalidateAllAuthTokensForEmail("ada@kit.edu");
    }

    /**
     * The rule that matters, in the shape it actually arrives in. A soft delete that did not
     * happen must not log the account out: the caller is still a live student, and revoking
     * here would sign them out of a session that is still legitimately theirs.
     *
     * <p>Since F-5 the failure is a thrown {@code ApiException} rather than a response
     * carrying {@code success = false}, so this is what the path looks like now -- the
     * exception travels on to the handler and the revocation is never reached.
     */
    @Test
    void deleteAccount_failedDeletion_leavesTheTokensAlone() {
        // Given
        Student student = student();
        when(studentService.deleteAccount(student))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Student not found"));

        // When / Then
        assertThatThrownBy(() -> service().deleteAccount(student))
                .isInstanceOf(ApiException.class)
                .hasMessage("Student not found");

        verifyNoInteractions(sessionRevoker);
    }

    /**
     * The same rule stated against the collaborator's contract rather than against today's
     * implementation of it. {@code StudentService} cannot return this shape any more, but
     * {@code AccountService} does not know that -- and the guard is what keeps the rule true
     * if the way a failed deletion is signalled ever changes again.
     */
    @Test
    void deleteAccount_unsuccessfulResponse_stillLeavesTheTokensAlone() {
        // Given
        Student student = student();
        when(studentService.deleteAccount(student))
                .thenReturn(new BasicResponse("Not deleted", false));

        // When
        BasicResponse response = service().deleteAccount(student);

        // Then
        assertThat(response.success()).isFalse();
        verifyNoInteractions(sessionRevoker);
    }

    // ---------- requestLogout ----------

    /**
     * The count comes back from the revocation and is embedded in the message, so it is
     * part of the response contract rather than a log line.
     */
    @Test
    void requestLogout_embedsTheNumberOfInvalidatedSessionsInTheMessage() {
        // Given
        when(sessionRevoker.invalidateAllAuthTokensForEmail("ada@kit.edu")).thenReturn(3);

        // When
        BasicResponse response = service().requestLogout(student());

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Logged out successfully (3 sessions)");
    }

    /** Logging out with nothing to revoke is still a success, not a failure. */
    @Test
    void requestLogout_noActiveSessions_stillReportsSuccess() {
        // Given
        when(sessionRevoker.invalidateAllAuthTokensForEmail("ada@kit.edu")).thenReturn(0);

        // When
        BasicResponse response = service().requestLogout(student());

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Logged out successfully (0 sessions)");
    }
}

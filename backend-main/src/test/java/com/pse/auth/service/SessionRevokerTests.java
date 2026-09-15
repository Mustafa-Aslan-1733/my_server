package com.pse.auth.service;

import com.pse.config.properties.RateLimitProperties;
import com.pse.config.properties.AuthProperties;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import com.pse.moderation.model.Admin;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;


import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ending sessions, and what that leaves in the audit log.
 *
 * <p>Split out of {@code AuthServiceTests} with the code it covers; the test bodies are
 * unchanged. It also moves into the mirror package, which the original was one level
 * above.
 */
@ExtendWith(MockitoExtension.class)
class SessionRevokerTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    private static final AuthProperties LIFETIMES =
            new AuthProperties(Duration.ofDays(1), Duration.ofDays(365), Duration.ofMinutes(5));

    private static final RateLimitProperties RATE_LIMITS =
            new RateLimitProperties(Duration.ofMinutes(15), "x".repeat(32), 3, 20, 10, 30);

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private AuditWriter auditWriter;

    private SessionRevoker sessionRevoker;

    @BeforeEach
    void setUp() {
        sessionRevoker = new SessionRevoker(tokenRepository, new SessionAuditWriter(auditWriter));
    }


    private Student activeStudent(String email) {
        Student student = new Student();
        student.setKitEmail(email);
        student.setUsername("student");
        student.setStatus(UserStatus.ACTIVE);
        return student;
    }




    @Test
    void logoutRevokesCurrentStudentToken() {

        AuthenticatedUser principal = mock(AuthenticatedUser.class);

        Student student = mock(Student.class);

        Token token = mock(Token.class);

        when(principal.token())
                .thenReturn(token);

        when(principal.student())
                .thenReturn(student);

        when(principal.isAdmin())
                .thenReturn(false);

        when(token.isRevoked())
                .thenReturn(false);

        when(student.getUsername())
                .thenReturn("student");


        BasicResponse response =
                sessionRevoker.logout(principal);


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Logged out successfully");


        verify(token).setRevoked(true);

        verify(tokenRepository).save(token);

        verify(auditWriter).writeStudentAction(
                        eq(student),
                        eq(AuditAction.USER_LOGOUT),
                        eq(AuditTargetType.USER_SESSION),
                        nullable(UUID.class),
                        eq("student session"),
                        anyMap()
                );
    }



    @Test
    void logoutDoesNotSaveAlreadyRevokedTokenAgain() {

        AuthenticatedUser principal = mock(AuthenticatedUser.class);

        Token token = mock(Token.class);

        when(principal.token())
                .thenReturn(token);

        when(token.isRevoked())
                .thenReturn(true);


        BasicResponse response = sessionRevoker.logout(principal);


        assertThat(response.success()).isTrue();

        verify(token, never()).setRevoked(anyBoolean());

        verify(tokenRepository, never()).save(any());

        verifyNoInteractions(auditWriter);
    }




    @Test
    void logoutAllRevokesAllActiveTokens() {

        AuthenticatedUser principal = mock(AuthenticatedUser.class);

        Student student = mock(Student.class);

        Token currentToken = mock(Token.class);

        Token secondToken = mock(Token.class);

        Token alreadyRevoked = mock(Token.class);


        when(principal.student())
                .thenReturn(student);

        when(principal.token())
                .thenReturn(currentToken);

        when(principal.isAdmin())
                .thenReturn(false);

        when(student.getKitEmail())
                .thenReturn(
                        "student@student.kit.edu"
                );

        when(student.getUsername())
                .thenReturn("student");

        when(currentToken.isRevoked())
                .thenReturn(false);

        when(secondToken.isRevoked())
                .thenReturn(false);

        when(alreadyRevoked.isRevoked())
                .thenReturn(true);


        List<Token> tokens = List.of(
                        currentToken,
                        secondToken,
                        alreadyRevoked
                );

        when(tokenRepository.findByStudentOrEmail(student, "student@student.kit.edu"))
                .thenReturn(tokens);


        BasicResponse response = sessionRevoker.logoutAll(principal);


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Logged out from all devices (2 sessions)");


        verify(currentToken).setRevoked(true);

        verify(secondToken).setRevoked(true);

        verify(alreadyRevoked, never()).setRevoked(true);

        verify(tokenRepository).saveAll(tokens);
    }




    @Test
    void invalidateAllAuthTokensForEmailRevokesEveryToken() {

        Token first = mock(Token.class);

        Token second = mock(Token.class);

        List<Token> tokens = List.of(first, second);

        when(tokenRepository.findByEmail("student@student.kit.edu"))
                .thenReturn(tokens);


        int result = sessionRevoker.invalidateAllAuthTokensForEmail("student@student.kit.edu");


        assertThat(result).isEqualTo(2);

        verify(first).setRevoked(true);

        verify(second).setRevoked(true);

        verify(tokenRepository).saveAll(tokens);
    }


    @Test
    void logoutAll_administrator_recordsAnAdminLogoutWithTheNumberOfRevokedSessions() {
        Student student = activeStudent("admin@student.kit.edu");
        Token current = new Token();
        Token other = new Token();
        Token alreadyRevoked = new Token();
        alreadyRevoked.setRevoked(true);
        AuthenticatedUser principal = new AuthenticatedUser(student, current, new Admin());
        when(tokenRepository.findByStudentOrEmail(student, student.getKitEmail()))
                .thenReturn(List.of(current, other, alreadyRevoked));

        BasicResponse response = sessionRevoker.logoutAll(principal);

        assertThat(response.message()).contains("2 sessions");
        verify(auditWriter).write(
                eq(principal.admin()),
                eq(AuditAction.ADMIN_LOGOUT),
                eq(AuditTargetType.ADMIN_SESSION),
                nullable(UUID.class),
                eq("student all sessions"),
                anyMap(),
                argThat(metadata -> Integer.valueOf(2).equals(metadata.get("revokedSessions"))));
        verify(auditWriter, never()).writeStudentAction(
                any(), any(), any(), any(), any(), anyMap());
    }
}

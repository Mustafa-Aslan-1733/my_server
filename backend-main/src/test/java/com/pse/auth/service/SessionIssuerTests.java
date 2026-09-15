package com.pse.auth.service;

import com.pse.config.properties.RateLimitProperties;
import com.pse.config.properties.AuthProperties;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.auth.dto.request.LoginRequest;
import com.pse.auth.dto.response.LoginResponse;
import com.pse.auth.model.SessionType;
import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import org.mockito.ArgumentCaptor;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.Mock;

import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Step two of a login: the code is spent and a session is minted, or the attempt is refused.
 *
 * <p>Split out of {@code AuthServiceTests} with the code it covers; the test bodies are
 * unchanged. It also moves into the mirror package, which the original was one level
 * above.
 */
@ExtendWith(MockitoExtension.class)
class SessionIssuerTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    private static final AuthProperties LIFETIMES =
            new AuthProperties(Duration.ofDays(1), Duration.ofDays(365), Duration.ofMinutes(5));

    private static final RateLimitProperties RATE_LIMITS =
            new RateLimitProperties(Duration.ofMinutes(15), "x".repeat(32), 3, 20, 10, 30);

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private AdminBootstrapPolicy bootstrapPolicy;

    @Mock
    private AuditWriter auditWriter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HttpServletRequest httpRequest;

    private SessionIssuer sessionIssuer;

    @BeforeEach
    void setUp() {
        sessionIssuer = new SessionIssuer(
                studentRepository,
                adminRepository,
                tokenRepository,
                otpService,
                rateLimitService,
                bootstrapPolicy,
                // Real rather than mocked: both only forward to collaborators already
                // mocked above, and every assertion here is about what reaches those.
                new StudentProvisioning(studentRepository, CLOCK),
                new SessionAuditWriter(auditWriter),
                auditWriter,
                eventPublisher,
                CLOCK,
                LIFETIMES,
                RATE_LIMITS);
    }


    // ------------------------------------------------------------------------------------
    // Batch 6. The arms the tests above leave open, in the house style: what the session tier
    // resolves to, what an administrator's logout records, and the input normalisation that
    // every login goes through. Named method_scenario_expectation; the tests above predate
    // that scheme and are left as they are.
    // ------------------------------------------------------------------------------------

    private LoginRequest loginRequest(String email, SessionType sessionType) {
        LoginRequest request = mock(LoginRequest.class);
        when(request.email()).thenReturn(email);
        lenient().when(request.loginToken()).thenReturn("ABC234");
        lenient().when(request.sessionType()).thenReturn(sessionType);
        return request;
    }


    private Student activeStudent(String email) {
        Student student = new Student();
        student.setKitEmail(email);
        student.setUsername("student");
        student.setStatus(UserStatus.ACTIVE);
        return student;
    }


    private void loginReaches(String email, Student student) {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(rateLimitService.emailSubject(anyString())).thenReturn("email");
        when(rateLimitService.ipSubject(anyString())).thenReturn("ip");
        when(studentRepository.findByKitEmail(email))
                .thenReturn(student == null ? Optional.empty() : Optional.of(student));
        when(otpService.consume(email, "ABC234")).thenReturn(true);
        lenient().when(tokenRepository.save(any(Token.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }




    @Test
    void loginReturnsErrorWhenCodeIsInvalid() {

        LoginRequest request = mock(LoginRequest.class);

        when(request.email())
                .thenReturn("student@student.kit.edu");

        when(request.loginToken())
                .thenReturn("wrong");

        when(httpRequest.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(rateLimitService.emailSubject(anyString()))
                .thenReturn("email");

        when(rateLimitService.ipSubject(anyString()))
                .thenReturn("ip");

        when(studentRepository.findByKitEmail("student@student.kit.edu"))
                .thenReturn(Optional.empty());

        when(otpService.consume("student@student.kit.edu", "wrong"))
                .thenReturn(false);


        assertThatThrownBy(() -> sessionIssuer.login(
                        request,
                        httpRequest
                )).isInstanceOf(ApiException.class);


        verify(rateLimitService).consume(
                        RateLimitService.LOGIN_FAILURE,
                        "email",
                        10
                );

        verify(rateLimitService).consume(
                        RateLimitService.LOGIN_FAILURE,
                        "ip",
                        30
                );

        verify(tokenRepository, never()).save(any());
    }



    @Test
    void blockedStudentCannotLogin() {

        LoginRequest request = mock(LoginRequest.class);

        Student student = mock(Student.class);

        when(request.email())
                .thenReturn("blocked@student.kit.edu");

        when(request.loginToken())
                .thenReturn("ABC234");

        when(httpRequest.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(rateLimitService.emailSubject(anyString()))
                .thenReturn("email");

        when(rateLimitService.ipSubject(anyString()))
                .thenReturn("ip");

        when(studentRepository.findByKitEmail("blocked@student.kit.edu"))
                .thenReturn(
                Optional.of(student)
        );

        when(student.getStatus())
                .thenReturn(UserStatus.BLOCKED);

        when(adminRepository.findByStudent(student))
                .thenReturn(Optional.empty());


        assertThatThrownBy(() -> sessionIssuer.login(
                        request,
                        httpRequest
                )).isInstanceOf(ApiException.class);


        verify(otpService, never()).consume(anyString(), anyString());

        verify(rateLimitService).consume(
                        RateLimitService.LOGIN_FAILURE,
                        "email",
                        10
                );

        verify(auditWriter).writeRefusal(
                        eq(student),
                        eq(false),
                        eq(AuditAction.LOGIN_REFUSED),
                        eq(AuditTargetType.USER_SESSION),
                        isNull(),
                        anyString(),
                        anyMap()
                );
    }



    @Test
    void loginExistingStudentCreatesSession() {

        LoginRequest request = mock(LoginRequest.class);

        Student student = mock(Student.class);

        UUID studentId = UUID.randomUUID();

        when(request.email())
                .thenReturn("student@student.kit.edu");

        when(request.loginToken())
                .thenReturn(" ABC234 ");

        when(request.sessionType())
                .thenReturn(null);

        when(httpRequest.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(httpRequest.getHeader("User-Agent"))
                .thenReturn("JUnit");

        when(rateLimitService.emailSubject(anyString()))
                .thenReturn("email");

        when(rateLimitService.ipSubject(anyString()))
                .thenReturn("ip");

        when(studentRepository.findByKitEmail("student@student.kit.edu"))
                .thenReturn(
                Optional.of(student)
        );


        when(student.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(student.getUsername())
                .thenReturn("student");

        when(adminRepository.findByStudent(student))
                .thenReturn(Optional.empty());

        when(bootstrapPolicy.shouldBootstrap("student@student.kit.edu"))
                .thenReturn(false);

        when(otpService.consume("student@student.kit.edu", "ABC234"))
                .thenReturn(true);

        when(tokenRepository.save(any(Token.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );


        LoginResponse response = sessionIssuer.login(
                        request,
                        httpRequest
                );


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Login successful");

        assertThat(response.authToken()).isNotNull();


        verify(studentRepository).save(student);


        verify(tokenRepository).save(
                        argThat(token ->
                                token.getStudent() == student
                                        && token.getSessionType()
                                        == SessionType.APP
                                        && !token.isRevoked()
                        )
                );


        verify(rateLimitService).reset(
                        RateLimitService.LOGIN_FAILURE,
                        "email"
                );


        verify(auditWriter).writeStudentAction(
                        eq(student),
                        eq(AuditAction.USER_LOGIN),
                        eq(AuditTargetType.USER_SESSION),
                        nullable(UUID.class),
                        eq("student session"),
                        anyMap()
                );


        verify(eventPublisher).publishEvent(any(SuccessfulLoginEvent.class));
    }


    @Test
    void login_noSessionTypeRequestedAndNoAdminRow_issuesAnAppSession() {
        String email = "student@student.kit.edu";
        Student student = activeStudent(email);
        loginReaches(email, student);
        when(adminRepository.findByStudent(student)).thenReturn(Optional.empty());
        when(bootstrapPolicy.shouldBootstrap(email)).thenReturn(false);

        sessionIssuer.login(loginRequest(email, null), httpRequest);

        verify(tokenRepository).save(argThat(t -> t.getSessionType() == SessionType.APP));
    }


    @Test
    void login_noSessionTypeRequestedButAnAdminRowExists_issuesAnAdminSession() {
        // The default follows the account when the client says nothing. It is the only place
        // an administrator session is handed out without being asked for, which is why the
        // arm is worth stating rather than inferring from the one below.
        String email = "admin@student.kit.edu";
        Student student = activeStudent(email);
        loginReaches(email, student);
        when(adminRepository.findByStudent(student)).thenReturn(Optional.of(new Admin()));

        sessionIssuer.login(loginRequest(email, null), httpRequest);

        verify(tokenRepository).save(argThat(t -> t.getSessionType() == SessionType.ADMIN));
    }


    @Test
    void login_adminSessionAskedForWithoutAnAdminRow_isRefused() {
        String email = "student@student.kit.edu";
        Student student = activeStudent(email);
        loginReaches(email, student);
        when(adminRepository.findByStudent(student)).thenReturn(Optional.empty());
        when(bootstrapPolicy.shouldBootstrap(email)).thenReturn(false);

        assertThatThrownBy(() ->
                sessionIssuer.login(loginRequest(email, SessionType.ADMIN), httpRequest))
                .isInstanceOf(ApiException.class)
                .hasMessage("Admin access required");

        verify(tokenRepository, never()).save(any());
    }


    @Test
    void login_firstLoginOfABootstrapAddress_createsTheAdminRowBeforeTheTierIsResolved() {
        // The ordering is the point: resolving the tier first would refuse the very request
        // that is supposed to create the administrator.
        String email = "boss@student.kit.edu";
        Student student = activeStudent(email);
        loginReaches(email, student);
        when(adminRepository.findByStudent(student)).thenReturn(Optional.empty());
        when(bootstrapPolicy.shouldBootstrap(email)).thenReturn(true);
        when(adminRepository.save(any(Admin.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        sessionIssuer.login(loginRequest(email, SessionType.ADMIN), httpRequest);

        verify(adminRepository).save(argThat(admin -> admin.getStudent() == student));
        verify(tokenRepository).save(argThat(t -> t.getSessionType() == SessionType.ADMIN));
    }


    @Test
    void login_addressThatIsNotAKitAddress_isRefusedBeforeAnyRateLimitIsConsumed() {
        assertThatThrownBy(() ->
                sessionIssuer.login(loginRequest("someone@gmail.com", null), httpRequest))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid KIT email");

        verifyNoInteractions(rateLimitService, otpService, tokenRepository);
    }


    @Test
    void login_newAccount_getsAUsernameThatIsNotAlreadyTaken() {
        // The generator can collide; the loop is what makes the column's unique constraint a
        // formality rather than a 500 on somebody's first login.
        String email = "fresh@student.kit.edu";
        loginReaches(email, null);
        when(studentRepository.existsByUsername(anyString())).thenReturn(true, false);
        when(studentRepository.save(any(Student.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminRepository.findByStudent(any())).thenReturn(Optional.empty());
        when(bootstrapPolicy.shouldBootstrap(email)).thenReturn(false);

        sessionIssuer.login(loginRequest(email, null), httpRequest);

        verify(studentRepository, times(2)).existsByUsername(anyString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void login_withoutAUsableUserAgentHeader_publishesTheLoginAsUnknown(String userAgent) {
        String email = "student@student.kit.edu";
        Student student = activeStudent(email);
        loginReaches(email, student);
        when(httpRequest.getHeader("User-Agent")).thenReturn(userAgent);
        when(adminRepository.findByStudent(student)).thenReturn(Optional.empty());
        when(bootstrapPolicy.shouldBootstrap(email)).thenReturn(false);

        sessionIssuer.login(loginRequest(email, null), httpRequest);

        ArgumentCaptor<SuccessfulLoginEvent> event =
                ArgumentCaptor.forClass(SuccessfulLoginEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userAgent()).isEqualTo("Unknown");
    }


    @Test
    void login_absurdlyLongUserAgent_isTruncatedBeforeItIsStored() {
        // Attacker-supplied and unbounded; the column is not. Truncating here is what keeps a
        // 100 kB header from failing the insert on an otherwise valid login.
        String email = "student@student.kit.edu";
        Student student = activeStudent(email);
        loginReaches(email, student);
        when(httpRequest.getHeader("User-Agent")).thenReturn("U".repeat(600));
        when(adminRepository.findByStudent(student)).thenReturn(Optional.empty());
        when(bootstrapPolicy.shouldBootstrap(email)).thenReturn(false);

        sessionIssuer.login(loginRequest(email, null), httpRequest);

        ArgumentCaptor<SuccessfulLoginEvent> event =
                ArgumentCaptor.forClass(SuccessfulLoginEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userAgent()).hasSize(500);
    }


    @Test
    void login_noEmailAtAll_isRefusedAsAnInvalidKitAddress() {
        LoginRequest request = mock(LoginRequest.class);
        when(request.email()).thenReturn(null);

        assertThatThrownBy(() -> sessionIssuer.login(request, httpRequest))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid KIT email");
    }


    @Test
    void login_deletedAccount_isRefusedTheSameWayABlockedOneIs() {
        String email = "deleted@student.kit.edu";
        Student student = activeStudent(email);
        student.setStatus(UserStatus.DELETED);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.emailSubject(anyString())).thenReturn("email");
        when(rateLimitService.ipSubject(anyString())).thenReturn("ip");
        when(studentRepository.findByKitEmail(email)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> sessionIssuer.login(loginRequest(email, null), httpRequest))
                .isInstanceOf(ApiException.class)
                .hasMessage("Account is not active");

        verify(otpService, never()).consume(anyString(), anyString());
        verify(tokenRepository, never()).save(any());
    }
}

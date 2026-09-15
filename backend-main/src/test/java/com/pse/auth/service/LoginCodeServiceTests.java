package com.pse.auth.service;

import com.pse.config.properties.RateLimitProperties;
import com.pse.config.properties.AuthProperties;
import com.pse.auth.dto.request.OTPRequest;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.mockito.InOrder;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;


import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Step one of a login: the code is mailed, or deliberately not.
 *
 * <p>Split out of {@code AuthServiceTests} with the code it covers; the test bodies are
 * unchanged. It also moves into the mirror package, which the original was one level
 * above.
 */
@ExtendWith(MockitoExtension.class)
class LoginCodeServiceTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    private static final AuthProperties LIFETIMES =
            new AuthProperties(Duration.ofDays(1), Duration.ofDays(365), Duration.ofMinutes(5));

    private static final RateLimitProperties RATE_LIMITS =
            new RateLimitProperties(Duration.ofMinutes(15), "x".repeat(32), 3, 20, 10, 30);

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private LoginCodeDelivery loginCodeDelivery;

    @Mock
    private AccountReactivator accountReactivator;

    @Mock
    private HttpServletRequest httpRequest;

    private LoginCodeService loginCodeService;

    @BeforeEach
    void setUp() {
        loginCodeService = new LoginCodeService(studentRepository, otpService, rateLimitService,
                loginCodeDelivery, RATE_LIMITS, accountReactivator);
    }




    /**
     * Characterization of F-48, at the layer where the ordering is visible. <b>Current
     * behaviour, not intended behaviour</b>: the open question is {@code docs/TODO.md} item 39,
     * and {@code AuthApiIntegrationTests.anAnonymousLoginCodeRequestBringsBackAnAccountThatDeletedItself}
     * pins the same rule end to end, through the public route and with no credentials at all.
     *
     * <p>What this one adds is the <b>sequence</b>, which is the whole defect: the account is
     * back before {@code OtpService.issue} has produced a code and long before anybody could
     * have entered one. Reviving an account therefore costs a caller nothing but knowing the
     * address. If the rule becomes "the code has to be entered", this ordering is what breaks,
     * and it should: invert it, do not delete it.
     *
     * <p>The stub mutates the account it is handed because the production collaborator does --
     * {@code requestLogin} reads {@code getStatus()} again below the branch to decide whether a
     * code may be issued at all, so a reactivation that did not mutate the instance would leave
     * the caller refusing to send to an account it has just revived. Stating that here keeps the
     * coupling visible instead of leaving it to be rediscovered.
     */
    @Test
    void requestLoginRevivesADeletedAccountBeforeTheCodeIsEvenIssued() {

        OTPRequest request = mock(OTPRequest.class);

        Student deleted = new Student();
        deleted.setUsername("gone");
        deleted.setKitEmail("gone@student.kit.edu");
        deleted.setStatus(UserStatus.DELETED);

        when(request.email()).thenReturn("gone@student.kit.edu");
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimitService.emailSubject("gone@student.kit.edu")).thenReturn("email-subject");
        when(rateLimitService.ipSubject("203.0.113.7")).thenReturn("ip-subject");
        when(studentRepository.findByKitEmail("gone@student.kit.edu"))
                .thenReturn(Optional.of(deleted));
        when(otpService.issue("gone@student.kit.edu")).thenReturn("ABC234");

        doAnswer(invocation -> {
            invocation.getArgument(0, Student.class).setStatus(UserStatus.ACTIVE);
            return null;
        }).when(accountReactivator).reactivate(deleted);


        BasicResponse response = loginCodeService.requestLogin(request, httpRequest);


        InOrder order = inOrder(accountReactivator, otpService, loginCodeDelivery);
        order.verify(accountReactivator).reactivate(deleted);
        order.verify(otpService).issue("gone@student.kit.edu");
        order.verify(loginCodeDelivery).send("gone@student.kit.edu", "ABC234");

        // Indistinguishable from the response a live address gets, which is deliberate and is
        // not the defect: a different answer here would make this route an account-enumeration
        // oracle. The defect is what happened to the account before the answer was written.
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Login code sent");
    }

    /**
     * Characterization of F-48's second half: <b>the revival outlives the failure of the request
     * that caused it</b>. Delivery throws, the caller is answered {@code 500}, the one-time
     * password is invalidated -- and the account stays revived, because
     * {@code AccountReactivator} committed before the mail was attempted.
     *
     * <p>That is deliberate in the sense that nothing was changed when the reactivation moved
     * into its own transaction: widening the boundary to cover delivery would have fixed half of
     * F-48 as a side effect of a refactor. Invert this when the product decision is taken.
     */
    @Test
    void theRevivalOfADeletedAccountOutlivesAFailureToSendTheCode() {

        OTPRequest request = mock(OTPRequest.class);

        Student deleted = new Student();
        deleted.setUsername("gone");
        deleted.setKitEmail("gone@student.kit.edu");
        deleted.setStatus(UserStatus.DELETED);

        when(request.email()).thenReturn("gone@student.kit.edu");
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimitService.emailSubject("gone@student.kit.edu")).thenReturn("email-subject");
        when(rateLimitService.ipSubject("203.0.113.7")).thenReturn("ip-subject");
        when(studentRepository.findByKitEmail("gone@student.kit.edu"))
                .thenReturn(Optional.of(deleted));
        when(otpService.issue("gone@student.kit.edu")).thenReturn("ABC234");

        doAnswer(invocation -> {
            invocation.getArgument(0, Student.class).setStatus(UserStatus.ACTIVE);
            return null;
        }).when(accountReactivator).reactivate(deleted);

        doThrow(new IllegalStateException("smtp is down"))
                .when(loginCodeDelivery).send("gone@student.kit.edu", "ABC234");


        assertThatThrownBy(() -> loginCodeService.requestLogin(request, httpRequest))
                .isInstanceOf(ApiException.class)
                .hasMessage("Could not send login code");


        verify(accountReactivator).reactivate(deleted);
        verify(otpService).invalidate("gone@student.kit.edu");
        assertThat(deleted.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void requestLoginNormalizesEmailAndSendsCode() {

        OTPRequest request = mock(OTPRequest.class);

        when(request.email())
                .thenReturn("  TEST@STUDENT.KIT.EDU ");

        when(httpRequest.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(rateLimitService.emailSubject("test@student.kit.edu"))
                .thenReturn("email-subject");

        when(rateLimitService.ipSubject("127.0.0.1"))
                .thenReturn("ip-subject");

        when(studentRepository.findByKitEmail("test@student.kit.edu"))
                .thenReturn(Optional.empty());

        when(otpService.issue("test@student.kit.edu"))
                .thenReturn("ABC234");


        BasicResponse response = loginCodeService.requestLogin(request, httpRequest);


        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Login code sent");


        verify(rateLimitService).consume(
                        RateLimitService.REQUEST_CODE,
                        "email-subject",
                        3
                );

        verify(rateLimitService).consume(
                        RateLimitService.REQUEST_CODE,
                        "ip-subject",
                        20
                );

        verify(otpService).issue(
                        "test@student.kit.edu"
                );

        verify(loginCodeDelivery).send(
                        "test@student.kit.edu",
                        "ABC234"
                );
    }



    @Test
    void requestLoginDoesNotSendCodeForBlockedStudent() {

        OTPRequest request = mock(OTPRequest.class);

        Student student = mock(Student.class);

        when(request.email())
                .thenReturn("blocked@student.kit.edu");

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


        BasicResponse response = loginCodeService.requestLogin(request, httpRequest);

        assertThat(response.success()).isTrue();

        assertThat(response.message()).isEqualTo("Login code sent");


        verify(otpService, never()).issue(anyString());

        verifyNoInteractions(loginCodeDelivery);
    }



    @Test
    void requestLoginInvalidatesCodeWhenDeliveryFails() {

        OTPRequest request = mock(OTPRequest.class);

        when(request.email())
                .thenReturn("student@student.kit.edu");

        when(httpRequest.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(rateLimitService.emailSubject(anyString()))
                .thenReturn("email");

        when(rateLimitService.ipSubject(anyString()))
                .thenReturn("ip");

        when(studentRepository.findByKitEmail(anyString()))
                .thenReturn(Optional.empty());

        when(otpService.issue("student@student.kit.edu"))
                .thenReturn("ABC234");

        doThrow(
                new RuntimeException("mail failed")
        )
                .when(loginCodeDelivery)
                .send(
                        "student@student.kit.edu",
                        "ABC234"
                );


        assertThatThrownBy(() -> loginCodeService.requestLogin(
                        request,
                        httpRequest
                )).isInstanceOf(ApiException.class);


        verify(otpService).invalidate("student@student.kit.edu");
    }
}

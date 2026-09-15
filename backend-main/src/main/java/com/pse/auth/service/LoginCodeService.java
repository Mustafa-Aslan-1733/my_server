package com.pse.auth.service;

import com.pse.auth.dto.request.OTPRequest;
import com.pse.config.properties.RateLimitProperties;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.shared.util.KitEmail;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Step one of a login: mails a one-time password to a KIT address.
 *
 * <p>Both APIs share this: {@code POST /auth/request-login} and
 * {@code POST /admin/auth/request-login} are the same operation, and which kind of session
 * the code eventually buys is decided later, by {@link SessionIssuer}.
 */
@Service
public class LoginCodeService {

    private final StudentRepository studentRepository;
    private final OtpService otpService;
    private final RateLimitService rateLimitService;
    private final LoginCodeDelivery loginCodeDelivery;
    private final RateLimitProperties rateLimits;

    private final AccountReactivator accountReactivator;

    /**
     * Creates LoginCodeService.
     *
     * @param studentRepository the studentRepository
     * @param otpService the otpService
     * @param rateLimitService the rateLimitService
     * @param loginCodeDelivery the loginCodeDelivery
     * @param rateLimits the rateLimits
     * @param accountReactivator the accountReactivator
     */
    public LoginCodeService(
            StudentRepository studentRepository,
            OtpService otpService,
            RateLimitService rateLimitService,
            LoginCodeDelivery loginCodeDelivery,
            RateLimitProperties rateLimits,
            AccountReactivator accountReactivator
    ) {
        this.studentRepository = studentRepository;
        this.otpService = otpService;
        this.rateLimitService = rateLimitService;
        this.loginCodeDelivery = loginCodeDelivery;
        this.rateLimits = rateLimits;
        this.accountReactivator = accountReactivator;
    }

    /**
     * Returns requestLogin.
     *
     * @param request the request
     * @param httpRequest the httpRequest
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    public BasicResponse requestLogin(OTPRequest request, HttpServletRequest httpRequest) {
        String email = KitEmail.requireKitAddress(request.email());
        String emailSubject = rateLimitService.emailSubject(email);
        String ipSubject = rateLimitService.ipSubject(httpRequest.getRemoteAddr());
        rateLimitService.consume(RateLimitService.REQUEST_CODE, emailSubject, rateLimits.requestEmail());
        rateLimitService.consume(RateLimitService.REQUEST_CODE, ipSubject, rateLimits.requestIp());

        // The response stays identical to the success case on purpose: a
        // distinguishable answer here would turn this endpoint into an account
        // enumeration oracle.
        Student existing = studentRepository.findByKitEmail(email).orElse(null);

        if (existing != null) {
            if (existing.getStatus() == UserStatus.DELETED) {
                // Requesting a code for a deleted account brings it back -- deliberately, since
                // commit bf86ee6. What is not deliberate is that it happens here, on an
                // unauthenticated request, rather than when the code is entered: see F-48 and
                // AccountReactivator, which owns that decision and now records the event.
                accountReactivator.reactivate(existing);
            } else if (!existing.getStatus().canLogIn()) {
                // Blocked/suspended accounts still must not receive a login code.
                return new BasicResponse("Login code sent", true);
            }
        }

        if (existing != null && !existing.getStatus().canLogIn()) {
            return new BasicResponse("Login code sent", true);
        }

        String code = otpService.issue(email);
        try {
            loginCodeDelivery.send(email, code);
        } catch (RuntimeException exception) {
            otpService.invalidate(email);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not send login code");
        }
        return new BasicResponse("Login code sent", true);
    }
}

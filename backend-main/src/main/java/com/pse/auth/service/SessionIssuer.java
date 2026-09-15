package com.pse.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.auth.dto.request.LoginRequest;
import com.pse.auth.dto.response.LoginResponse;
import com.pse.auth.model.SessionType;
import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import com.pse.config.properties.AuthProperties;
import com.pse.config.properties.RateLimitProperties;
import com.pse.moderation.model.Admin;
import com.pse.moderation.repository.AdminRepository;
import com.pse.security.TokenHasher;
import com.pse.shared.error.ApiException;
import com.pse.shared.util.KitEmail;
import com.pse.shared.util.UtcDates;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step two of a login: spends the mailed code and mints a session.
 *
 * <p>Everything the two login routes do that is not "who are you" lives here, including the
 * refusals -- and the refusals are the reason this is one method with one order of
 * operations rather than several: the status gate runs before the code is consumed so a
 * valid code is not burnt on a request that can only end in 403, and the admin gate runs
 * after the bootstrap block so a configured administrator's first login is not refused for a
 * row that same request was about to create.
 */
@Service
public class SessionIssuer {

    /**
     * Which API minted a session. It decides both what the token may be used for and how
     * long it lives: the panel logs in through {@code /admin/auth/login} and gets an
     * administrator session on the admin schedule, every app client logs in through
     * {@code /auth/login} and gets a year, administrators included. Deriving the lifetime
     * from the account instead is what used to put an administrator's phone on the
     * panel's expiry schedule.
     */
    private enum LoginApi {
        /**
         * The APP.
         */
        APP,
        /**
         * The ADMIN.
         */
        ADMIN
    }

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final TokenRepository tokenRepository;
    private final OtpService otpService;
    private final RateLimitService rateLimitService;
    private final AdminBootstrapPolicy bootstrapPolicy;
    private final StudentProvisioning studentProvisioning;
    private final SessionAuditWriter sessionAudit;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final AuthProperties lifetimes;
    private final RateLimitProperties rateLimits;

    /**
     * Creates SessionIssuer.
     *
     * @param studentRepository the studentRepository
     * @param adminRepository the adminRepository
     * @param tokenRepository the tokenRepository
     * @param otpService the otpService
     * @param rateLimitService the rateLimitService
     * @param bootstrapPolicy the bootstrapPolicy
     * @param studentProvisioning the studentProvisioning
     * @param sessionAudit the sessionAudit
     * @param auditWriter the auditWriter
     * @param eventPublisher the eventPublisher
     * @param clock the clock
     * @param lifetimes the lifetimes
     * @param rateLimits the rateLimits
     */
    public SessionIssuer(
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            TokenRepository tokenRepository,
            OtpService otpService,
            RateLimitService rateLimitService,
            AdminBootstrapPolicy bootstrapPolicy,
            StudentProvisioning studentProvisioning,
            SessionAuditWriter sessionAudit,
            AuditWriter auditWriter,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            AuthProperties lifetimes,
            RateLimitProperties rateLimits
    ) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.tokenRepository = tokenRepository;
        this.otpService = otpService;
        this.rateLimitService = rateLimitService;
        this.bootstrapPolicy = bootstrapPolicy;
        this.studentProvisioning = studentProvisioning;
        this.sessionAudit = sessionAudit;
        this.auditWriter = auditWriter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.lifetimes = lifetimes;
        this.rateLimits = rateLimits;
    }

    /**
     * Returns login.
     *
     * @param request the request
     * @param httpRequest the httpRequest
     * @return the result
     */
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        return issueSession(request, httpRequest, LoginApi.APP);
    }

    /**
     * Login for the admin panel. Only an account with an {@code admins} row gets through,
     * and the session is always an administrator session whatever the body asks for.
     *
     * @param request the request
     * @param httpRequest the httpRequest
     * @return the result
     */
    @Transactional
    public LoginResponse adminLogin(LoginRequest request, HttpServletRequest httpRequest) {
        return issueSession(request, httpRequest, LoginApi.ADMIN);
    }

    private LoginResponse issueSession(LoginRequest request, HttpServletRequest httpRequest, LoginApi api) {
        String email = KitEmail.requireKitAddress(request.email());
        String submittedCode = request.loginToken().trim();
        String emailSubject = rateLimitService.emailSubject(email);
        String ipSubject = rateLimitService.ipSubject(httpRequest.getRemoteAddr());

        // Refusals by this gate are deliberately not audited. It consumes nothing, so it
        // answers every request for the rest of the window; one record per refused request
        // would be unbounded. The attempts that filled the bucket are already in the log.
        rateLimitService.ensureAllowed(RateLimitService.LOGIN_FAILURE, emailSubject, rateLimits.loginEmail());
        rateLimitService.ensureAllowed(RateLimitService.LOGIN_FAILURE, ipSubject, rateLimits.loginIp());

        // Check the account status before consuming the code. Consuming first burns
        // a valid code on a request that can only ever end in 403.
        Student existing = studentRepository.findByKitEmail(email).orElse(null);
        if (existing != null && !existing.getStatus().canLogIn()) {
            // Counts against the failure budget the same way a wrong code does. Without
            // it this refusal never fills the bucket, and a known blocked address could
            // be replayed to write audit records without limit.
            consumeLoginFailure(emailSubject, ipSubject);
            auditLoginRefusal(existing, "ACCOUNT_" + existing.getStatus().name());
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
        }

        if (!otpService.consume(email, submittedCode)) {
            consumeLoginFailure(emailSubject, ipSubject);
            auditLoginRefusal(existing, "INVALID_CODE");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired login code");
        }

        Student student = existing == null ? studentProvisioning.create(email) : existing;
        student.setLastSeenAt(LocalDateTime.now(clock));
        studentRepository.save(student);

        Admin admin = adminRepository.findByStudent(student).orElse(null);
        if (admin == null && bootstrapPolicy.shouldBootstrap(email)) {
            admin = new Admin();
            admin.setStudent(student);
            admin = adminRepository.save(admin);
        }

        // After the bootstrap block above, never before it: the first login of a
        // configured bootstrap address would otherwise be refused for an admin row that
        // this very request was about to create.
        SessionType sessionType = api == LoginApi.ADMIN
                ? requireAdminSession(existing, admin, emailSubject, ipSubject)
                : resolveSessionType(request.sessionType(), admin);

        String rawToken = TokenGenerator.generateToken();
        Token token = new Token();
        token.setEmail(email);
        token.setStudent(student);
        token.setHash(TokenHasher.hash(rawToken));
        token.setSessionType(sessionType);
        token.setExpiresAt(LocalDateTime.now(clock).plus(
                api == LoginApi.ADMIN ? lifetimes.adminSessionTtl() : lifetimes.appSessionTtl()
        ));
        token = tokenRepository.save(token);

        boolean asAdmin = sessionType == SessionType.ADMIN;
        sessionAudit.recordLogin(
                asAdmin,
                admin,
                student,
                token.getId(),
                student.getUsername() + (asAdmin ? " admin session" : " session"),
                asAdmin
                        ? Map.of("method", "EMAIL_OTP", "api", api.name())
                        : Map.of("method", "EMAIL_OTP", "newAccount", existing == null, "api", api.name())
        );

        rateLimitService.reset(RateLimitService.LOGIN_FAILURE, emailSubject);
        eventPublisher.publishEvent(new SuccessfulLoginEvent(
                email,
                httpRequest.getRemoteAddr(),
                safeUserAgent(httpRequest.getHeader("User-Agent"))
        ));
        return new LoginResponse("Login successful", true, rawToken, UtcDates.format(token.getExpiresAt()));
    }

    private void consumeLoginFailure(String emailSubject, String ipSubject) {
        rateLimitService.consume(RateLimitService.LOGIN_FAILURE, emailSubject, rateLimits.loginEmail());
        rateLimitService.consume(RateLimitService.LOGIN_FAILURE, ipSubject, rateLimits.loginIp());
    }

    /**
     * Records a login the backend turned down. Written in its own transaction because
     * every caller throws immediately afterwards. Unknown emails are skipped: there is
     * no account to attribute the attempt to.
     *
     * <p>Every caller consumes the login-failure budget first, which is what bounds how
     * many of these a single address can produce in a window.
     */
    private void auditLoginRefusal(Student account, String reason) {
        if (account == null) {
            return;
        }
        auditWriter.writeRefusal(
                account,
                adminRepository.findByStudent(account).isPresent(),
                AuditAction.LOGIN_REFUSED,
                AuditTargetType.USER_SESSION,
                null,
                account.getUsername() + " login attempt",
                Map.of("reason", reason, "method", "EMAIL_OTP")
        );
    }

    /**
     * The admin API mints administrator sessions only, so an account without an
     * {@code admins} row cannot be served at all. The refusal costs a slot in the
     * login-failure budget and leaves a record, the same as every other refusal here:
     * without that, anyone holding a valid code could ask this endpoint which addresses
     * are administrators, unbudgeted and unlogged.
     */
    private SessionType requireAdminSession(Student account, Admin admin, String emailSubject, String ipSubject) {
        if (admin == null) {
            consumeLoginFailure(emailSubject, ipSubject);
            auditLoginRefusal(account, "NOT_AN_ADMIN");
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return SessionType.ADMIN;
    }

    private SessionType resolveSessionType(SessionType requestedSessionType, Admin admin) {
        if (requestedSessionType == null) {
            return admin == null ? SessionType.APP : SessionType.ADMIN;
        }
        if (requestedSessionType == SessionType.ADMIN && admin == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return requestedSessionType;
    }

    private String safeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        return userAgent.substring(0, Math.min(userAgent.length(), 500));
    }
}

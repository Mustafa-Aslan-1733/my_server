package com.pse.auth.controller;

import com.pse.auth.service.IdentityService;
import com.pse.auth.service.LoginCodeService;
import com.pse.auth.service.SessionIssuer;
import com.pse.auth.service.SessionRevoker;
import com.pse.auth.dto.request.LoginRequest;
import com.pse.auth.dto.request.OTPRequest;
import com.pse.auth.dto.request.ValidateCredentialsRequest;
import com.pse.auth.dto.response.AuthMeResponse;
import com.pse.shared.dto.BasicResponse;
import com.pse.auth.dto.response.LoginResponse;
import com.pse.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginCodeService loginCodeService;
    private final SessionIssuer sessionIssuer;
    private final SessionRevoker sessionRevoker;
    private final IdentityService identityService;

    public AuthController(
            LoginCodeService loginCodeService,
            SessionIssuer sessionIssuer,
            SessionRevoker sessionRevoker,
            IdentityService identityService
    ) {
        this.loginCodeService = loginCodeService;
        this.sessionIssuer = sessionIssuer;
        this.sessionRevoker = sessionRevoker;
        this.identityService = identityService;
    }

    @PostMapping("/request-login")
    public BasicResponse requestLogin(@Valid @RequestBody OTPRequest request, HttpServletRequest httpRequest) {
        return loginCodeService.requestLogin(request, httpRequest);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return sessionIssuer.login(request, httpRequest);
    }

    @PostMapping("/logout")
    public BasicResponse logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        return sessionRevoker.logout(principal);
    }

    @PostMapping("/logout-all")
    public BasicResponse logoutAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        return sessionRevoker.logoutAll(principal);
    }


    /**
     * The admin panel's identity read, on the path it still calls.
     *
     * <p>This is a legacy alias of {@code GET /admin/auth/me} and delegates to the same
     * service, so the two cannot answer differently. It lives here rather than as a second
     * class-level path on {@link AdminAuthController}, which would collide with the five
     * {@code /auth} operations this controller already maps.
     *
     * <p>It is admin-tier despite sitting on the app API: {@code SecurityConfig} matches
     * {@code GET /auth/me} with {@code hasRole("ADMIN")} the way {@code /admin/**} covers its
     * twin. Deleted together with that matcher once the panel reports it has migrated.
     *
     * @param principal the caller's session, supplied by the security chain
     * @return the signed-in administrator's identity
     */
    @GetMapping("/me")
    public AuthMeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return identityService.me(principal);
    }

    @PostMapping("/validate")
    public BasicResponse validate(@RequestHeader("Authorization") String authHeader, @Valid @RequestBody ValidateCredentialsRequest request) {
        return identityService.validate(authHeader, request);
    }
}

package com.pse.auth.service;

import org.springframework.http.HttpStatus;
import com.pse.shared.error.ApiException;
import com.pse.auth.dto.request.ValidateCredentialsRequest;
import com.pse.auth.dto.response.AuthMeResponse;
import com.pse.auth.dto.response.AuthUserResponse;
import com.pse.auth.exception.InvalidAuthTokenException;
import com.pse.security.AuthenticatedUser;
import com.pse.security.TokenAuthenticationService;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.enums.UserRole;
import com.pse.shared.util.KitEmail;
import com.pse.user.model.Student;
import org.springframework.stereotype.Service;

/**
 * Answers "who is holding this token", for the routes that ask directly and for the two
 * services that read the {@code Authorization} header by hand.
 *
 * <p>It issues nothing and revokes nothing, which is why it is not part of {@link
 * SessionIssuer} or {@link SessionRevoker}: it needs neither the token table nor a clock.
 */
@Service
public class IdentityService {

    private final TokenAuthenticationService tokenAuthenticationService;
    private final AdminSuperuserPolicy superuserPolicy;

    public IdentityService(
            TokenAuthenticationService tokenAuthenticationService,
            AdminSuperuserPolicy superuserPolicy
    ) {
        this.tokenAuthenticationService = tokenAuthenticationService;
        this.superuserPolicy = superuserPolicy;
    }

    public AuthMeResponse me(AuthenticatedUser principal) {
        Student student = principal.student();
        return new AuthMeResponse(
                "Authenticated",
                true,
                new AuthUserResponse(
                        student.getId(),
                        student.getUsername(),
                        student.getKitEmail(),
                        principal.isAdmin() ? UserRole.ADMIN : UserRole.STUDENT,
                        student.getStatus(),
                        principal.isAdmin()
                                && superuserPolicy.isSuperuser(student.getKitEmail())
                )
        );
    }

    /**
     * F-5's shape, and the last instance of it worth changing.
     *
     * <p>All three failures here answered {@code 200 {"success": false}}, while the failure
     * three lines away -- a header that is not a bearer token at all -- has always thrown
     * {@code InvalidAuthTokenException} and answered <b>401</b>. So "no token" was 401 and
     * "wrong token" was 200: the harder failure got the softer answer, from one method.
     *
     * <p>The two that remain are told apart rather than merged, because they are different
     * facts about the request. A missing {@code email} is a malformed body -- 400, with the
     * same {@code "Invalid request"} the validation handler already sends. A well-formed
     * address that is not this token's owner is an authorization failure -- 401, with the
     * same {@code "Not logged in"} the two existing 401 handlers send. Reusing both strings
     * on purpose: a client matching on messages already handles them.
     */
    public BasicResponse validate(String authHeader, ValidateCredentialsRequest request) {

        Student student = verifyUser(authHeader);
        if (student == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        if (request.email() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid request");
        }
        if (!student.getKitEmail().equals(KitEmail.normalize(request.email()))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        return new BasicResponse("Correct auth-Token for email: " + request.email(), true);
    }

    /**
     * The account behind a raw {@code Authorization} header, for the three routes the
     * security chain leaves open and which therefore read the header themselves.
     *
     * @return {@code null} when the header is well-formed but the token is not valid
     * @throws InvalidAuthTokenException when there is no bearer token to read at all, which
     *         {@code GlobalExceptionHandler} answers as a 401 rather than a 500
     */
    public Student verifyUser(String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidAuthTokenException("Couldn't find Auth-Token");
        }

        return tokenAuthenticationService
                .authenticate(authHeader.substring("Bearer ".length()).trim())
                .map(AuthenticatedUser::student)
                .orElse(null);
    }
}

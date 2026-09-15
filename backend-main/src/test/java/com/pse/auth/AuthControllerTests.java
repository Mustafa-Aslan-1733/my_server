package com.pse.auth;

import com.pse.auth.controller.AuthController;
import com.pse.auth.dto.request.LoginRequest;
import com.pse.auth.dto.request.OTPRequest;
import com.pse.auth.dto.request.ValidateCredentialsRequest;
import com.pse.shared.dto.BasicResponse;
import com.pse.auth.dto.response.LoginResponse;
import com.pse.auth.service.IdentityService;
import com.pse.auth.service.LoginCodeService;
import com.pse.auth.service.SessionIssuer;
import com.pse.auth.service.SessionRevoker;
import com.pse.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

    @Mock
    private LoginCodeService loginCodeService;

    @Mock
    private SessionIssuer sessionIssuer;

    @Mock
    private SessionRevoker sessionRevoker;

    @Mock
    private IdentityService identityService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private AuthenticatedUser principal;

    @InjectMocks
    private AuthController authController;


    @Test
    void requestLoginReturnsServiceResponse() {

        OTPRequest request = mock(OTPRequest.class);

        BasicResponse expected =
                new BasicResponse(
                        "Login code sent",
                        true
                );

        when(loginCodeService.requestLogin(
                request,
                httpRequest
        )).thenReturn(expected);


        BasicResponse response = authController.requestLogin(
                        request,
                        httpRequest
                );


        assertThat(response).isSameAs(expected);

        verify(loginCodeService)
                .requestLogin(
                        request,
                        httpRequest
                );
    }


    @Test
    void loginReturnsServiceResponse() {

        LoginRequest request = mock(LoginRequest.class);

        LoginResponse expected = mock(LoginResponse.class);

        when(sessionIssuer.login(
                request,
                httpRequest
        )).thenReturn(expected);


        LoginResponse response = authController.login(
                        request,
                        httpRequest
                );


        assertThat(response).isSameAs(expected);

        verify(sessionIssuer).login(
                        request,
                        httpRequest
                );
    }


    @Test
    void logoutReturnsServiceResponse() {

        BasicResponse expected =
                new BasicResponse(
                        "Logged out successfully",
                        true
                );

        when(sessionRevoker.logout(principal))
                .thenReturn(expected);


        BasicResponse response =
                authController.logout(principal);


        assertThat(response).isSameAs(expected);

        verify(sessionRevoker).logout(principal);
    }


    @Test
    void logoutAllReturnsServiceResponse() {

        BasicResponse expected = new BasicResponse("Logged out from all devices", true);

        when(sessionRevoker.logoutAll(principal))
                .thenReturn(expected);


        BasicResponse response = authController.logoutAll(principal);


        assertThat(response).isSameAs(expected);

        verify(sessionRevoker).logoutAll(principal);
    }


    @Test
    void validateReturnsServiceResponse() {

        String authHeader = "Bearer secret-token";

        ValidateCredentialsRequest request = mock(ValidateCredentialsRequest.class);

        BasicResponse expected = new BasicResponse("Correct", true);

        when(identityService.validate(
                authHeader,
                request
        )).thenReturn(expected);


        BasicResponse response = authController.validate(
                        authHeader,
                        request
                );


        assertThat(response).isSameAs(expected);

        verify(identityService).validate(
                        authHeader,
                        request
                );
    }
}
package com.pse.security;

import tools.jackson.databind.ObjectMapper;
import com.pse.shared.dto.BasicResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Provides BearerTokenAuthenticationFilter.
 */
@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final TokenAuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    /**
     * Creates BearerTokenAuthenticationFilter.
     *
     * @param authenticationService the authenticationService
     * @param objectMapper the objectMapper
     */
    public BearerTokenAuthenticationFilter(
            TokenAuthenticationService authenticationService,
            ObjectMapper objectMapper
    ) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Filters the request.
     *
     * @param request the request
     * @param response the response
     * @param filterChain the filterChain
     * @throws ServletException if the filter chain fails
     * @throws IOException if the response cannot be written
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!header.startsWith(PREFIX) || header.length() == PREFIX.length()) {
            unauthorized(response);
            return;
        }

        AuthenticatedUser principal = authenticationService
                .authenticate(header.substring(PREFIX.length()).trim())
                .orElse(null);
        if (principal == null) {
            unauthorized(response);
            return;
        }

        List<SimpleGrantedAuthority> authorities = principal.isAdmin()
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities)
        );
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new BasicResponse("Not logged in", false));
    }
}

package com.pse.config;

import com.pse.support.TestDeliveryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pse.support.ApiIntegrationTest;

/**
 * Covers the two ways the Admin Web was locked out of the deployed backend: an empty
 * origin allow-list, which answered every browser call with 403, and CORS preflight
 * being evaluated by the role matchers, which answered admin routes with 401.
 */
@ApiIntegrationTest
class CorsConfigurationTests {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired MockMvc mockMvc;

    @Test
    @WithAnonymousUser
    void preflightForLoginIsAllowedForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/auth/request-login")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    /**
     * A preflight carries no Authorization header, so it must never reach the
     * {@code hasRole("ADMIN")} matcher for {@code /users/**}.
     */
    @Test
    @WithAnonymousUser
    void preflightForAdminRouteIsNotRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(options("/users")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    /**
     * The same for the prefixed admin API, where a single {@code /admin/**} rule stands in
     * front of every panel route.
     */
    @Test
    @WithAnonymousUser
    void preflightForAdminPrefixedRouteIsNotRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(options("/admin/users")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    @WithAnonymousUser
    void preflightForAdminLoginIsAllowedForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/admin/auth/login")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    @WithAnonymousUser
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/auth/request-login")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void blankConfigurationFallsBackToLocalDevelopmentOrigins() {
        assertThat(allowedOrigins("   ,  ,"))
                .isEqualTo(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
    }

    @Test
    void configuredOriginsAreTrimmed() {
        assertThat(allowedOrigins(" https://admin.example , http://localhost:5173 "))
                .isEqualTo(List.of("https://admin.example", "http://localhost:5173"));
    }

    @Test
    void wildcardOriginIsRejected() {
        assertThatThrownBy(() -> new CorsConfig("https://*.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> allowedOrigins(String configuredValue) {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) new CorsConfig(configuredValue).corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfigurations().get("/**");
        return configuration.getAllowedOrigins();
    }
}

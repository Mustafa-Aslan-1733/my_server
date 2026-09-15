package com.pse.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long the things a login hands out stay valid.
 *
 * <p>The lifetime belongs to the API that issued the session, not to the account: a session
 * from {@code POST /admin/auth/login} expires on the admin schedule, one from
 * {@code POST /auth/login} lasts a year for everyone, administrators included.
 *
 * <p>The {@code AUTH_STUDENT_SESSION_TTL} environment variable is the former name of
 * {@code appSessionTtl} and is still honoured -- that fallback lives in
 * {@code application.properties}, where {@code DockerConfigurationTests} pins it, and not
 * here. The constructor this replaces also carried a second, property-level fallback to
 * {@code app.auth.student-session-ttl}; it could never fire, because
 * {@code application.properties} always defines {@code app.auth.app-session-ttl}.
 *
 * <p>The rate limits are deliberately not here. They are one policy read by two classes and
 * have their own record -- see {@link RateLimitProperties}.
 *
 * @param adminSessionTtl the adminSessionTtl
 * @param appSessionTtl the appSessionTtl
 * @param otpTtl the otpTtl
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @DefaultValue("P1D") Duration adminSessionTtl,
        @DefaultValue("P365D") Duration appSessionTtl,
        @DefaultValue("PT5M") Duration otpTtl
) {
}

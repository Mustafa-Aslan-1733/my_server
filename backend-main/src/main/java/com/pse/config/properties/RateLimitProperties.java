package com.pse.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The login rate-limiting policy: one window, one key, and the four budgets spent inside it.
 *
 * <p>Its own record because the policy was split across two classes and neither held all of
 * it. {@code RateLimitService} owned the window and the HMAC key while {@code AuthService}
 * owned the four budgets as loose {@code int} constructor parameters -- four adjacent,
 * silently swappable arguments, positioned twelfth to fifteenth in an eighteen-parameter
 * constructor. Whether {@code requestEmail} or {@code loginIp} arrived in the right slot was
 * a matter of counting commas.
 *
 * @param window       how long one counting window lasts before the counts reset
 * @param hmacKey      the key subjects are hashed with; {@code RateLimitService} refuses to
 *                     start on anything shorter than 32 characters
 *
 * @param requestEmail codes that may be requested for one address per window
 * @param requestIp    codes that may be requested from one address per window
 * @param loginEmail   failed logins tolerated for one account per window
 * @param loginIp      failed logins tolerated from one address per window
 */
@ConfigurationProperties(prefix = "app.auth.rate-limit")
public record RateLimitProperties(
        @DefaultValue("PT15M") Duration window,
        @DefaultValue("") String hmacKey,
        @DefaultValue("3") int requestEmail,
        @DefaultValue("20") int requestIp,
        @DefaultValue("10") int loginEmail,
        @DefaultValue("30") int loginIp
) {
}

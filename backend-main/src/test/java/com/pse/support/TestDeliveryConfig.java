package com.pse.support;

import com.pse.auth.service.LoginCodeDelivery;
import com.pse.auth.service.LoginSuccessDelivery;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Substitutes mail delivery, so a test can read the login code the user would have been sent.
 *
 * <p><b>Both beans are {@code @Primary}, and that is load-bearing.</b> {@code MailService} is
 * {@code @Profile("!test")}, so under the {@code test} profile these are the only candidates and
 * nothing has to win. The end-to-end layer runs against PostgreSQL with no profile active, where
 * {@code MailService} is a bean too -- and two {@code LoginCodeDelivery} beans is a context that
 * will not start. Marking these primary makes this configuration self-sufficient rather than
 * dependent on which profile happens to be on, which is the same reason
 * {@code TestGitLabConfig} marks its recording client primary.
 */
@TestConfiguration
public class TestDeliveryConfig {

    @Bean
    @Primary
    CapturingLoginCodeDelivery capturingLoginCodeDelivery() {
        return new CapturingLoginCodeDelivery();
    }

    @Bean
    @Primary
    LoginSuccessDelivery noOpLoginSuccessDelivery() {
        return (email, ipAddress, userAgent) -> {
        };
    }

    public static final class CapturingLoginCodeDelivery implements LoginCodeDelivery {

        private final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void send(String email, String code) {
            codes.put(email, code);
        }

        public String codeFor(String email) {
            return codes.get(email);
        }

        public void clear() {
            codes.clear();
        }
    }
}

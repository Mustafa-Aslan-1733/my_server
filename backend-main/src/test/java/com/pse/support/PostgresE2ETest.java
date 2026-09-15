package com.pse.support;

import com.pse.DemoApplication;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The end-to-end layer: a real servlet container on a real port, against a real PostgreSQL.
 *
 * <p><b>What this buys over the API layer.</b> Everything else in the suite calls handlers
 * through MockMvc, which is Spring's dispatcher without a server: no socket, no HTTP parsing, no
 * real connection handling. Here a request is written to a port and read back off one, so the
 * container, the filter chain, content negotiation and the JSON encoder are all doing their real
 * jobs -- and the production Flyway schema is underneath. What this layer asserts is that a
 * whole journey works; field-level checking stays in the API and unit layers, which already have
 * it.
 *
 * <p><b>Why this is a different context from {@link PostgresIntegrationTest}, deliberately.</b>
 * {@code webEnvironment = RANDOM_PORT} alone would fork one, and these journeys additionally need
 * {@link TestDeliveryConfig} -- the login code is normally emailed, and
 * {@code CapturingLoginCodeDelivery} is the only way a test can read it. Two contexts against one
 * database would reset the schema under each other, so the CI job runs this as a **separate Maven
 * invocation**: a separate JVM cannot interleave, and the "exactly one context" guard stays true
 * for each invocation on its own rather than being weakened to allow two.
 *
 * <p>Skipped unless {@code POSTGRES_SMOKE_JDBC_URL} is set, like the rest of the PostgreSQL work,
 * so {@code ./mvnw verify} on a developer machine is unchanged and still needs no Docker.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(classes = DemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = PostgresTestDatabase.class)
// A journey signs in more than three times for one address -- the session-lifetime one signs in
// twice as the same account, having created it with a third. The limiter is real behaviour with
// its own tests in AuthApiIntegrationTests; here it would only cap how many steps a story may
// have, so it is lifted rather than worked around.
@TestPropertySource(properties = {
        "app.auth.rate-limit.request-email=50",
        "app.auth.rate-limit.request-ip=200",
        "app.auth.rate-limit.login-email=50",
        "app.auth.rate-limit.login-ip=200"
})
@Import({TestDeliveryConfig.class, TestGitLabConfig.class, DatabaseReset.class})
@EnabledIfEnvironmentVariable(named = PostgresTestDatabase.URL_VARIABLE, matches = ".+")
public @interface PostgresE2ETest {
}

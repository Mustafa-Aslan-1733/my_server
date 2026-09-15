package com.pse.support;

import com.pse.DemoApplication;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The context configuration every PostgreSQL integration test must carry, in one place.
 *
 * <p><b>Why this is an annotation and not a comment.</b>
 * {@link PostgresTestDatabase#initialize} drops and recreates the {@code public} schema, so two
 * Spring contexts using it would wipe each other's data mid-run. The classes must therefore
 * carry <em>identical</em> configuration, so that Spring's context cache hands them one context
 * and the reset happens once. That requirement used to live in a javadoc paragraph asking each
 * new author to copy four annotations correctly; P-4 in {@code docs/test-findings.md} records
 * what happens when setup is duplicated instead of shared. Here the rule is the type system's
 * job rather than the reader's.
 *
 * <p>Spring merges meta-annotations into the same {@code MergedContextConfiguration}, so a
 * class annotated with this gets exactly the context it would have got from the four
 * annotations written out — the cache key is unchanged.
 *
 * <p><b>Deliberately no {@link TestDeliveryConfig} or {@link TestGitLabConfig}.</b> Either would
 * change the context key for every class at once, and nothing here needs them:
 * {@link AdminSessions} mints a usable admin bearer token by writing rows, so no test in this
 * layer drives the login flow or reaches a tracker. {@link PostgresE2ETest} does need both, which
 * is why it is a separate context and a separate CI invocation.
 *
 * <p>{@link DatabaseReset} is imported, which is the rule this paragraph describes being followed
 * rather than broken: it was added here, for all of them, at once.
 *
 * <p>The {@code POSTGRES_SMOKE_JDBC_URL} condition is part of the bundle on purpose. It is what
 * makes these tests select themselves into the {@code server:postgres-integration} job and skip
 * on a developer machine, which is what keeps {@code ./mvnw verify} Docker-free.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(classes = DemoApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestDatabase.class)
@Import(DatabaseReset.class)
@EnabledIfEnvironmentVariable(named = PostgresTestDatabase.URL_VARIABLE, matches = ".+")
public @interface PostgresIntegrationTest {
}

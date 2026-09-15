package com.pse.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The context configuration every MockMvc API test carries, in one place.
 *
 * <p>Eleven classes held these four lines identically, which is why they already shared a single
 * Spring context and why the suite is as quick as it is. Identical by coincidence is a fragile
 * thing to depend on, though: the twelfth class is the one that gets a line wrong, and the cost
 * is not a failure but a second application start nobody notices. Stated once, it cannot drift.
 *
 * <p>Companion to {@link PostgresIntegrationTest}, which does the same job on the PostgreSQL side
 * for a stronger reason -- there, a forked context resets the schema under the first one.
 *
 * <p>{@link DatabaseReset} rides along, so a test class autowires it rather than declaring
 * eighteen repositories to empty in {@code @BeforeEach}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestDeliveryConfig.class, TestGitLabConfig.class, DatabaseReset.class})
public @interface ApiIntegrationTest {
}

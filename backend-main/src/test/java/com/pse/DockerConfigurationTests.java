package com.pse;


import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;

class DockerConfigurationTests {

	@Test
	void applicationPropertiesProvideLocalDatasourceDefaults() throws IOException {
		MockEnvironment environment = environmentWithApplicationProperties();

		assertThat(environment.getProperty("spring.datasource.url"))
		        .isEqualTo("jdbc:postgresql://localhost:5432/postgres");
		assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("postgres");
		assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("");
	}

	@Test
	void applicationPropertiesAllowDockerComposeDatasourceOverrides() throws IOException {
		MockEnvironment environment = environmentWithApplicationProperties();
		environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/postgres");
		environment.setProperty("SPRING_DATASOURCE_USERNAME", "docker_user");
		environment.setProperty("SPRING_DATASOURCE_PASSWORD", "docker_password");

		assertThat(environment.getProperty("spring.datasource.url"))
		        .isEqualTo("jdbc:postgresql://postgres:5432/postgres");
		assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("docker_user");
		assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("docker_password");
	}

	/**
	 * The lifetime belongs to the API that issued the session: one day for a session from
	 * /admin/auth/login, a year for one from /auth/login whoever the account belongs to.
	 */
	@Test
	void sessionLifetimesDefaultToTheirApiSchedules() throws IOException {
		MockEnvironment environment = environmentWithApplicationProperties();

		assertThat(environment.getProperty("app.auth.admin-session-ttl")).isEqualTo("P1D");
		assertThat(environment.getProperty("app.auth.app-session-ttl")).isEqualTo("P365D");
	}

	@Test
	void appSessionLifetimeStillReadsTheFormerVariableName() throws IOException {
		// A deployed .env written before the rename still carries AUTH_STUDENT_SESSION_TTL.
		MockEnvironment environment = environmentWithApplicationProperties();
		environment.setProperty("AUTH_STUDENT_SESSION_TTL", "P30D");

		assertThat(environment.getProperty("app.auth.app-session-ttl")).isEqualTo("P30D");
	}

	@Test
	void appSessionLifetimePrefersTheCurrentVariableName() throws IOException {
		MockEnvironment environment = environmentWithApplicationProperties();
		environment.setProperty("AUTH_STUDENT_SESSION_TTL", "P30D");
		environment.setProperty("AUTH_APP_SESSION_TTL", "P365D");

		assertThat(environment.getProperty("app.auth.app-session-ttl")).isEqualTo("P365D");
	}

	private MockEnvironment environmentWithApplicationProperties() throws IOException {
		MockEnvironment environment = new MockEnvironment();
		environment.getPropertySources().addLast(new ResourcePropertySource(
				"applicationProperties",
				new ClassPathResource("application.properties")));
		return environment;
	}
}

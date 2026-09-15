package com.pse;

import com.pse.support.PostgresIntegrationTest;
import com.pse.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production migrations, run against a real PostgreSQL rather than the H2 translation
 * the rest of the suite uses.
 *
 * <p>{@link PostgresIntegrationTest} carries the context configuration, which is load-bearing
 * rather than decoration: {@link PostgresTestDatabase} resets the schema, so a second context
 * would wipe this one's data mid-run.
 */
@PostgresIntegrationTest
class PostgreSqlMigrationSmokeTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthReportsHealthyAfterProductionPostgresqlMigrations() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

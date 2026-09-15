package com.pse.health;

import com.pse.shared.dto.BasicResponse;
import com.pse.shared.error.ApiException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides HealthController.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final Flyway flyway;

    /**
     * Creates HealthController.
     *
     * @param jdbcTemplate the jdbcTemplate
     * @param flyway the flyway
     */
    public HealthController(JdbcTemplate jdbcTemplate, Flyway flyway) {
        this.jdbcTemplate = jdbcTemplate;
        this.flyway = flyway;
    }

    /**
     * Returns health.
     *
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @GetMapping("/health")
    public BasicResponse health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            verifyMigrations();
            return new BasicResponse("API healthy", true);
        } catch (DataAccessException | FlywayException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "API unhealthy");
        }
    }

    private void verifyMigrations() {
        MigrationInfoService info = flyway.info();
        for (MigrationInfo migration : info.all()) {
            if (migration.getState().isFailed()) {
                throw new FlywayException("Database migrations are failed or pending");
            }
        }
        if (info.pending().length > 0) {
            throw new FlywayException("Database migrations are failed or pending");
        }

        MigrationInfo current = info.current();
        if (current == null) {
            throw new FlywayException("No Flyway migration has been applied or baselined");
        }
    }
}

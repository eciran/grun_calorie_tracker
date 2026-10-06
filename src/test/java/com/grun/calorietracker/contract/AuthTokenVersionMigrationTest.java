package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenVersionMigrationTest {
    @Test void migrationPreservesExistingRowsAndInitializesLegacySessions() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            for (String table : new String[]{"users", "refresh_tokens", "admin_sessions"}) {
                jdbc.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY)");
                jdbc.update("INSERT INTO " + table + " (id) VALUES (1)");
            }
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V303__add_auth_token_version.sql"))
                    .execute(database);
            for (String table : new String[]{"users", "refresh_tokens", "admin_sessions"}) {
                assertThat(jdbc.queryForObject("SELECT auth_token_version FROM " + table + " WHERE id = 1", Long.class))
                        .isZero();
                jdbc.update("INSERT INTO " + table + " (id) VALUES (2)");
                assertThat(jdbc.queryForObject("SELECT auth_token_version FROM " + table + " WHERE id = 2", Long.class))
                        .isZero();
            }
        } finally {
            database.shutdown();
        }
    }
}

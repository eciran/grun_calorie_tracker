package com.grun.calorietracker.service.reminder;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.task.scheduling.enabled=false"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_MEAL_REMINDER_POSTGRES", matches = "true")
class MealReminderPostgresConcurrencyIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void databaseOwnsOccurrenceIdempotencyConstraint() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from pg_constraint
                where conname = 'uq_meal_reminder_occurrence_user_day_slot'
                """, Integer.class);
        assertEquals(1, count);
    }

    @Test
    void concurrentWorkersNeverClaimTheSameScheduleRow() throws Exception {
        List<Long> available = jdbcTemplate.queryForList(
                "select user_id from meal_reminder_schedules order by user_id limit 2", Long.class);
        Assumptions.assumeFalse(available.isEmpty(), "Requires at least one migrated user schedule");
        try (Connection first = dataSource.getConnection(); Connection second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            Long firstClaim = claimOne(first);
            Long secondClaim = claimOne(second);
            if (secondClaim != null) assertNotEquals(firstClaim, secondClaim);
            first.rollback();
            second.rollback();
        }
    }

    private Long claimOne(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
                select user_id from meal_reminder_schedules
                order by user_id for update skip locked limit 1
                """)) {
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : null;
            }
        }
    }
}

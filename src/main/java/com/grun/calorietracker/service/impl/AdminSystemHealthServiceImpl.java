package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminSystemHealthDto;
import com.grun.calorietracker.service.AdminSystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSystemHealthServiceImpl implements AdminSystemHealthService {

    private final DataSource dataSource;
    private final Environment environment;

    @Override
    public AdminSystemHealthDto getHealth() {
        DatabaseCheck databaseCheck = checkDatabase();
        String status = "UP".equals(databaseCheck.status()) ? "UP" : "DEGRADED";

        return new AdminSystemHealthDto(
                status,
                environment.getProperty("spring.application.name", "grun-calorie-tracker"),
                environment.getProperty("info.app.version", "unknown"),
                activeProfiles(),
                databaseCheck.status(),
                databaseCheck.latencyMs(),
                LocalDateTime.now()
        );
    }

    private DatabaseCheck checkDatabase() {
        long startedAt = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return new DatabaseCheck(valid ? "UP" : "DOWN", elapsedMs(startedAt));
        } catch (Exception ex) {
            return new DatabaseCheck("DOWN", elapsedMs(startedAt));
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private List<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return List.of("default");
        }
        return Arrays.asList(profiles);
    }

    private record DatabaseCheck(String status, long latencyMs) {
    }
}

package com.grun.calorietracker.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("foodProductCacheUserScopeResolver")
@RequiredArgsConstructor
public class FoodProductCacheUserScopeResolver {

    private final JdbcTemplate jdbcTemplate;

    public String resolve(String email) {
        if (email == null || email.isBlank()) {
            return "anonymous";
        }
        return jdbcTemplate.query(
                "SELECT id FROM users WHERE LOWER(email) = LOWER(?)",
                resultSet -> resultSet.next() ? "user-" + resultSet.getLong(1) : "unknown",
                email
        );
    }
}

package com.grun.calorietracker.repository;

import com.grun.calorietracker.service.support.UserAnalyticsCacheIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserAnalyticsCacheRevisionRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<UserAnalyticsCacheIdentity> findIdentityByEmail(String email) {
        return jdbcTemplate.query("""
                SELECT u.id AS user_id,
                       COALESCE(r.revision, 0) AS revision,
                       u.time_zone AS time_zone
                  FROM users u
                  LEFT JOIN user_analytics_cache_revisions r ON r.user_id = u.id
                 WHERE LOWER(u.email) = LOWER(?)
                """, (resultSet, rowNumber) -> new UserAnalyticsCacheIdentity(
                resultSet.getLong("user_id"),
                resultSet.getLong("revision"),
                resultSet.getString("time_zone")
        ), email).stream().findFirst();
    }

    public void incrementRevision(Long userId) {
        jdbcTemplate.update("""
                INSERT INTO user_analytics_cache_revisions (user_id, revision, updated_at)
                VALUES (?, 1, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id)
                DO UPDATE SET revision = user_analytics_cache_revisions.revision + 1,
                              updated_at = CURRENT_TIMESTAMP
                """, userId);
    }
}

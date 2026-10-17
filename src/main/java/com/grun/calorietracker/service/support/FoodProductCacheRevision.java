package com.grun.calorietracker.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.ConnectionCallback;

import java.sql.Savepoint;
import com.grun.calorietracker.config.FoodProductCacheProperties;
import org.springframework.beans.factory.annotation.Value;

@Component("foodProductCacheRevision")
@RequiredArgsConstructor
public class FoodProductCacheRevision {
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;
    private final FoodProductCacheProperties properties;
    @Value("${spring.cache.type:simple}") private String globalType = "simple";
    private volatile Boolean available;

    public record Key(Long revision, Object dimension) { }

    public Key key(Object dimension) {
        return new Key(current(), dimension);
    }

    public Long current() {
        if (!properties.isEnabled() || globalType.equalsIgnoreCase("none")) return null;
        try {
            if (Boolean.FALSE.equals(available)) return null;
            Long stamp = jdbc.execute((ConnectionCallback<Long>) connection -> {
                if (available == null) {
                    boolean postgres = connection.getMetaData().getDatabaseProductName().contains("PostgreSQL");
                    if (!postgres) { available = false; return null; }
                    try (var probe = connection.prepareStatement("select to_regprocedure('public.grun_product_cache_stamp()') is not null");
                         var result = probe.executeQuery()) {
                        result.next(); available = result.getBoolean(1);
                    }
                    if (!available) return null;
                }
                int isolation = connection.getTransactionIsolation();
                if (isolation != java.sql.Connection.TRANSACTION_READ_COMMITTED) return null;
                Savepoint savepoint = connection.getAutoCommit() ? null : connection.setSavepoint();
                try (var statement = connection.prepareStatement("select public.grun_product_cache_stamp()")) {
                    statement.setQueryTimeout(1);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        return (Long) result.getObject(1);
                    }
                } catch (java.sql.SQLException failure) {
                    if (savepoint != null) connection.rollback(savepoint);
                    throw failure;
                } finally {
                    if (savepoint != null) connection.releaseSavepoint(savepoint);
                }
            });
            if (stamp == null) meters.counter("grun.product.cache.bypass", "reason", "writer").increment();
            return stamp;
        } catch (DataAccessException unavailable) {
            meters.counter("grun.product.cache.bypass", "reason", "revision_unavailable").increment();
            return null;
        }
    }
}

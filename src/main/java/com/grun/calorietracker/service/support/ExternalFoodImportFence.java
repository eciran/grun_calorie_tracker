package com.grun.calorietracker.service.support;

import com.grun.calorietracker.exception.CatalogWriteInProgressException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;

@Component
public class ExternalFoodImportFence {
    private final JdbcTemplate jdbc;

    public ExternalFoodImportFence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void acquire() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("External food import requires a writable transaction");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String database = connection.getMetaData().getDatabaseProductName();
            // H2 is only a local contract fixture; distributed concurrency is proved on PostgreSQL.
            if ("H2".equalsIgnoreCase(database)) return null;
            if (!"PostgreSQL".equalsIgnoreCase(database)
                    || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED
                    || connection.getAutoCommit()) {
                throw new IllegalStateException("External import identity fencing requires PostgreSQL READ COMMITTED");
            }
            // One transaction-scoped fence avoids opposite batch-order and nested lookup deadlocks.
            // Provider calls happen before this fence. Only the DB import/recheck phase is serialized.
            try (var statement = connection.prepareStatement("select pg_try_advisory_xact_lock(17491, 310)")) {
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) {
                        throw new CatalogWriteInProgressException();
                    }
                    return null;
                }
            }
        });
    }
}

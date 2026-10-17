package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalFoodImportFenceTest {
    @AfterEach void clear() { TransactionSynchronizationManager.clear(); }

    @Test void noTransactionOrReadOnlyCannotImport() {
        var jdbc = mock(JdbcTemplate.class); var fence = new ExternalFoodImportFence(jdbc);
        assertThrows(IllegalStateException.class, fence::acquire);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(IllegalStateException.class, fence::acquire);
        verifyNoInteractions(jdbc);
    }

    @Test void strongerIsolationFailsClosedInsteadOfUsingAnOldSnapshot() throws Exception {
        var connection = mock(Connection.class); var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        var jdbc = new JdbcTemplate() {
            @Override public <T> T execute(ConnectionCallback<T> action) {
                try { return action.doInConnection(connection); }
                catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
            }
        };
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(IllegalStateException.class, new ExternalFoodImportFence(jdbc)::acquire);
        verify(connection, never()).prepareStatement(anyString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SQLite", "MySQL"})
    void unprovedDatabaseDriverFailsBeforeLockSql(String database) throws Exception {
        var connection = mock(Connection.class); var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(database);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(IllegalStateException.class, new ExternalFoodImportFence(callbackJdbc(connection))::acquire);
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test void autoCommitCannotReleaseFenceBeforeTheWrite() throws Exception {
        var connection = mock(Connection.class); var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(connection.getAutoCommit()).thenReturn(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(IllegalStateException.class, new ExternalFoodImportFence(callbackJdbc(connection))::acquire);
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test void writableReadCommittedUsesBoundedTransactionLockAndClosesResources() throws Exception {
        var connection = mock(Connection.class); var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        var statement = mock(PreparedStatement.class); var result = mock(ResultSet.class);
        when(connection.prepareStatement("select pg_advisory_xact_lock(17491, 310)")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        new ExternalFoodImportFence(callbackJdbc(connection)).acquire();
        var order = inOrder(statement, result);
        order.verify(statement).setQueryTimeout(5); order.verify(statement).executeQuery();
        order.verify(result).close(); order.verify(statement).close();
        verify(connection, never()).commit(); verify(connection, never()).close();
    }

    private JdbcTemplate callbackJdbc(Connection connection) {
        return new JdbcTemplate() {
            @Override public <T> T execute(ConnectionCallback<T> action) {
                try { return action.doInConnection(connection); }
                catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
            }
        };
    }
}

package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReliableRevenueCatWebhookServiceTest {
    private final RevenueCatWebhookServiceImpl processor = mock(RevenueCatWebhookServiceImpl.class);
    private final SubscriptionProviderEventRepository events = mock(SubscriptionProviderEventRepository.class);
    private JdbcTemplate jdbc;
    private ReliableRevenueCatWebhookService service;

    @BeforeEach
    void setup() {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE audit (id BIGINT PRIMARY KEY, status VARCHAR(20), attempts INT, retry_at TIMESTAMP, error VARCHAR(1000))");
        jdbc.execute("CREATE TABLE grants (event_id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO audit VALUES (1, 'FAILED', 0, ?, NULL)", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        service = new ReliableRevenueCatWebhookService(processor, events, new DataSourceTransactionManager(datasource));
        when(events.findLockedById(1L)).thenAnswer(invocation -> Optional.of(jdbc.queryForObject(
                "SELECT * FROM audit WHERE id=1 FOR UPDATE", (rs, n) -> {
                    var event = new SubscriptionProviderEventEntity();
                    event.setId(1L);
                    event.setProviderEventId("event-1");
                    event.setStatus(SubscriptionProviderEventStatus.valueOf(rs.getString("status")));
                    event.setProcessingAttempts(rs.getInt("attempts"));
                    var retry = rs.getTimestamp("retry_at");
                    event.setNextAttemptAt(retry == null ? null : retry.toLocalDateTime());
                    return event;
                })));
        when(events.saveAndFlush(any())).thenAnswer(invocation -> {
            SubscriptionProviderEventEntity event = invocation.getArgument(0);
            jdbc.update("UPDATE audit SET status=?, attempts=?, retry_at=?, error=? WHERE id=1",
                    event.getStatus().name(), event.getProcessingAttempts(), event.getNextAttemptAt(), event.getProcessingError());
            return event;
        });
    }

    @Test
    void failedProcessingRollsBackCreditsButCommitsFailureEvenIfAlertFails() {
        when(processor.retryStoredEvent(1L)).thenAnswer(invocation -> {
            jdbc.update("INSERT INTO grants VALUES (1)");
            return response("FAILED", "database unavailable");
        });
        doThrow(new IllegalStateException("notification failed"))
                .when(processor).notifyAdminsAboutFailedProviderEvent(any());

        assertThat(service.retryStoredEvent(1L).getStatus()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grants", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT attempts FROM audit", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT error FROM audit", String.class)).isEqualTo("database unavailable");
        assertThat(jdbc.queryForObject("SELECT retry_at FROM audit", java.sql.Timestamp.class)).isNotNull();
        verify(processor, never()).notifyAdminsAboutFailedProviderEvent(any());
    }

    @Test
    void successfulRetryAndDuplicateDeliveryDoNotAllocateTwice() {
        when(processor.retryStoredEvent(1L)).thenAnswer(invocation -> {
            jdbc.update("INSERT INTO grants VALUES (1)");
            jdbc.update("UPDATE audit SET status='PROCESSED' WHERE id=1");
            return response("PROCESSED", "done");
        });
        assertThat(service.retryStoredEvent(1L).getStatus()).isEqualTo("PROCESSED");
        assertThat(service.retryStoredEvent(1L).getStatus()).isEqualTo("PROCESSED");
        verify(processor, times(1)).retryStoredEvent(1L);
        verify(processor, times(1)).resolveAdminAlertsForProviderEvent(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grants", Integer.class)).isEqualTo(1);
    }

    @Test
    void repeatedWebhookHonorsBackoffAndDoesNotSendRepeatedAlerts() {
        var payload = JsonNodeFactory.instance.objectNode();
        when(processor.captureWebhook("auth", payload)).thenReturn(1L);
        when(processor.retryStoredEvent(1L)).thenReturn(response("FAILED", "temporary error"));
        service.processWebhook("auth", payload);
        service.processWebhook("auth", payload);
        verify(processor, times(1)).retryStoredEvent(1L);
        verify(processor, never()).notifyAdminsAboutFailedProviderEvent(any());
        assertThat(jdbc.queryForObject("SELECT attempts FROM audit", Integer.class)).isEqualTo(1);
    }

    @Test
    void optimisticLockConflictIsRetriedImmediatelyBeforeEnteringBackoff() {
        when(processor.retryStoredEvent(1L))
                .thenThrow(new ObjectOptimisticLockingFailureException("subscription", 1L))
                .thenAnswer(invocation -> {
                    jdbc.update("UPDATE audit SET status='PROCESSED' WHERE id=1");
                    return response("PROCESSED", "done");
                });

        assertThat(service.retryStoredEvent(1L).getStatus()).isEqualTo("PROCESSED");

        verify(processor, times(2)).retryStoredEvent(1L);
        verify(processor).resolveAdminAlertsForProviderEvent(1L);
        verify(processor, never()).notifyAdminsAboutFailedProviderEvent(any());
        assertThat(jdbc.queryForObject("SELECT attempts FROM audit", Integer.class)).isZero();
    }

    @Test
    void ownershipConflictStopsAutomaticRetriesWithoutGrantingCredits() {
        when(processor.retryStoredEvent(1L)).thenReturn(response("FAILED", "SUBSCRIPTION_OWNERSHIP_CONFLICT"));
        service.retryStoredEvent(1L);
        assertThat(jdbc.queryForObject("SELECT retry_at FROM audit", java.sql.Timestamp.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grants", Integer.class)).isZero();
    }

    @Test
    void thrownProcessingErrorDoesNotLoseTheDurableEvent() {
        when(processor.retryStoredEvent(1L)).thenAnswer(invocation -> {
            jdbc.update("INSERT INTO grants VALUES (1)");
            throw new IllegalStateException("database write failed");
        });
        service.retryStoredEvent(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grants", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT error FROM audit", String.class))
                .contains("database write failed");
        assertThat(jdbc.queryForObject("SELECT attempts FROM audit", Integer.class)).isEqualTo(1);
    }

    @Test
    void retryLimitStopsAutomaticWorkButAllowsExplicitOperatorRetry() {
        jdbc.update("UPDATE audit SET attempts=7 WHERE id=1");
        var payload = JsonNodeFactory.instance.objectNode();
        when(processor.captureWebhook("auth", payload)).thenReturn(1L);
        when(processor.retryStoredEvent(1L)).thenReturn(response("FAILED", "temporary error"));
        service.processWebhook("auth", payload);
        service.processWebhook("auth", payload);
        verify(processor, times(1)).retryStoredEvent(1L);
        assertThat(jdbc.queryForObject("SELECT retry_at FROM audit", java.sql.Timestamp.class)).isNull();
        service.retryStoredEvent(1L);
        verify(processor, times(2)).retryStoredEvent(1L);
    }

    private RevenueCatWebhookResponseDto response(String status, String message) {
        return new RevenueCatWebhookResponseDto(true, false, "event-1", status, message);
    }
}

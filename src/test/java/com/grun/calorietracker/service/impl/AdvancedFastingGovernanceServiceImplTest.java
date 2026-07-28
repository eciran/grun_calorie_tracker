package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.AdvancedFastingOperationsConfigEntity;
import com.grun.calorietracker.enums.FastingReminderDeliveryStatus;
import com.grun.calorietracker.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedFastingGovernanceServiceImplTest {
    @Mock AdvancedFastingOperationsConfigRepository configs;
    @Mock FastingReminderDeliveryRepository deliveries;
    SimpleMeterRegistry meters;
    AdvancedFastingGovernanceServiceImpl service;

    @BeforeEach void setUp() {
        meters = new SimpleMeterRegistry();
        service = new AdvancedFastingGovernanceServiceImpl(configs, deliveries, meters);
    }

    @Test void exposesSafeDefaultsAndOperationalMetricsWithoutDeclarations() {
        when(configs.findById(1L)).thenReturn(Optional.empty());
        when(deliveries.countByStatus(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == FastingReminderDeliveryStatus.FAILED ? 4L : 0L);
        meters.counter("grun.fasting.safety.blocked").increment(2);
        meters.counter("grun.fasting.scheduler.failures").increment();
        meters.counter("grun.fasting.push.failures").increment(3);

        AdvancedFastingGovernanceDto result = service.getGovernance();

        assertThat(result.activeSafetyPolicyVersion()).isEqualTo("FASTING_SAFETY_V1");
        assertThat(result.maximumContinuousFastingHours()).isEqualTo(24);
        assertThat(result.preStartMinutes()).isEqualTo(30);
        assertThat(result.failedDeliveries()).isEqualTo(4);
        assertThat(result.blockedUnsafeRequests()).isEqualTo(2);
        assertThat(result.schedulerFailures()).isEqualTo(1);
        assertThat(result.pushFailures()).isEqualTo(3);
    }

    @Test void updatesOnlyBoundedOperationalFields() {
        AdvancedFastingOperationsConfigEntity entity = new AdvancedFastingOperationsConfigEntity();
        entity.setId(1L);
        entity.setReminderEnabled(true);
        entity.setPreStartMinutes(30);
        entity.setNearingCompletionMinutes(30);
        entity.setMissedPlanMinutes(60);
        entity.setMaxRetryAttempts(3);
        entity.setUpdatedAt(LocalDateTime.of(2026, 7, 28, 12, 0));
        when(configs.findById(1L)).thenReturn(Optional.of(entity));
        when(configs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdvancedFastingGovernanceDto result = service.updateOperations(
                new AdvancedFastingOperationsConfigRequestDto(false, 20, 25, 90, 4));

        assertThat(result.reminderEnabled()).isFalse();
        assertThat(result.preStartMinutes()).isEqualTo(20);
        assertThat(result.nearingCompletionMinutes()).isEqualTo(25);
        assertThat(result.missedPlanMinutes()).isEqualTo(90);
        assertThat(result.maxRetryAttempts()).isEqualTo(4);
        assertThat(result.maximumContinuousFastingHours()).isEqualTo(24);
        verify(configs).save(entity);
    }
}
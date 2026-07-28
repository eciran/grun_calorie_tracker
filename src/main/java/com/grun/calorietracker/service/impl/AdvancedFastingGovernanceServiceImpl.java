package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.AdvancedFastingOperationsConfigEntity;
import com.grun.calorietracker.enums.FastingReminderDeliveryStatus;
import com.grun.calorietracker.repository.AdvancedFastingOperationsConfigRepository;
import com.grun.calorietracker.repository.FastingReminderDeliveryRepository;
import com.grun.calorietracker.service.AdvancedFastingGovernanceService;
import com.grun.calorietracker.service.support.FastingSafetyPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdvancedFastingGovernanceServiceImpl implements AdvancedFastingGovernanceService {
    public static final long GLOBAL_CONFIG_ID = 1L;
    private final AdvancedFastingOperationsConfigRepository configRepository;
    private final FastingReminderDeliveryRepository deliveryRepository;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public AdvancedFastingGovernanceDto getGovernance() {
        return toDto(currentOperations());
    }

    @Override
    @Transactional
    public AdvancedFastingGovernanceDto updateOperations(AdvancedFastingOperationsConfigRequestDto request) {
        AdvancedFastingOperationsConfigEntity config = configRepository.findById(GLOBAL_CONFIG_ID)
                .orElseGet(this::defaults);
        config.setReminderEnabled(request.reminderEnabled());
        config.setPreStartMinutes(request.preStartMinutes());
        config.setNearingCompletionMinutes(request.nearingCompletionMinutes());
        config.setMissedPlanMinutes(request.missedPlanMinutes());
        config.setMaxRetryAttempts(request.maxRetryAttempts());
        return toDto(configRepository.save(config));
    }

    @Override
    @Transactional(readOnly = true)
    public AdvancedFastingOperationsConfigEntity currentOperations() {
        return configRepository.findById(GLOBAL_CONFIG_ID).orElseGet(this::defaults);
    }

    private AdvancedFastingOperationsConfigEntity defaults() {
        AdvancedFastingOperationsConfigEntity config = new AdvancedFastingOperationsConfigEntity();
        config.setId(GLOBAL_CONFIG_ID);
        config.setReminderEnabled(true);
        config.setPreStartMinutes(30);
        config.setNearingCompletionMinutes(30);
        config.setMissedPlanMinutes(60);
        config.setMaxRetryAttempts(3);
        return config;
    }

    private AdvancedFastingGovernanceDto toDto(AdvancedFastingOperationsConfigEntity config) {
        return new AdvancedFastingGovernanceDto(
                FastingSafetyPolicy.VERSION,
                FastingSafetyPolicy.MAX_CONTINUOUS_FASTING_HOURS,
                Boolean.TRUE.equals(config.getReminderEnabled()),
                config.getPreStartMinutes(),
                config.getNearingCompletionMinutes(),
                config.getMissedPlanMinutes(),
                config.getMaxRetryAttempts(),
                deliveryRepository.countByStatus(FastingReminderDeliveryStatus.PENDING),
                deliveryRepository.countByStatus(FastingReminderDeliveryStatus.DEFERRED),
                deliveryRepository.countByStatus(FastingReminderDeliveryStatus.FAILED),
                deliveryRepository.countByStatus(FastingReminderDeliveryStatus.SUPPRESSED),
                metric("grun.fasting.safety.blocked"),
                metric("grun.fasting.scheduler.failures"),
                metric("grun.fasting.push.failures"),
                config.getUpdatedAt()
        );
    }

    private long metric(String name) {
        return Math.round(meterRegistry.find(name).counters().stream()
                .mapToDouble(counter -> counter.count()).sum());
    }
}
package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AdminAiOperationsPolicyDto;
import com.grun.calorietracker.dto.AdminAiOperationsPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AdminAiOperationsRollbackRequestDto;
import com.grun.calorietracker.entity.AiOperationsPolicyEntity;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.repository.AiOperationsPolicyRepository;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.service.AiOperationsPolicyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiOperationsPolicyServiceImpl implements AiOperationsPolicyService {
    private static final long POLICY_ID = 1L;

    private final AiOperationsPolicyRepository policyRepository;
    private final AiRequestHistoryRepository requestHistoryRepository;
    private final AiProperties aiProperties;

    @PostConstruct
    void applyPersistedPolicy() {
        policyRepository.findById(POLICY_ID).ifPresent(this::applyRuntimeConfiguration);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAiOperationsPolicyDto getPolicy() {
        return toDto(requiredPolicy());
    }

    @Override
    @Transactional
    public AdminAiOperationsPolicyDto update(String adminEmail, AdminAiOperationsPolicyUpdateRequestDto request) {
        AiOperationsPolicyEntity policy = requiredPolicy();
        requireCurrentVersion(policy, request.getVersion());
        policy.setPreviousModel(effectiveModel(policy));
        policy.setPreviousPhotoModel(effectivePhotoModel(policy));
        policy.setPreviousPhotoProvider(effectivePhotoProvider(policy));
        policy.setPreviousPromptVersion(effectivePromptVersion(policy));
        policy.setCircuitOpen(request.getCircuitOpen());
        policy.setFailureRateThreshold(request.getFailureRateThreshold());
        policy.setRejectionRateThreshold(request.getRejectionRateThreshold());
        policy.setMaxTokensPer24Hours(request.getMaxTokensPer24Hours());
        policy.setMaxCostPer24Hours(request.getMaxCostPer24Hours());
        policy.setCostCurrency(request.getCostCurrency().trim().toUpperCase(Locale.ROOT));
        policy.setActiveModel(request.getActiveModel().trim());
        policy.setActivePhotoModel(isBlank(request.getActivePhotoModel())
                ? request.getActiveModel().trim()
                : request.getActivePhotoModel().trim());
        policy.setActivePhotoProvider(request.getActivePhotoProvider());
        policy.setActivePromptVersion(request.getActivePromptVersion().trim());
        policy.setChangeReason(request.getReason().trim());
        policy.setUpdatedBy(adminEmail);
        policy.setUpdatedAt(LocalDateTime.now());
        AiOperationsPolicyEntity saved = policyRepository.saveAndFlush(policy);
        applyRuntimeConfiguration(saved);
        return toDto(saved);
    }

    @Override
    @Transactional
    public AdminAiOperationsPolicyDto rollback(String adminEmail, AdminAiOperationsRollbackRequestDto request) {
        AiOperationsPolicyEntity policy = requiredPolicy();
        requireCurrentVersion(policy, request.getVersion());
        if (isBlank(policy.getPreviousModel()) || isBlank(policy.getPreviousPhotoModel())
                || policy.getPreviousPhotoProvider() == null
                || isBlank(policy.getPreviousPromptVersion())) {
            throw new IllegalArgumentException("No previous AI model and prompt deployment is available.");
        }
        String currentModel = effectiveModel(policy);
        String currentPhotoModel = effectivePhotoModel(policy);
        var currentPhotoProvider = effectivePhotoProvider(policy);
        String currentPrompt = effectivePromptVersion(policy);
        policy.setActiveModel(policy.getPreviousModel());
        policy.setActivePhotoModel(policy.getPreviousPhotoModel());
        policy.setActivePhotoProvider(policy.getPreviousPhotoProvider());
        policy.setActivePromptVersion(policy.getPreviousPromptVersion());
        policy.setPreviousModel(currentModel);
        policy.setPreviousPhotoModel(currentPhotoModel);
        policy.setPreviousPhotoProvider(currentPhotoProvider);
        policy.setPreviousPromptVersion(currentPrompt);
        policy.setChangeReason(request.getReason().trim());
        policy.setUpdatedBy(adminEmail);
        policy.setUpdatedAt(LocalDateTime.now());
        AiOperationsPolicyEntity saved = policyRepository.saveAndFlush(policy);
        applyRuntimeConfiguration(saved);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertRequestAllowed() {
        AiOperationsPolicyEntity policy = requiredPolicy();
        if (Boolean.TRUE.equals(policy.getCircuitOpen())) {
            throw new AiProviderException("AI requests are temporarily paused by the operations circuit breaker.");
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long tokens = requestHistoryRepository.sumTotalTokensAfter(since);
        if (policy.getMaxTokensPer24Hours() > 0 && tokens >= policy.getMaxTokensPer24Hours()) {
            throw new AiProviderException("AI requests are temporarily paused because the 24-hour token budget was reached.");
        }
        double cost = requestHistoryRepository.sumEstimatedCostAfter(since, policy.getCostCurrency());
        if (policy.getMaxCostPer24Hours() > 0 && cost >= policy.getMaxCostPer24Hours()) {
            throw new AiProviderException("AI requests are temporarily paused because the 24-hour cost budget was reached.");
        }
    }

    private AiOperationsPolicyEntity requiredPolicy() {
        return policyRepository.findById(POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("AI operations policy is not initialized."));
    }

    private void requireCurrentVersion(AiOperationsPolicyEntity policy, Long requestedVersion) {
        if (requestedVersion == null || !requestedVersion.equals(policy.getVersion())) {
            throw new IllegalArgumentException("AI operations policy changed. Refresh before saving.");
        }
    }

    private void applyRuntimeConfiguration(AiOperationsPolicyEntity policy) {
        if (!isBlank(policy.getActiveModel())) aiProperties.setModel(policy.getActiveModel());
        if (!isBlank(policy.getActivePhotoModel())) aiProperties.getPhoto().setModel(policy.getActivePhotoModel());
        if (policy.getActivePhotoProvider() != null) aiProperties.getPhoto().setProvider(policy.getActivePhotoProvider());
        if (!isBlank(policy.getActivePromptVersion())) aiProperties.setPromptVersion(policy.getActivePromptVersion());
        AiProperties.Monitoring monitoring = aiProperties.getMonitoring();
        monitoring.setFailureRateThreshold(policy.getFailureRateThreshold());
        monitoring.setRejectionRateThreshold(policy.getRejectionRateThreshold());
        monitoring.setMaxTokensPerWindow(policy.getMaxTokensPer24Hours());
        monitoring.setMaxEstimatedCostPerCurrency(policy.getMaxCostPer24Hours());
    }

    private AdminAiOperationsPolicyDto toDto(AiOperationsPolicyEntity policy) {
        AdminAiOperationsPolicyDto dto = new AdminAiOperationsPolicyDto();
        dto.setVersion(policy.getVersion());
        dto.setCircuitOpen(Boolean.TRUE.equals(policy.getCircuitOpen()));
        dto.setFailureRateThreshold(policy.getFailureRateThreshold());
        dto.setRejectionRateThreshold(policy.getRejectionRateThreshold());
        dto.setMaxTokensPer24Hours(policy.getMaxTokensPer24Hours());
        dto.setMaxCostPer24Hours(policy.getMaxCostPer24Hours());
        dto.setCostCurrency(policy.getCostCurrency());
        dto.setActiveModel(effectiveModel(policy));
        dto.setActivePhotoModel(effectivePhotoModel(policy));
        dto.setActivePhotoProvider(effectivePhotoProvider(policy));
        dto.setPhotoInputTokenCostPer1m(aiProperties.getPhoto().getInputTokenCostPer1m());
        dto.setPhotoOutputTokenCostPer1m(aiProperties.getPhoto().getOutputTokenCostPer1m());
        dto.setPhotoCostCurrency(aiProperties.getPhoto().getCostCurrency());
        dto.setActivePromptVersion(effectivePromptVersion(policy));
        dto.setRollbackAvailable(!isBlank(policy.getPreviousModel())
                && !isBlank(policy.getPreviousPhotoModel())
                && policy.getPreviousPhotoProvider() != null
                && !isBlank(policy.getPreviousPromptVersion()));
        dto.setUpdatedBy(policy.getUpdatedBy());
        dto.setUpdatedAt(policy.getUpdatedAt());
        return dto;
    }

    private String effectiveModel(AiOperationsPolicyEntity policy) {
        return isBlank(policy.getActiveModel()) ? aiProperties.getModel() : policy.getActiveModel();
    }

    private String effectivePromptVersion(AiOperationsPolicyEntity policy) {
        return isBlank(policy.getActivePromptVersion()) ? aiProperties.getPromptVersion() : policy.getActivePromptVersion();
    }

    private String effectivePhotoModel(AiOperationsPolicyEntity policy) {
        String configured = aiProperties.getPhoto().getModel();
        String fallback = isBlank(configured) ? aiProperties.getModel() : configured;
        return isBlank(policy.getActivePhotoModel()) ? fallback : policy.getActivePhotoModel();
    }

    private com.grun.calorietracker.enums.AiProvider effectivePhotoProvider(AiOperationsPolicyEntity policy) {
        return policy.getActivePhotoProvider() == null
                ? aiProperties.resolveProvider(com.grun.calorietracker.enums.AiRequestType.PHOTO_MEAL_LOG)
                : policy.getActivePhotoProvider();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

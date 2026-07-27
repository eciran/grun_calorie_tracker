package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AdminAiOperationsPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AdminAiOperationsRollbackRequestDto;
import com.grun.calorietracker.entity.AiOperationsPolicyEntity;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.repository.AiOperationsPolicyRepository;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationsPolicyServiceImplTest {
    private final AiOperationsPolicyRepository policyRepository = mock(AiOperationsPolicyRepository.class);
    private final AiRequestHistoryRepository historyRepository = mock(AiRequestHistoryRepository.class);
    private final AiProperties properties = new AiProperties();
    private final AiOperationsPolicyServiceImpl service =
            new AiOperationsPolicyServiceImpl(policyRepository, historyRepository, properties);

    @Test
    void update_appliesVersionedRuntimePolicy() {
        AiOperationsPolicyEntity policy = policy();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AdminAiOperationsPolicyUpdateRequestDto request = updateRequest();

        var result = service.update("ops@grun.app", request);

        assertEquals("gpt-next", result.getActiveModel());
        assertEquals("prompt-v3", properties.getPromptVersion());
        assertEquals(0.1, properties.getMonitoring().getFailureRateThreshold());
        verify(policyRepository).saveAndFlush(policy);
    }

    @Test
    void update_whenVersionIsStale_rejects() {
        AiOperationsPolicyEntity policy = policy();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        AdminAiOperationsPolicyUpdateRequestDto request = updateRequest();
        request.setVersion(2L);

        assertThrows(IllegalArgumentException.class, () -> service.update("ops@grun.app", request));
    }

    @Test
    void assertRequestAllowed_whenCircuitIsOpen_blocksProviderCall() {
        AiOperationsPolicyEntity policy = policy();
        policy.setCircuitOpen(true);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        assertThrows(AiProviderException.class, service::assertRequestAllowed);
    }

    @Test
    void rollback_restoresPreviousModelAndPrompt() {
        AiOperationsPolicyEntity policy = policy();
        policy.setPreviousModel("gpt-stable");
        policy.setPreviousPromptVersion("prompt-v1");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AdminAiOperationsRollbackRequestDto request = new AdminAiOperationsRollbackRequestDto();
        request.setVersion(3L);
        request.setReason("Rollback after quality regression");

        var result = service.rollback("ops@grun.app", request);

        assertEquals("gpt-stable", result.getActiveModel());
        assertEquals("prompt-v1", properties.getPromptVersion());
    }

    private AiOperationsPolicyEntity policy() {
        AiOperationsPolicyEntity policy = new AiOperationsPolicyEntity();
        policy.setId(1L);
        policy.setVersion(3L);
        policy.setCircuitOpen(false);
        policy.setFailureRateThreshold(0.2);
        policy.setRejectionRateThreshold(0.4);
        policy.setMaxTokensPer24Hours(1_000_000L);
        policy.setMaxCostPer24Hours(20d);
        policy.setCostCurrency("USD");
        policy.setActiveModel("gpt-current");
        policy.setActivePromptVersion("prompt-v2");
        policy.setChangeReason("Initial policy");
        policy.setUpdatedBy("system");
        policy.setUpdatedAt(LocalDateTime.now());
        return policy;
    }

    private AdminAiOperationsPolicyUpdateRequestDto updateRequest() {
        AdminAiOperationsPolicyUpdateRequestDto request = new AdminAiOperationsPolicyUpdateRequestDto();
        request.setVersion(3L);
        request.setCircuitOpen(false);
        request.setFailureRateThreshold(0.1);
        request.setRejectionRateThreshold(0.25);
        request.setMaxTokensPer24Hours(2_000_000L);
        request.setMaxCostPer24Hours(40d);
        request.setCostCurrency("USD");
        request.setActiveModel("gpt-next");
        request.setActivePromptVersion("prompt-v3");
        request.setReason("Controlled production rollout");
        return request;
    }
}
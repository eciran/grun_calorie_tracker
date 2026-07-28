package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AiRequestHistoryDetailDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AiRequestHistoryService;
import com.grun.calorietracker.service.support.AiSafeResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiRequestHistoryServiceImpl implements AiRequestHistoryService {

    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<AiRequestHistoryDetailDto> listHistory(String email, AiRequestType requestType, AiRequestStatus status, int limit) {
        UserEntity user = getUser(email);
        PageRequest pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        List<AiRequestHistoryEntity> history = findHistory(user, requestType, status, pageable);
        return history.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public AiRequestHistoryDetailDto getHistoryItem(String email, Long requestId) {
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("AI request history item was not found."));
        return toDto(history);
    }

    @Override
    @Transactional
    public void acknowledgeCompletion(String email, Long requestId) {
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("AI request history item was not found."));
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED
                && history.getStatus() != AiRequestStatus.FAILED) {
            throw new IllegalArgumentException("AI request is not complete.");
        }
        if (history.getCompletionNotifiedAt() == null) {
            history.setCompletionNotifiedAt(LocalDateTime.now());
            aiRequestHistoryRepository.save(history);
        }
    }

    private List<AiRequestHistoryEntity> findHistory(
            UserEntity user,
            AiRequestType requestType,
            AiRequestStatus status,
            PageRequest pageable
    ) {
        if (requestType != null && status != null) {
            return aiRequestHistoryRepository.findByUserAndRequestTypeAndStatusOrderByCreatedAtDesc(user, requestType, status, pageable);
        }
        if (requestType != null) {
            return aiRequestHistoryRepository.findByUserAndRequestTypeOrderByCreatedAtDesc(user, requestType, pageable);
        }
        if (status != null) {
            return aiRequestHistoryRepository.findByUserAndStatusOrderByCreatedAtDesc(user, status, pageable);
        }
        return aiRequestHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    private AiRequestHistoryDetailDto toDto(AiRequestHistoryEntity entity) {
        AiRequestHistoryDetailDto dto = new AiRequestHistoryDetailDto();
        dto.setId(entity.getId());
        dto.setRequestType(entity.getRequestType());
        dto.setProvider(entity.getProvider());
        dto.setModel(entity.getModel());
        dto.setPromptVersion(entity.getPromptVersion());
        dto.setStatus(entity.getStatus());
        dto.setQuotaConsumed(entity.getQuotaConsumed());
        dto.setQuotaConsumedAmount(entity.getQuotaConsumedAmount());
        dto.setQuotaRefundedAmount(entity.getQuotaRefundedAmount());
        dto.setLatencyMs(entity.getLatencyMs());
        dto.setTotalTokens(entity.getTotalTokens());
        dto.setEstimatedCost(entity.getEstimatedCost());
        dto.setCostCurrency(entity.getCostCurrency());
        dto.setRejectionReason(entity.getRejectionReason());
        dto.setHasRejectionFeedback(entity.getRejectionFeedback() != null && !entity.getRejectionFeedback().isBlank());
        dto.setUserMessage(entity.getStatus() == AiRequestStatus.FAILED
                ? AiSafeResponseBuilder.GENERIC_AI_FAILURE_MESSAGE
                : null);
        dto.setInputPayload(readJson(entity.getInputPayload()));
        JsonNode outputPayload = readJson(entity.getOutputPayload());
        dto.setOutputPayload(outputPayload);
        JsonNode safeOutputPayload = resolveSafeOutputPayload(entity, outputPayload);
        dto.setSafeOutputPayload(safeOutputPayload);
        dto.setHasSafeOutputPayload(safeOutputPayload != null);
        dto.setConfirmationPayload(readJson(entity.getConfirmationPayload()));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setConfirmedAt(entity.getConfirmedAt());
        dto.setRejectedAt(entity.getRejectedAt());
        return dto;
    }

    private JsonNode resolveSafeOutputPayload(AiRequestHistoryEntity entity, JsonNode outputPayload) {
        if (entity.getStatus() == AiRequestStatus.FAILED) {
            if (outputPayload != null && outputPayload.hasNonNull("userMessage")) {
                return outputPayload;
            }
            return objectMapper.valueToTree(AiSafeResponseBuilder.failurePayload(entity.getRequestType(), true));
        }
        return outputPayload;
    }

    private JsonNode readJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }
}

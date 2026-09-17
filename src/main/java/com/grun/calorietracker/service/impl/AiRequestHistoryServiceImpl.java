package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AiRequestHistoryDetailDto;
import com.grun.calorietracker.dto.AiRequestHistoryDto;
import com.grun.calorietracker.dto.AiRequestHistoryPageDto;
import com.grun.calorietracker.dto.AiRequestRecoveryDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AiRequestHistoryService;
import com.grun.calorietracker.service.support.AiSafeResponseBuilder;
import com.grun.calorietracker.service.support.AiIdempotencySupport;
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
    @Transactional(readOnly = true)
    public AiRequestRecoveryDto recoverByKey(String email, AiRequestType requestType, String idempotencyKey) {
        UserEntity user = getUser(email);
        if (requestType == null) throw new IllegalArgumentException("AI request type is required.");
        String key = AiIdempotencySupport.normalizeKey(idempotencyKey);
        // No provider, entitlement, quota, completion acknowledgement or retry side effects.
        return aiRequestHistoryRepository.findByUserAndRequestTypeAndIdempotencyKey(user, requestType, key)
                .map(history -> new AiRequestRecoveryDto(true, toSummary(history)))
                .orElseGet(() -> new AiRequestRecoveryDto(false, null));
    }

    @Override
    @Transactional(readOnly = true)
    public AiRequestHistoryPageDto listHistoryPage(String email, List<AiRequestType> requestTypes,
                                                  List<AiRequestStatus> statuses, Long beforeId, int limit) {
        UserEntity user = getUser(email);
        if (limit < 1 || limit > 100 || (beforeId != null && beforeId < 1)) {
            throw new IllegalArgumentException("Invalid history page bounds.");
        }
        var types = requestTypes == null || requestTypes.isEmpty() ? List.of(AiRequestType.values()) : requestTypes;
        var states = statuses == null || statuses.isEmpty() ? List.of(AiRequestStatus.values()) : statuses;
        // ID ordering provides a stable cursor even when timestamps tie or new requests arrive.
        var rows = aiRequestHistoryRepository.findUserHistoryPage(user, types, states,
                beforeId == null ? Long.MAX_VALUE : beforeId, PageRequest.of(0, limit + 1));
        var items = rows.stream().limit(limit).map(this::toSummary).toList();
        Long next = rows.size() > limit ? items.get(items.size() - 1).getId() : null;
        return new AiRequestHistoryPageDto(items, next);
    }

    private AiRequestHistoryDto toSummary(AiRequestHistoryEntity entity) {
        AiRequestHistoryDto dto = new AiRequestHistoryDto();
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
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

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
        if (entity.getRequestType() == com.grun.calorietracker.enums.AiRequestType.AI_DAILY_INSIGHT
                || entity.getRequestType() == com.grun.calorietracker.enums.AiRequestType.AI_WEEKLY_INSIGHT) {
            outputPayload = com.grun.calorietracker.service.support.AiCoachingPresentation.cleanHistory(outputPayload);
        }
        dto.setOutputPayload(outputPayload);
        JsonNode safeOutputPayload = resolveSafeOutputPayload(entity, outputPayload);
        dto.setSafeOutputPayload(safeOutputPayload);
        if (entity.getStatus() == AiRequestStatus.FAILED && safeOutputPayload != null
                && ("NO_FOOD_DETECTED".equals(safeOutputPayload.path("errorCode").asText())
                || "IMAGE_UNCLEAR".equals(safeOutputPayload.path("errorCode").asText()))) {
            dto.setUserMessage(safeOutputPayload.path("userMessage").asText());
        }
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

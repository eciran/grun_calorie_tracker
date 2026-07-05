package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiInsightRequestDto;
import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AiInsightService;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.DashboardService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiInsightServiceImpl implements AiInsightService {

    private static final int MAX_WEEKLY_DAYS = 14;

    private final AiProperties properties;
    private final List<AiMealDraftProviderClient> providerClients;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final UserRepository userRepository;
    private final DashboardService dashboardService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;

    @Override
    @Transactional
    public AiInsightResponseDto createDailyInsight(String email, AiInsightRequestDto request) {
        AiInsightRequestDto safeRequest = request == null ? new AiInsightRequestDto() : request;
        LocalDate date = safeRequest.getDate() == null ? LocalDate.now() : safeRequest.getDate();
        safeRequest.setDate(date);
        safeRequest.setContext(toDailyContext(dashboardService.getDailySummary(email, date)));
        return createInsight(email, AiRequestType.AI_DAILY_INSIGHT, safeRequest);
    }

    @Override
    @Transactional
    public AiInsightResponseDto createWeeklyInsight(String email, AiInsightRequestDto request) {
        AiInsightRequestDto safeRequest = request == null ? new AiInsightRequestDto() : request;
        LocalDate end = safeRequest.getEndDate() == null ? LocalDate.now() : safeRequest.getEndDate();
        LocalDate start = safeRequest.getStartDate() == null ? end.minusDays(6) : safeRequest.getStartDate();
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Weekly insight startDate must be before or equal to endDate.");
        }
        if (start.plusDays(MAX_WEEKLY_DAYS - 1L).isBefore(end)) {
            throw new IllegalArgumentException("Weekly insight range cannot exceed " + MAX_WEEKLY_DAYS + " days.");
        }
        safeRequest.setStartDate(start);
        safeRequest.setEndDate(end);
        safeRequest.setContext(toWeeklyContext(email, start, end));
        return createInsight(email, AiRequestType.AI_WEEKLY_INSIGHT, safeRequest);
    }

    private AiInsightResponseDto createInsight(String email, AiRequestType requestType, AiInsightRequestDto request) {
        sanitizeUserContext(request);
        providerConfigurationValidator.validateConfiguredForDraft();
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_INSIGHTS);
        UserEntity user = getUser(email);
        if (Boolean.FALSE.equals(user.getAiInsightsEnabled())) {
            throw new IllegalArgumentException("AI insights are disabled for this user.");
        }

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(requestType);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setInputPayload(writeJson(toPrivacySafeInputPayload(requestType, request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);

        long startedAt = System.nanoTime();
        SubscriptionDto quota = subscriptionService.consumeAiQuota(email);
        try {
            AiInsightResponseDto response = requestType == AiRequestType.AI_DAILY_INSIGHT
                    ? activeProvider().createDailyInsight(request)
                    : activeProvider().createWeeklyInsight(request);
            response = normalize(response, requestType);
            response.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());

            history.setStatus(AiRequestStatus.DRAFT_CREATED);
            history.setOutputPayload(writeJson(response));
            history.setQuotaConsumed(true);
            history.setQuotaConsumedAmount(1);
            history.setLatencyMs(elapsedMs(startedAt));
            AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
            response.setRequestId(saved.getId());
            return response;
        } catch (RuntimeException ex) {
            boolean refunded = refundConsumedQuota(user);
            history.setStatus(AiRequestStatus.FAILED);
            history.setErrorMessage(ex.getMessage());
            history.setQuotaConsumed(!refunded);
            history.setQuotaConsumedAmount(refunded ? 0 : 1);
            history.setLatencyMs(elapsedMs(startedAt));
            aiRequestHistoryRepository.save(history);
            throw ex;
        }
    }

    private AiInsightResponseDto normalize(AiInsightResponseDto response, AiRequestType requestType) {
        if (response == null) {
            throw new IllegalArgumentException("AI insight provider returned an empty response.");
        }
        response.setRequestType(requestType);
        response.setProvider(properties.getProvider());
        response.setModel(properties.getModel());
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        if (response.getTitle() == null || response.getTitle().isBlank()) {
            response.setTitle(requestType == AiRequestType.AI_DAILY_INSIGHT ? "Daily insight" : "Weekly insight");
        }
        if (response.getSummary() == null || response.getSummary().isBlank()) {
            throw new IllegalArgumentException("AI insight provider returned no summary.");
        }
        if (response.getHighlights() == null) {
            response.setHighlights(List.of());
        }
        if (response.getWarnings() == null) {
            response.setWarnings(List.of());
        }
        if (response.getRecommendedActions() == null) {
            response.setRecommendedActions(List.of());
        }
        return response;
    }

    private Map<String, Object> toDailyContext(DailySummaryDto summary) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("date", summary.getSummaryDate());
        context.put("targetCalories", summary.getTargetCalories());
        context.put("consumedCalories", summary.getConsumedCalories());
        context.put("burnedCalories", summary.getBurnedCalories());
        context.put("remainingCalories", summary.getRemainingCalories());
        context.put("proteinProgressPercent", summary.getProteinProgressPercent());
        context.put("nutritionQualityScore", summary.getNutritionQualityScore());
        context.put("totalExerciseMinutes", summary.getTotalExerciseMinutes());
        context.put("currentLogStreakDays", summary.getCurrentLogStreakDays());
        context.put("hasFoodLogs", summary.getHasFoodLogs());
        context.put("hasExerciseLogs", summary.getHasExerciseLogs());
        return context;
    }

    private Map<String, Object> toWeeklyContext(String email, LocalDate start, LocalDate end) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("startDate", start);
        context.put("endDate", end);
        int days = 0;
        double consumedCalories = 0;
        double burnedCalories = 0;
        double exerciseMinutes = 0;
        int diaryDays = 0;
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            DailySummaryDto summary = dashboardService.getDailySummary(email, cursor);
            days++;
            consumedCalories += value(summary.getConsumedCalories());
            burnedCalories += value(summary.getBurnedCalories());
            exerciseMinutes += summary.getTotalExerciseMinutes() == null ? 0 : summary.getTotalExerciseMinutes();
            if (Boolean.TRUE.equals(summary.getHasAnyDiaryEntry())) {
                diaryDays++;
            }
        }
        context.put("days", days);
        context.put("averageConsumedCalories", round(consumedCalories / Math.max(days, 1)));
        context.put("averageBurnedCalories", round(burnedCalories / Math.max(days, 1)));
        context.put("totalExerciseMinutes", round(exerciseMinutes));
        context.put("diaryDays", diaryDays);
        return context;
    }

    private Map<String, Object> toPrivacySafeInputPayload(AiRequestType requestType, AiInsightRequestDto request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestType", requestType);
        payload.put("date", request.getDate());
        payload.put("startDate", request.getStartDate());
        payload.put("endDate", request.getEndDate());
        payload.put("focus", request.getFocus());
        payload.put("noteLength", request.getNote() == null ? 0 : request.getNote().length());
        payload.put("language", request.getLanguage());
        payload.put("hasBackendContext", request.getContext() != null);
        return payload;
    }


    private void sanitizeUserContext(AiInsightRequestDto request) {
        request.setNote(sanitizeNote(request.getNote()));
        if (request.getLanguage() != null) {
            request.setLanguage(request.getLanguage().trim());
        }
    }

    private String sanitizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String sanitized = note
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.length() > 160 ? sanitized.substring(0, 160).trim() : sanitized;
    }
    private AiMealDraftProviderClient activeProvider() {
        Map<AiProvider, AiMealDraftProviderClient> clients = new EnumMap<>(AiProvider.class);
        for (AiMealDraftProviderClient client : providerClients) {
            clients.put(client.provider(), client);
        }
        AiMealDraftProviderClient client = clients.get(properties.getProvider());
        if (client == null) {
            throw new IllegalArgumentException("AI provider is not configured: " + properties.getProvider());
        }
        return client;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("AI insight payload could not be serialized.");
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean refundConsumedQuota(UserEntity user) {
        try {
            subscriptionService.refundConsumedAiQuota(user.getId(), 1);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private double value(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}



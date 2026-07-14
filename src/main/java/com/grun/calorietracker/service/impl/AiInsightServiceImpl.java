package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiInsightRequestDto;
import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.AiUsageMetadataCarrier;
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
import com.grun.calorietracker.service.support.AiSafeResponseBuilder;
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
        history.setPromptVersion(properties.getPromptVersion());
        history.setInputPayload(writeJson(toPrivacySafeInputPayload(requestType, request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);

        long startedAt = System.nanoTime();
        SubscriptionDto quota = subscriptionService.consumeAiQuota(email);
        try {
            AiInsightResponseDto response = requestType == AiRequestType.AI_DAILY_INSIGHT
                    ? activeProvider().createDailyInsight(request)
                    : activeProvider().createWeeklyInsight(request);
            response = normalize(response, requestType, request);
            response.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());
            copyUsageMetadata(response, history);

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
            history.setOutputPayload(writeJson(AiSafeResponseBuilder.failurePayload(requestType, true)));
            history.setQuotaConsumed(!refunded);
            history.setQuotaConsumedAmount(refunded ? 0 : 1);
            history.setLatencyMs(elapsedMs(startedAt));
            aiRequestHistoryRepository.save(history);
            throw ex;
        }
    }

    private AiInsightResponseDto normalize(AiInsightResponseDto response, AiRequestType requestType, AiInsightRequestDto request) {
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
        normalizeQuality(response, requestType);
        enrichStructuredInsight(response, requestType, request);
        return response;
    }

    private void normalizeQuality(AiInsightResponseDto response, AiRequestType requestType) {
        response.setSchemaVersion("ai_response_v3");
        if (response.getReviewReasons() == null) {
            response.setReviewReasons(List.of());
        }
        if (response.getConfidence() == null) {
            response.setConfidence(0.8);
        }
        if (response.getQualityScore() == null) {
            response.setQualityScore((int) Math.round(response.getConfidence() * 100));
        }
        if (response.getPriority() == null || response.getPriority().isBlank()) {
            response.setPriority("MEDIUM");
        }
        if (response.getCategory() == null || response.getCategory().isBlank()) {
            response.setCategory("GENERAL");
        }
        if (response.getActionType() == null || response.getActionType().isBlank()) {
            response.setActionType("NONE");
        }
        if (response.getCtaLabel() == null || response.getCtaLabel().isBlank()) {
            response.setCtaLabel(requestType == AiRequestType.AI_DAILY_INSIGHT ? "Review today" : "Review week");
        }
        if (response.getCtaTarget() == null || response.getCtaTarget().isBlank()) {
            response.setCtaTarget(requestType == AiRequestType.AI_DAILY_INSIGHT ? "daily-summary" : "weekly-summary");
        }
    }
    private void enrichStructuredInsight(AiInsightResponseDto response, AiRequestType requestType, AiInsightRequestDto request) {
        Map<String, Object> context = contextMap(request.getContext());
        if (response.getDataCoverage() == null) {
            response.setDataCoverage(new AiInsightResponseDto.DataCoverage());
        }
        AiInsightResponseDto.DataCoverage coverage = response.getDataCoverage();
        if (coverage.getSignalsUsed() == null) {
            coverage.setSignalsUsed(new java.util.ArrayList<>());
        }
        if (coverage.getMissingSignals() == null) {
            coverage.setMissingSignals(new java.util.ArrayList<>());
        }
        if (response.getKeyFindings() == null) {
            response.setKeyFindings(new java.util.ArrayList<>());
        }
        if (response.getPersonalizedActions() == null) {
            response.setPersonalizedActions(new java.util.ArrayList<>());
        }

        if (requestType == AiRequestType.AI_DAILY_INSIGHT) {
            enrichDailyInsight(response, context);
        } else {
            enrichWeeklyInsight(response, context);
        }
        if (coverage.getConfidenceLabel() == null || coverage.getConfidenceLabel().isBlank()) {
            coverage.setConfidenceLabel(resolveConfidenceLabel(response.getConfidence(), coverage.getMissingSignals().size()));
        }
        if (response.getDataQualityNote() == null || response.getDataQualityNote().isBlank()) {
            response.setDataQualityNote(buildDataQualityNote(coverage));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contextMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return java.util.Collections.emptyMap();
    }
    private void enrichDailyInsight(AiInsightResponseDto response, Map<String, Object> context) {
        AiInsightResponseDto.DataCoverage coverage = response.getDataCoverage();
        coverage.setDaysAnalyzed(defaultInt(coverage.getDaysAnalyzed(), 1));
        coverage.setExerciseLogged(defaultBoolean(coverage.getExerciseLogged(), bool(context.get("hasExerciseLogs"))));
        coverage.setExerciseMinutes(defaultInt(coverage.getExerciseMinutes(), intValue(context.get("totalExerciseMinutes"))));
        coverage.setMealsLogged(defaultInt(coverage.getMealsLogged(), Boolean.TRUE.equals(bool(context.get("hasFoodLogs"))) ? 1 : 0));
        coverage.setDiaryDays(defaultInt(coverage.getDiaryDays(), Boolean.TRUE.equals(bool(context.get("hasFoodLogs"))) ? 1 : 0));
        addSignal(coverage, "calories");
        addSignal(coverage, "protein");
        if (Boolean.TRUE.equals(coverage.getExerciseLogged())) {
            addSignal(coverage, "exercise");
        } else {
            addMissingSignal(coverage, "exercise");
        }
        addMissingSignal(coverage, "water");
        addMissingSignal(coverage, "sleep");

        Double consumedCalories = doubleValue(context.get("consumedCalories"));
        Double targetCalories = doubleValue(context.get("targetCalories"));
        Double proteinProgress = doubleValue(context.get("proteinProgressPercent"));
        Integer qualityScore = intValue(context.get("nutritionQualityScore"));
        Integer streakDays = intValue(context.get("currentLogStreakDays"));

        if (response.getKeyFindings().isEmpty()) {
            addFinding(response, "trend", "Calorie control", calorieMessage(consumedCalories, targetCalories), calorieEvidence(consumedCalories, targetCalories), "Shows how close today was to the user's target.", "LOW");
            addFinding(response, "pattern", "Protein balance", proteinMessage(proteinProgress), proteinProgress == null ? "Protein progress is missing." : "Protein progress: " + proteinProgress.intValue() + "%.", "Helps decide whether tomorrow should push protein or balance portions.", proteinProgress != null && proteinProgress > 125 ? "MEDIUM" : "LOW");
            if (qualityScore != null) {
                addFinding(response, "quality", "Nutrition quality", "Nutrition quality scored " + qualityScore + "/100.", "Quality score: " + qualityScore + "/100.", "Points the next improvement beyond calories.", qualityScore < 65 ? "MEDIUM" : "LOW");
            }
            if (streakDays != null && streakDays > 0) {
                addFinding(response, "consistency", "Logging consistency", "The current logging streak is " + streakDays + " day(s).", "Current streak: " + streakDays + " day(s).", "Consistency improves future coaching accuracy.", "LOW");
            }
        }
        if (response.getPersonalizedActions().isEmpty()) {
            addAction(response, 1, nextFoodAction(proteinProgress, qualityScore), "This targets the biggest visible opportunity from today's nutrition data.", "Better balance tomorrow without overcorrecting.", "LOW", "nutritionQualityScore");
            addAction(response, 2, "Keep tomorrow's calorie intake close to target rather than aggressively cutting.", "Today is already close enough that a large correction would add noise.", "More stable weekly progress.", "LOW", "consumedCalories");
        }
        if (response.getTomorrowFocus() == null || response.getTomorrowFocus().isBlank()) {
            response.setTomorrowFocus(nextFoodAction(proteinProgress, qualityScore));
        }
        if (response.getWatchOut() == null || response.getWatchOut().isBlank()) {
            response.setWatchOut(proteinProgress != null && proteinProgress > 125
                    ? "Protein is already high; tomorrow should focus on balance and portions, not simply adding more protein."
                    : "Do not judge the day from one metric only; use calories, protein, quality score and activity together.");
        }
    }

    private void enrichWeeklyInsight(AiInsightResponseDto response, Map<String, Object> context) {
        AiInsightResponseDto.DataCoverage coverage = response.getDataCoverage();
        Integer days = intValue(context.get("days"));
        Integer diaryDays = intValue(context.get("diaryDays"));
        Double exerciseMinutes = doubleValue(context.get("totalExerciseMinutes"));
        coverage.setDaysAnalyzed(defaultInt(coverage.getDaysAnalyzed(), days == null ? 7 : days));
        coverage.setDiaryDays(defaultInt(coverage.getDiaryDays(), diaryDays));
        coverage.setExerciseLogged(defaultBoolean(coverage.getExerciseLogged(), exerciseMinutes != null && exerciseMinutes > 0));
        coverage.setExerciseMinutes(defaultInt(coverage.getExerciseMinutes(), exerciseMinutes == null ? null : exerciseMinutes.intValue()));
        addSignal(coverage, "calorie trend");
        addSignal(coverage, "logging consistency");
        if (Boolean.TRUE.equals(coverage.getExerciseLogged())) {
            addSignal(coverage, "exercise trend");
        } else {
            addMissingSignal(coverage, "exercise trend");
        }
        addMissingSignal(coverage, "sleep");
        addMissingSignal(coverage, "water");

        if (response.getKeyFindings().isEmpty()) {
            addFinding(response, "pattern", "Logging coverage", "Logged diary data on " + formatInt(diaryDays) + " of " + formatInt(days) + " day(s).", "Diary days: " + formatInt(diaryDays) + "/" + formatInt(days) + ".", "More logged days make weekly coaching more reliable.", coverage.getDiaryDays() != null && coverage.getDaysAnalyzed() != null && coverage.getDiaryDays() < coverage.getDaysAnalyzed() ? "MEDIUM" : "LOW");
            addFinding(response, "trend", "Exercise volume", "Exercise volume for the period was " + formatDouble(exerciseMinutes) + " minute(s).", "Total exercise minutes: " + formatDouble(exerciseMinutes) + ".", "Shows whether training support is present alongside nutrition.", exerciseMinutes != null && exerciseMinutes > 0 ? "LOW" : "MEDIUM");
        }
        if (response.getPersonalizedActions().isEmpty()) {
            addAction(response, 1, "Choose one repeatable nutrition habit for the next 7 days instead of changing everything at once.", "Weekly trends improve faster when the target is narrow and measurable.", "Better adherence and clearer signal next week.", "LOW", "diaryDays");
            addAction(response, 2, "Keep logging consistent on the days that usually get missed.", "Missing days reduce confidence and make AI coaching less personal.", "Higher quality weekly analysis.", "LOW", "loggingConsistency");
        }
        if (response.getTomorrowFocus() == null || response.getTomorrowFocus().isBlank()) {
            response.setTomorrowFocus("Start the next period with one simple, trackable habit rather than a full reset.");
        }
        if (response.getWatchOut() == null || response.getWatchOut().isBlank()) {
            response.setWatchOut("Do not overreact to one high or low day; weekly patterns matter more than isolated entries.");
        }
    }

    private void addFinding(AiInsightResponseDto response, String type, String label, String message, String evidence, String impact, String severity) {
        AiInsightResponseDto.KeyFinding finding = new AiInsightResponseDto.KeyFinding();
        finding.setType(type);
        finding.setLabel(label);
        finding.setMessage(message);
        finding.setEvidence(evidence);
        finding.setImpact(impact);
        finding.setSeverity(severity);
        response.getKeyFindings().add(finding);
    }

    private void addAction(AiInsightResponseDto response, int priority, String action, String reason, String expectedImpact, String effort, String linkedMetric) {
        AiInsightResponseDto.PersonalizedAction item = new AiInsightResponseDto.PersonalizedAction();
        item.setPriority(priority);
        item.setAction(action);
        item.setReason(reason);
        item.setExpectedImpact(expectedImpact);
        item.setEffort(effort);
        item.setLinkedMetric(linkedMetric);
        response.getPersonalizedActions().add(item);
    }

    private String calorieMessage(Double consumed, Double target) {
        if (consumed == null || target == null || target <= 0) {
            return "Calorie target comparison is limited because target or consumed calories are missing.";
        }
        double difference = consumed - target;
        double percent = Math.abs(difference) / target * 100.0;
        if (percent <= 5.0) {
            return "Calories were very close to target, which suggests controlled intake today.";
        }
        return difference > 0 ? "Calories were above target, so tomorrow should avoid overcorrecting and focus on portions." : "Calories were below target, so tomorrow should avoid unnecessary restriction.";
    }

    private String calorieEvidence(Double consumed, Double target) {
        if (consumed == null || target == null) {
            return "Calorie evidence unavailable.";
        }
        return "Consumed " + round(consumed) + " kcal vs target " + round(target) + " kcal.";
    }

    private String proteinMessage(Double proteinProgress) {
        if (proteinProgress == null) {
            return "Protein progress is not available for this insight.";
        }
        if (proteinProgress >= 120) {
            return "Protein was strong, so the next improvement is likely meal balance rather than more protein.";
        }
        if (proteinProgress < 80) {
            return "Protein appears below target, so tomorrow should include a clear protein anchor meal.";
        }
        return "Protein was in a useful range for today.";
    }

    private String nextFoodAction(Double proteinProgress, Integer qualityScore) {
        if (qualityScore != null && qualityScore < 70) {
            return "Add one fiber-rich food tomorrow, such as vegetables, legumes, oats, or fruit.";
        }
        if (proteinProgress != null && proteinProgress < 90) {
            return "Anchor one meal tomorrow around 25-30g protein instead of spreading protein randomly.";
        }
        if (proteinProgress != null && proteinProgress > 125) {
            return "Keep protein moderate tomorrow and focus on fiber, hydration, and portion balance.";
        }
        return "Repeat the pattern that worked today, then improve one small quality detail tomorrow.";
    }

    private String buildDataQualityNote(AiInsightResponseDto.DataCoverage coverage) {
        if (coverage.getMissingSignals() == null || coverage.getMissingSignals().isEmpty()) {
            return "This insight used the available logged data for the selected period.";
        }
        return "This insight used logged app data, but is limited by missing signals: " + String.join(", ", coverage.getMissingSignals()) + ".";
    }

    private String resolveConfidenceLabel(Double confidence, int missingSignalCount) {
        if (confidence != null && confidence >= 0.85 && missingSignalCount <= 1) {
            return "HIGH";
        }
        if (confidence != null && confidence < 0.6 || missingSignalCount >= 3) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private void addSignal(AiInsightResponseDto.DataCoverage coverage, String value) {
        if (!coverage.getSignalsUsed().contains(value)) {
            coverage.getSignalsUsed().add(value);
        }
    }

    private void addMissingSignal(AiInsightResponseDto.DataCoverage coverage, String value) {
        if (!coverage.getMissingSignals().contains(value)) {
            coverage.getMissingSignals().add(value);
        }
    }

    private Integer defaultInt(Integer current, Integer fallback) {
        return current == null ? fallback : current;
    }

    private Boolean defaultBoolean(Boolean current, Boolean fallback) {
        return current == null ? fallback : current;
    }

    private Boolean bool(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private String formatInt(Integer value) {
        return value == null ? "unknown" : value.toString();
    }

    private String formatDouble(Double value) {
        return value == null ? "unknown" : String.valueOf(round(value));
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

    private void copyUsageMetadata(AiUsageMetadataCarrier response, AiRequestHistoryEntity history) {
        if (response == null || history == null) {
            return;
        }
        history.setPromptTokens(response.getPromptTokens());
        history.setCompletionTokens(response.getCompletionTokens());
        Integer totalTokens = response.getTotalTokens();
        if (totalTokens == null && (response.getPromptTokens() != null || response.getCompletionTokens() != null)) {
            totalTokens = (response.getPromptTokens() == null ? 0 : response.getPromptTokens())
                    + (response.getCompletionTokens() == null ? 0 : response.getCompletionTokens());
        }
        history.setTotalTokens(totalTokens);
        history.setEstimatedCost(response.getEstimatedCost());
        history.setCostCurrency(response.getCostCurrency());
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

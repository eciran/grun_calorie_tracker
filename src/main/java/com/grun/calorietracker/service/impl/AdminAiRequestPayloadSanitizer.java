package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grun.calorietracker.enums.AiRequestType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminAiRequestPayloadSanitizer {

    private static final int MAX_DEPTH = 6;
    private static final int MAX_ARRAY_ITEMS = 30;
    private static final int MAX_OBJECT_FIELDS = 80;
    private static final int MAX_TEXT_LENGTH = 1_000;

    private static final Set<String> INPUT_FIELDS = Set.of(
            "requestType", "language", "locale", "marketRegion", "logDate", "mealType", "startDate", "endDate",
            "date", "dayCount", "mealsPerDay", "daysPerWeek", "minutesPerSession", "level", "generationMode",
            "budgetPreference", "preparationTimePreference", "includeRecipeSuggestions", "workoutPlanId",
            "workoutSessionCount", "equipmentCount", "focusAreaCount", "excludedExerciseCount", "goalLength",
            "hasLimitationNotes", "dietaryPreferenceCount", "excludedFoodCount", "preferredMealTimeCount",
            "profileAllergenCount", "availableIngredientCount", "excludedIngredientCount", "servingCount",
            "targetCaloriesPerServing", "promptLength", "transcriptLength", "imageReferenceType", "imageReferenceLength",
            "hasUserNote", "userNoteLength", "focus", "noteLength", "hasBackendContext"
    );

    private static final Set<String> COMMON_OUTPUT_FIELDS = Set.of(
            "resultType", "summary", "userMessage", "professionalSummary", "assumptions", "confidence", "qualityScore",
            "estimatedUncertainty", "reviewReasons", "warnings", "aiRemainingThisPeriod", "safety"
    );
    private static final Set<String> MEAL_OUTPUT_FIELDS = Set.of(
            "suggestedMealType", "suggestedLogDate", "items"
    );
    private static final Set<String> RECIPE_OUTPUT_FIELDS = Set.of(
            "suggestedIngredients", "estimatedNutritionPerServing"
    );
    private static final Set<String> NUTRITION_PLAN_OUTPUT_FIELDS = Set.of(
            "name", "startDate", "endDate", "dailyTarget", "days", "generationMode", "workoutPlanId", "workoutScheduleSummary"
    );
    private static final Set<String> WORKOUT_OUTPUT_FIELDS = Set.of(
            "name", "days", "trainingPrinciples"
    );
    private static final Set<String> INSIGHT_OUTPUT_FIELDS = Set.of(
            "title", "highlights", "keyFindings", "personalizedActions", "recommendedActions", "tomorrowFocus", "watchOut",
            "dataCoverage", "dataQualityNote", "linkedMetric", "priority", "category", "actionType", "ctaLabel", "ctaTarget"
    );
    private static final Set<String> CONFIRMATION_FIELDS = Set.of(
            "confirmed", "confirmedAt", "logDate", "mealType", "recipeId", "planId", "workoutPlanId", "itemCount",
            "dayCount", "servingCount", "status", "correctionsApplied", "correctionCount"
    );

    private static final Set<String> NESTED_FIELDS = Set.of(
            "id", "day", "date", "label", "name", "title", "description", "summary", "instruction", "step", "order",
            "mealType", "time", "servings", "servingCount", "portion", "portionSize", "portionUnit", "portionGrams", "quantity", "unit",
            "foodItemId", "recipeId", "exerciseId", "durationMinutes", "sets", "reps", "restSeconds", "intensity",
            "calories", "protein", "carbs", "fat", "fiber", "sugar", "sodium", "saturatedFat", "cholesterol",
            "estimatedCalories", "estimatedProtein", "estimatedCarbs", "estimatedFat", "estimatedNutrition",
            "potassium", "calcium", "iron", "magnesium", "zinc", "vitaminA", "vitaminB12", "vitaminC", "vitaminD",
            "vitaminE", "target", "actual", "value", "min", "max", "score", "confidence", "reason", "message",
            "category", "severity", "type", "action", "route", "muscleGroups", "equipment", "ingredients", "steps",
            "meals", "exercises", "alternatives", "tips", "warnings", "items", "days", "nutrition", "macros",
            "micronutrients", "totals", "perServing", "schedule", "sessions", "highlights", "recommendations"
    );

    private static final Set<String> BLOCKED_KEYS = Set.of(
            "prompt", "systemprompt", "rawprompt", "transcript", "note", "usernote", "imagereference", "imageurl",
            "signedurl", "apikey", "secret", "password", "authorization", "accesstoken", "refreshtoken", "idempotencykey"
    );

    private final ObjectMapper objectMapper;

    JsonNode sanitizeInput(String payload) {
        return sanitize(payload, INPUT_FIELDS);
    }

    JsonNode sanitizeOutput(String payload, AiRequestType requestType) {
        Set<String> fields = new java.util.HashSet<>(COMMON_OUTPUT_FIELDS);
        if (requestType == AiRequestType.PHOTO_MEAL_LOG || requestType == AiRequestType.VOICE_FOOD_LOG) {
            fields.addAll(MEAL_OUTPUT_FIELDS);
        } else if (requestType == AiRequestType.AI_RECIPE_GENERATION || requestType == AiRequestType.AI_MEAL_PREPARATION_GUIDE) {
            fields.addAll(RECIPE_OUTPUT_FIELDS);
            fields.remove("warnings");
        } else if (requestType == AiRequestType.AI_NUTRITION_PLAN) {
            fields.addAll(NUTRITION_PLAN_OUTPUT_FIELDS);
        } else if (requestType == AiRequestType.AI_WORKOUT_PLAN) {
            fields.addAll(WORKOUT_OUTPUT_FIELDS);
        } else if (requestType == AiRequestType.AI_DAILY_INSIGHT || requestType == AiRequestType.AI_WEEKLY_INSIGHT) {
            fields.addAll(INSIGHT_OUTPUT_FIELDS);
        }
        return sanitize(payload, fields);
    }

    JsonNode sanitizeConfirmation(String payload) {
        return sanitize(payload, CONFIRMATION_FIELDS);
    }

    String sanitizeText(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("(?i)(bearer\\s+)[a-z0-9._-]+", "$1[REDACTED]")
                .replaceAll("(?i)https?://\\S+", "[URL REDACTED]")
                .replaceAll("(?i)(api[_-]?key|secret|password|token)\\s*[:=]\\s*\\S+", "$1=[REDACTED]");
        return normalized.length() <= MAX_TEXT_LENGTH ? normalized : normalized.substring(0, MAX_TEXT_LENGTH) + "...";
    }

    private JsonNode sanitize(String payload, Set<String> rootFields) {
        ObjectNode empty = objectMapper.createObjectNode();
        if (payload == null || payload.isBlank()) return empty;
        try {
            JsonNode sanitized = sanitizeNode(objectMapper.readTree(payload), rootFields, true, 0);
            return sanitized == null ? empty : sanitized;
        } catch (JsonProcessingException ignored) {
            return empty;
        }
    }

    private JsonNode sanitizeNode(JsonNode node, Set<String> allowedFields, boolean root, int depth) {
        if (node == null || node.isNull() || depth > MAX_DEPTH) return null;
        if (node.isValueNode()) {
            if (node.isTextual()) return objectMapper.getNodeFactory().textNode(sanitizeText(node.asText()));
            return node.deepCopy();
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            int count = 0;
            for (JsonNode item : node) {
                if (count++ >= MAX_ARRAY_ITEMS) break;
                JsonNode sanitized = sanitizeNode(item, NESTED_FIELDS, false, depth + 1);
                if (sanitized != null) result.add(sanitized);
            }
            return result;
        }
        if (!node.isObject()) return null;
        ObjectNode result = objectMapper.createObjectNode();
        int[] count = {0};
        node.fields().forEachRemaining(entry -> {
            if (count[0] >= MAX_OBJECT_FIELDS || blocked(entry.getKey())) return;
            Set<String> fields = root ? allowedFields : NESTED_FIELDS;
            if (!fields.contains(entry.getKey())) return;
            JsonNode sanitized = sanitizeNode(entry.getValue(), NESTED_FIELDS, false, depth + 1);
            if (sanitized != null) {
                result.set(entry.getKey(), sanitized);
                count[0]++;
            }
        });
        return result;
    }

    private boolean blocked(String key) {
        return BLOCKED_KEYS.contains(key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT));
    }
}
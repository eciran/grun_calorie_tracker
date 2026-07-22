package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AiMealDraftAlternativeCandidateDto;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiMealDraftResponseValidator;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class AiMealDraftResponseValidatorImpl implements AiMealDraftResponseValidator {

    private static final int MAX_ITEMS = 20;

    @Override
    public AiMealDraftResponseDto validateAndNormalize(AiMealDraftResponseDto response,
                                                       AiRequestType expectedType,
                                                       AiProvider expectedProvider,
                                                       String expectedModel) {
        if (response == null) {
            throw new IllegalArgumentException("AI provider returned an empty response.");
        }
        response.setRequestType(expectedType);
        response.setProvider(expectedProvider);
        response.setModel(expectedModel);
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        if (response.getSuggestedLogDate() == null) {
            response.setSuggestedLogDate(LocalDateTime.now());
        }
        if (isBlank(response.getSuggestedMealType())) {
            response.setSuggestedMealType("SNACK");
        } else {
            response.setSuggestedMealType(response.getSuggestedMealType().trim().toUpperCase());
        }
        validateItems(response.getItems());
        if (expectedType == AiRequestType.PHOTO_MEAL_LOG) {
            normalizeCountablePhotoPortions(response.getItems());
            response.setItems(mergeDuplicatePhotoItems(response.getItems()));
        }
        normalizeQuality(response);
        return response;
    }

    private void validateItems(List<AiMealDraftItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("AI provider returned no meal draft items.");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("AI provider returned too many meal draft items.");
        }
        for (AiMealDraftItemDto item : items) {
            validateItem(item);
        }
    }

    private void validateItem(AiMealDraftItemDto item) {
        if (item == null || isBlank(item.getName())) {
            throw new IllegalArgumentException("AI provider returned an unnamed meal draft item.");
        }
        if (item.getQuantity() != null && item.getQuantity() <= 0) {
            throw new IllegalArgumentException("AI provider returned a non-positive meal draft quantity.");
        }
        if (item.getDetectedPieceCount() != null && item.getDetectedPieceCount() <= 0) {
            throw new IllegalArgumentException("AI provider returned a non-positive detected piece count.");
        }
        if (item.getEstimatedTotalWeightGrams() != null && item.getEstimatedTotalWeightGrams() <= 0) {
            throw new IllegalArgumentException("AI provider returned a non-positive estimated total weight.");
        }
        if (item.getConfidence() != null && (item.getConfidence() < 0 || item.getConfidence() > 1)) {
            throw new IllegalArgumentException("AI provider returned confidence outside the 0-1 range.");
        }
        normalizeNutrition(item);
        item.setName(normalizeFoodDisplayName(item.getName()));
        if (item.getUnit() != null) {
            item.setUnit(item.getUnit().trim());
        }
        if (item.getDetectedPieceCount() == null && isPieceUnit(item.getUnit()) && isWholeNumber(item.getQuantity())) {
            item.setDetectedPieceCount(item.getQuantity().intValue());
        }
        if (item.getPortionEstimateMethod() == null || item.getPortionEstimateMethod().isBlank()) {
            item.setPortionEstimateMethod("UNKNOWN");
        }
        if (item.getNeedsUserPortionConfirmation() == null) {
            item.setNeedsUserPortionConfirmation(item.getQuantity() == null || item.getUnit() == null || item.getUnit().isBlank() || requiresReview(item));
        }
        if (isBlank(item.getReasoning())) {
            item.setReasoning("Estimated from the provided meal input and kept as an editable AI snapshot.");
        }
        if (isBlank(item.getPortionNote())) {
            item.setPortionNote(Boolean.TRUE.equals(item.getNeedsUserPortionConfirmation())
                    ? "Confirm the portion before saving; calories and macros depend on the final amount."
                    : "Portion appears usable, but you can adjust it before saving.");
        }
        item.setAlternativeMatchNames(normalizeAlternativeNames(item.getAlternativeMatchNames()));
        item.setAlternativeCandidates(normalizeAlternativeCandidates(item));
    }

    private List<AiMealDraftAlternativeCandidateDto> normalizeAlternativeCandidates(AiMealDraftItemDto primary) {
        if (primary.getAlternativeCandidates() == null || primary.getAlternativeCandidates().isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, AiMealDraftAlternativeCandidateDto> valid = new LinkedHashMap<>();
        String primaryName = primary.getName().trim().toLowerCase(Locale.ROOT);
        for (AiMealDraftAlternativeCandidateDto candidate : primary.getAlternativeCandidates()) {
            if (valid.size() >= 2 || !isUsableAlternative(candidate)) {
                continue;
            }
            String normalizedName = normalizeFoodDisplayName(candidate.getName());
            String key = normalizedName.toLowerCase(Locale.ROOT);
            if (key.equals(primaryName) || valid.containsKey(key)) {
                continue;
            }
            candidate.setName(normalizedName);
            candidate.setUnit(canonicalUnit(candidate.getUnit()));
            if (candidate.getDetectedPieceCount() != null
                    && candidate.getEstimatedTotalWeightGrams() != null
                    && isGramUnit(candidate.getUnit())) {
                candidate.setQuantity(candidate.getDetectedPieceCount().doubleValue());
                candidate.setUnit("PIECE");
            }
            valid.put(key, candidate);
        }
        return new ArrayList<>(valid.values());
    }

    private boolean isUsableAlternative(AiMealDraftAlternativeCandidateDto candidate) {
        if (candidate == null || isBlank(candidate.getName())
                || !Boolean.TRUE.equals(candidate.getMateriallyDifferent())
                || candidate.getQuantity() == null || !Double.isFinite(candidate.getQuantity())
                || candidate.getQuantity() <= 0 || isBlank(candidate.getUnit())
                || candidate.getEstimatedNutrition() == null
                || candidate.getConfidence() == null || !Double.isFinite(candidate.getConfidence())
                || candidate.getConfidence() < 0 || candidate.getConfidence() > 1) {
            return false;
        }
        try {
            validateRequiredNutrition(candidate.getEstimatedNutrition());
            validateOptionalNutrition(candidate.getEstimatedNutrition());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
    private List<AiMealDraftItemDto> mergeDuplicatePhotoItems(List<AiMealDraftItemDto> items) {
        Map<String, AiMealDraftItemDto> grouped = new LinkedHashMap<>();
        for (AiMealDraftItemDto item : items) {
            String key = duplicateKey(item);
            AiMealDraftItemDto existing = grouped.get(key);
            if (existing == null) {
                grouped.put(key, item);
            } else {
                mergePhotoItem(existing, item);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private void normalizeCountablePhotoPortions(List<AiMealDraftItemDto> items) {
        for (AiMealDraftItemDto item : items) {
            if (item.getDetectedPieceCount() == null
                    || item.getDetectedPieceCount() <= 0
                    || item.getEstimatedTotalWeightGrams() == null
                    || !isGramUnit(item.getUnit())) {
                continue;
            }
            item.setQuantity(item.getDetectedPieceCount().doubleValue());
            item.setUnit("PIECE");
            item.setNeedsUserPortionConfirmation(true);
            item.setPortionNote(item.getDetectedPieceCount()
                    + " visible pieces grouped as one item (estimated total weight "
                    + Math.round(item.getEstimatedTotalWeightGrams())
                    + " g). Confirm the piece count before saving.");
        }
    }

    private String duplicateKey(AiMealDraftItemDto item) {
        String name = item.getName().trim().toLowerCase(Locale.ROOT);
        String unit = canonicalUnit(item.getUnit());
        String matchedId = item.getMatchedFoodItemId() == null ? "unmatched" : item.getMatchedFoodItemId().toString();
        return name + "|" + unit + "|" + matchedId;
    }

    private void mergePhotoItem(AiMealDraftItemDto target, AiMealDraftItemDto source) {
        target.setQuantity(sumNullable(target.getQuantity(), source.getQuantity()));
        target.setDetectedPieceCount(sumNullable(target.getDetectedPieceCount(), source.getDetectedPieceCount()));
        target.setEstimatedTotalWeightGrams(sumNullable(
                target.getEstimatedTotalWeightGrams(), source.getEstimatedTotalWeightGrams()));
        target.setEstimatedNutrition(sumNutrition(target.getEstimatedNutrition(), source.getEstimatedNutrition()));
        target.setEstimatedCalories(target.getEstimatedNutrition().getCalories());
        target.setEstimatedProtein(target.getEstimatedNutrition().getProtein());
        target.setEstimatedCarbs(target.getEstimatedNutrition().getCarbs());
        target.setEstimatedFat(target.getEstimatedNutrition().getFat());
        target.setConfidence(minNullable(target.getConfidence(), source.getConfidence()));
        target.setReviewRequired(Boolean.TRUE.equals(target.getReviewRequired())
                || Boolean.TRUE.equals(source.getReviewRequired()));
        target.setNeedsUserPortionConfirmation(true);
        target.setPortionEstimateMethod("GROUPED_VISUAL_ESTIMATE");
        target.setVisibleInPhoto(Boolean.TRUE.equals(target.getVisibleInPhoto())
                || Boolean.TRUE.equals(source.getVisibleInPhoto()));
        target.setAlternativeMatchNames(mergeAlternativeNames(
                target.getAlternativeMatchNames(), source.getAlternativeMatchNames()));
        target.setNutritionEstimateNote(
                "Nutrition values represent the combined estimate for visually identical grouped pieces.");
        target.setPortionNote(groupedPortionNote(target));
    }

    private RecipeNutritionDto sumNutrition(RecipeNutritionDto left, RecipeNutritionDto right) {
        RecipeNutritionDto total = new RecipeNutritionDto();
        total.setCalories(sumRequired(left.getCalories(), right.getCalories()));
        total.setProtein(sumRequired(left.getProtein(), right.getProtein()));
        total.setCarbs(sumRequired(left.getCarbs(), right.getCarbs()));
        total.setFat(sumRequired(left.getFat(), right.getFat()));
        total.setFiber(sumOptional(left.getFiber(), right.getFiber()));
        total.setSugar(sumOptional(left.getSugar(), right.getSugar()));
        total.setSaturatedFat(sumOptional(left.getSaturatedFat(), right.getSaturatedFat()));
        total.setSodium(sumOptional(left.getSodium(), right.getSodium()));
        total.setPotassium(sumOptional(left.getPotassium(), right.getPotassium()));
        total.setCholesterol(sumOptional(left.getCholesterol(), right.getCholesterol()));
        total.setCalcium(sumOptional(left.getCalcium(), right.getCalcium()));
        total.setIron(sumOptional(left.getIron(), right.getIron()));
        total.setMagnesium(sumOptional(left.getMagnesium(), right.getMagnesium()));
        total.setZinc(sumOptional(left.getZinc(), right.getZinc()));
        total.setVitaminA(sumOptional(left.getVitaminA(), right.getVitaminA()));
        total.setVitaminC(sumOptional(left.getVitaminC(), right.getVitaminC()));
        total.setVitaminD(sumOptional(left.getVitaminD(), right.getVitaminD()));
        total.setVitaminE(sumOptional(left.getVitaminE(), right.getVitaminE()));
        total.setVitaminB12(sumOptional(left.getVitaminB12(), right.getVitaminB12()));
        return total;
    }

    private List<String> mergeAlternativeNames(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return merged.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String groupedPortionNote(AiMealDraftItemDto item) {
        String count = item.getDetectedPieceCount() == null
                ? "Similar pieces were"
                : item.getDetectedPieceCount() + " similar pieces were";
        if (item.getEstimatedTotalWeightGrams() != null) {
            return count + " grouped into one item with an estimated total weight of "
                    + Math.round(item.getEstimatedTotalWeightGrams())
                    + " g. Confirm the total weight before saving.";
        }
        return count + " grouped into one item. Confirm the combined portion before saving.";
    }

    private String canonicalUnit(String unit) {
        return unit == null ? "" : unit.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isPieceUnit(String unit) {
        String normalized = canonicalUnit(unit);
        return normalized.equals("PIECE")
                || normalized.equals("PIECES")
                || normalized.equals("PC")
                || normalized.equals("PCS");
    }

    private boolean isGramUnit(String unit) {
        String normalized = canonicalUnit(unit);
        return normalized.equals("GRAM") || normalized.equals("GRAMS") || normalized.equals("G");
    }

    private boolean isWholeNumber(Double value) {
        return value != null
                && Double.isFinite(value)
                && value <= Integer.MAX_VALUE
                && Math.rint(value) == value;
    }

    private Double sumRequired(Double left, Double right) {
        return left + right;
    }

    private Double sumOptional(Double left, Double right) {
        return left == null || right == null ? null : left + right;
    }

    private Double sumNullable(Double left, Double right) {
        return left == null || right == null ? null : left + right;
    }

    private Integer sumNullable(Integer left, Integer right) {
        return left == null || right == null ? null : left + right;
    }

    private Double minNullable(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.min(left, right);
    }
    private void normalizeNutrition(AiMealDraftItemDto item) {
        RecipeNutritionDto nutrition = item.getEstimatedNutrition();
        if (nutrition == null) {
            nutrition = new RecipeNutritionDto();
        }
        nutrition.setCalories(firstNonNull(nutrition.getCalories(), item.getEstimatedCalories()));
        nutrition.setProtein(firstNonNull(nutrition.getProtein(), item.getEstimatedProtein()));
        nutrition.setCarbs(firstNonNull(nutrition.getCarbs(), item.getEstimatedCarbs()));
        nutrition.setFat(firstNonNull(nutrition.getFat(), item.getEstimatedFat()));

        validateRequiredNutrition(nutrition);
        validateOptionalNutrition(nutrition);

        item.setEstimatedNutrition(nutrition);
        item.setEstimatedCalories(nutrition.getCalories());
        item.setEstimatedProtein(nutrition.getProtein());
        item.setEstimatedCarbs(nutrition.getCarbs());
        item.setEstimatedFat(nutrition.getFat());
        if (isBlank(item.getNutritionEstimateNote())) {
            item.setNutritionEstimateNote("Nutrition values are AI estimates for the detected portion; micronutrients may be unavailable when evidence is limited.");
        }
    }

    private void validateRequiredNutrition(RecipeNutritionDto nutrition) {
        validateNutritionValue(nutrition.getCalories(), "calories", true);
        validateNutritionValue(nutrition.getProtein(), "protein", true);
        validateNutritionValue(nutrition.getCarbs(), "carbohydrate", true);
        validateNutritionValue(nutrition.getFat(), "fat", true);
    }

    private void validateOptionalNutrition(RecipeNutritionDto nutrition) {
        validateNutritionValue(nutrition.getFiber(), "fiber", false);
        validateNutritionValue(nutrition.getSugar(), "sugar", false);
        validateNutritionValue(nutrition.getSaturatedFat(), "saturated fat", false);
        validateNutritionValue(nutrition.getSodium(), "sodium", false);
        validateNutritionValue(nutrition.getPotassium(), "potassium", false);
        validateNutritionValue(nutrition.getCholesterol(), "cholesterol", false);
        validateNutritionValue(nutrition.getCalcium(), "calcium", false);
        validateNutritionValue(nutrition.getIron(), "iron", false);
        validateNutritionValue(nutrition.getMagnesium(), "magnesium", false);
        validateNutritionValue(nutrition.getZinc(), "zinc", false);
        validateNutritionValue(nutrition.getVitaminA(), "vitamin A", false);
        validateNutritionValue(nutrition.getVitaminC(), "vitamin C", false);
        validateNutritionValue(nutrition.getVitaminD(), "vitamin D", false);
        validateNutritionValue(nutrition.getVitaminE(), "vitamin E", false);
        validateNutritionValue(nutrition.getVitaminB12(), "vitamin B12", false);
    }

    private void validateNutritionValue(Double value, String field, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("AI provider returned no estimated " + field + ".");
            }
            return;
        }
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("AI provider returned invalid estimated " + field + ".");
        }
    }

    private Double firstNonNull(Double preferred, Double fallback) {
        return preferred != null ? preferred : fallback;
    }

    private List<String> normalizeAlternativeNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .map(this::normalizeFoodDisplayName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizeFoodDisplayName(String value) {
        return FoodProductNormalizationRules.normalizeProductDisplayName(value);
    }

    private void normalizeQuality(AiMealDraftResponseDto response) {
        response.setSchemaVersion("ai_response_v3");
        if (response.getReviewReasons() == null) {
            response.setReviewReasons(List.of());
        }
        if (response.getAssumptions() == null) {
            response.setAssumptions(List.of());
        }
        if (response.getNextBestActions() == null) {
            response.setNextBestActions(List.of());
        }
        if (isBlank(response.getResultType())) {
            response.setResultType("AI_SNAPSHOT");
        }
        if (isBlank(response.getUserMessage())) {
            response.setUserMessage("AI prepared an editable meal estimate. Review portions before adding it to your diary.");
        }
        if (isBlank(response.getProfessionalSummary())) {
            response.setProfessionalSummary(response.getSummary());
        }
        if (response.getConfidence() == null) {
            response.setConfidence(minConfidence(response.getItems()));
        }
        if (response.getQualityScore() == null && response.getConfidence() != null) {
            response.setQualityScore((int) Math.round(response.getConfidence() * 100));
        }
        if (response.getEstimatedUncertainty() == null || response.getEstimatedUncertainty().isBlank()) {
            response.setEstimatedUncertainty(resolveUncertainty(response.getConfidence()));
        }
    }

    private Double minConfidence(List<AiMealDraftItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .map(AiMealDraftItemDto::getConfidence)
                .filter(value -> value != null)
                .min(Double::compareTo)
                .orElse(null);
    }

    private String resolveUncertainty(Double confidence) {
        if (confidence == null || confidence < 0.55) {
            return "HIGH";
        }
        if (confidence < 0.8) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean requiresReview(AiMealDraftItemDto item) {
        return Boolean.TRUE.equals(item.getReviewRequired()) || item.getConfidence() == null || item.getConfidence() < 0.75;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftAlternativeCandidateDto;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.impl.AiMealDraftResponseValidatorImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMealDraftResponseValidatorImplTest {

    private final AiMealDraftResponseValidatorImpl validator = new AiMealDraftResponseValidatorImpl();

    @Test
    void validateAndNormalize_setsExpectedMetadataAndDefaults() {
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setSuggestedMealType(" lunch ");
        response.setItems(List.of(item()));

        AiMealDraftResponseDto result = validator.validateAndNormalize(
                response,
                AiRequestType.VOICE_FOOD_LOG,
                AiProvider.HTTP_JSON,
                "provider-model-v1"
        );

        assertEquals(AiRequestType.VOICE_FOOD_LOG, result.getRequestType());
        assertEquals(AiProvider.HTTP_JSON, result.getProvider());
        assertEquals("provider-model-v1", result.getModel());
        assertEquals(AiRequestStatus.DRAFT_CREATED, result.getStatus());
        assertEquals("LUNCH", result.getSuggestedMealType());
        assertNotNull(result.getSuggestedLogDate());
        assertEquals("Tomatoes", result.getItems().get(0).getName());
        assertEquals(List.of("Cherry Tomatoes", "Tomatoes"), result.getItems().get(0).getAlternativeMatchNames());
        assertEquals(220.0, result.getItems().get(0).getEstimatedNutrition().getPotassium());
        assertEquals(18.0, result.getItems().get(0).getEstimatedCalories());
        assertNotNull(result.getItems().get(0).getNutritionEstimateNote());
    }

    @Test
    void validateAndNormalize_whenItemsEmpty_throws() {
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setItems(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndNormalize(response, AiRequestType.VOICE_FOOD_LOG, AiProvider.LOG, "log"));
    }

    @Test
    void validateAndNormalize_whenConfidenceInvalid_throws() {
        AiMealDraftItemDto item = item();
        item.setConfidence(1.5);
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setItems(List.of(item));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndNormalize(response, AiRequestType.VOICE_FOOD_LOG, AiProvider.LOG, "log"));
    }

    @Test
    void validateAndNormalize_whenMicronutrientIsNegative_throws() {
        AiMealDraftItemDto item = item();
        item.getEstimatedNutrition().setSodium(-1.0);
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setItems(List.of(item));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndNormalize(response, AiRequestType.PHOTO_MEAL_LOG, AiProvider.OPENAI, "model"));
    }

    @Test
    void validateAndNormalize_whenPhotoContainsDuplicatePreparedFood_groupsPiecesAndNutrition() {
        AiMealDraftItemDto first = chickenItem(281.0, 53.0, 180.0, 0.84);
        AiMealDraftItemDto second = chickenItem(315.0, 59.0, 200.0, 0.82);
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setItems(List.of(first, second));

        AiMealDraftResponseDto result = validator.validateAndNormalize(
                response, AiRequestType.PHOTO_MEAL_LOG, AiProvider.OPENAI, "model");

        assertEquals(1, result.getItems().size());
        AiMealDraftItemDto grouped = result.getItems().get(0);
        assertEquals(2.0, grouped.getQuantity());
        assertEquals(2, grouped.getDetectedPieceCount());
        assertEquals(380.0, grouped.getEstimatedTotalWeightGrams());
        assertEquals(596.0, grouped.getEstimatedNutrition().getCalories());
        assertEquals(112.0, grouped.getEstimatedNutrition().getProtein());
        assertEquals(1100.0, grouped.getEstimatedNutrition().getSodium());
        assertEquals(0.82, grouped.getConfidence());
    }
    @Test
    void validateAndNormalize_whenPhotoHasCountableFoodInGrams_usesPiecesForPrimaryDisplay() {
        AiMealDraftItemDto chicken = chickenItem(594.0, 35.2, 220.0, 0.74);
        chicken.setName("Breaded chicken pieces");
        chicken.setQuantity(220.0);
        chicken.setUnit("GRAM");
        chicken.setDetectedPieceCount(10);
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setItems(List.of(chicken));

        AiMealDraftResponseDto result = validator.validateAndNormalize(
                response, AiRequestType.PHOTO_MEAL_LOG, AiProvider.OPENAI, "model");

        AiMealDraftItemDto normalized = result.getItems().get(0);
        assertEquals(10.0, normalized.getQuantity());
        assertEquals("PIECE", normalized.getUnit());
        assertEquals(10, normalized.getDetectedPieceCount());
        assertEquals(220.0, normalized.getEstimatedTotalWeightGrams());
        assertEquals(594.0, normalized.getEstimatedNutrition().getCalories());
        assertEquals(true, normalized.getPortionNote().contains("estimated total weight 220 g"));
    }

    @Test
    void validateAndNormalize_whenAlternativeIsComplete_retainsAndNormalizesIt() {
        AiMealDraftItemDto chicken = chickenItem(430.0, 80.0, 260.0, 0.84);
        AiMealDraftAlternativeCandidateDto alternative = alternative(" chicken thigh ", 540.0, true);
        alternative.setQuantity(260.0);
        alternative.setUnit("g");
        alternative.setDetectedPieceCount(2);
        alternative.setEstimatedTotalWeightGrams(260.0);
        chicken.setAlternativeCandidates(List.of(alternative));
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setItems(List.of(chicken));

        AiMealDraftResponseDto result = validator.validateAndNormalize(
                response, AiRequestType.PHOTO_MEAL_LOG, AiProvider.OPENAI, "model");

        AiMealDraftAlternativeCandidateDto normalized = result.getItems().get(0).getAlternativeCandidates().get(0);
        assertEquals("Chicken Thigh", normalized.getName());
        assertEquals("PIECE", normalized.getUnit());
        assertEquals(2.0, normalized.getQuantity());
        assertEquals(260.0, normalized.getEstimatedTotalWeightGrams());
    }

    @Test
    void validateAndNormalize_whenAlternativeIsCosmeticOrIncomplete_discardsIt() {
        AiMealDraftItemDto chicken = chickenItem(430.0, 80.0, 260.0, 0.84);
        chicken.setAlternativeCandidates(List.of(
                alternative("Cooked chicken breast, herb-seasoned", 430.0, true),
                alternative("Chicken Thigh", 540.0, false),
                alternative("Turkey Breast", null, true)));
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setItems(List.of(chicken));

        AiMealDraftResponseDto result = validator.validateAndNormalize(
                response, AiRequestType.PHOTO_MEAL_LOG, AiProvider.OPENAI, "model");

        assertTrue(result.getItems().get(0).getAlternativeCandidates().isEmpty());
    }
    private AiMealDraftItemDto item() {
        AiMealDraftItemDto item = new AiMealDraftItemDto();
        item.setName(" tomatoes ");
        item.setAlternativeMatchNames(List.of(" cherry tomatoes ", "tomatoes"));
        item.setQuantity(100.0);
        item.setUnit(" g ");
        item.setEstimatedCalories(18.0);
        item.setEstimatedProtein(0.9);
        item.setEstimatedCarbs(3.9);
        item.setEstimatedFat(0.2);
        item.setEstimatedNutrition(new RecipeNutritionDto(
                18.0, 0.9, 3.9, 0.2, 1.2, 2.6, 0.0,
                5.0, 220.0, 0.0, 10.0, 0.3, 11.0, 0.2,
                42.0, 14.0, 0.0, 0.5, 0.0
        ));
        item.setConfidence(0.5);
        return item;
    }

    private AiMealDraftItemDto chickenItem(double calories, double protein, double weightGrams, double confidence) {
        AiMealDraftItemDto item = new AiMealDraftItemDto();
        item.setName("Cooked chicken breast, herb-seasoned");
        item.setQuantity(1.0);
        item.setUnit("PIECE");
        item.setDetectedPieceCount(1);
        item.setEstimatedTotalWeightGrams(weightGrams);
        item.setEstimatedNutrition(new RecipeNutritionDto(
                calories, protein, 1.0, 7.0, 0.0, 0.0, 2.0,
                550.0, 500.0, 120.0, 20.0, 1.0, 40.0, 1.5,
                10.0, 0.0, 0.2, 0.5, 0.8
        ));
        item.setConfidence(confidence);
        item.setVisibleInPhoto(true);
        item.setNeedsUserPortionConfirmation(true);
        return item;
    }

    private AiMealDraftAlternativeCandidateDto alternative(String name, Double calories, boolean materiallyDifferent) {
        AiMealDraftAlternativeCandidateDto candidate = new AiMealDraftAlternativeCandidateDto();
        candidate.setName(name);
        candidate.setQuantity(2.0);
        candidate.setUnit("PIECE");
        candidate.setDetectedPieceCount(2);
        candidate.setEstimatedTotalWeightGrams(260.0);
        if (calories != null) {
            candidate.setEstimatedNutrition(new RecipeNutritionDto(
                    calories, 65.0, 1.0, 30.0, 0.0, 0.0, 8.0,
                    460.0, 620.0, 250.0, 30.0, 2.5, 60.0, 4.0,
                    25.0, 0.0, 0.3, 1.5, 1.1));
        }
        candidate.setNutritionEstimateNote("Alternative estimate.");
        candidate.setMatchReason("Visually plausible.");
        candidate.setConfidence(0.62);
        candidate.setMateriallyDifferent(materiallyDifferent);
        return candidate;
    }}

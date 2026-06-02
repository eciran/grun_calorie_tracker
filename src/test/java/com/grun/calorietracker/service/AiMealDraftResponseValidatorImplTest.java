package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.impl.AiMealDraftResponseValidatorImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private AiMealDraftItemDto item() {
        AiMealDraftItemDto item = new AiMealDraftItemDto();
        item.setName(" Chicken rice ");
        item.setQuantity(100.0);
        item.setUnit(" g ");
        item.setConfidence(0.5);
        return item;
    }
}

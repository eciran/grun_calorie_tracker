package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiMealDraftResponseValidator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
        if (item.getConfidence() != null && (item.getConfidence() < 0 || item.getConfidence() > 1)) {
            throw new IllegalArgumentException("AI provider returned confidence outside the 0-1 range.");
        }
        item.setName(item.getName().trim());
        if (item.getUnit() != null) {
            item.setUnit(item.getUnit().trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

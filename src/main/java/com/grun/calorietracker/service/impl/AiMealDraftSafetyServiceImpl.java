package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiSafetyResultDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiMealDraftSafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiMealDraftSafetyServiceImpl implements AiMealDraftSafetyService {

    private static final String PURPOSE_BLOCKED = "AI meal logging only supports food diary draft creation.";
    private static final Set<String> SENSITIVE_TERMS = Set.of(
            "diagnose", "diagnosis", "treatment", "prescription", "medication", "medicine",
            "eating disorder", "anorexia", "bulimia", "starve", "starvation",
            "tedavi", "teshis", "tani", "ilac", "recete",
            "yeme bozuklugu", "anoreksi", "bulimi", "ac kal"
    );

    private final AiProperties properties;

    @Override
    public void validateVoiceRequest(AiVoiceFoodDraftRequestDto request) {
        if (!properties.getSafety().isEnabled()) {
            return;
        }
        String text = normalize(request.getTranscript());
        if (containsSensitiveTerms(text)) {
            throw new IllegalArgumentException(PURPOSE_BLOCKED);
        }
    }

    @Override
    public void validatePhotoRequest(AiPhotoMealDraftRequestDto request) {
        validatePhotoReference(request.getImageReference());
        if (!properties.getSafety().isEnabled()) {
            return;
        }
        String text = normalize(request.getUserNote());
        if (containsSensitiveTerms(text)) {
            throw new IllegalArgumentException(PURPOSE_BLOCKED);
        }
    }

    @Override
    public AiSafetyResultDto reviewProviderResponse(AiMealDraftResponseDto response, AiRequestType requestType) {
        if (!properties.getSafety().isEnabled()) {
            return AiSafetyResultDto.clear();
        }

        AiSafetyResultDto result = AiSafetyResultDto.clear();
        double totalCalories = 0.0;

        if (response.getItems() != null) {
            for (AiMealDraftItemDto item : response.getItems()) {
                Double itemCalories = item.getEstimatedCalories();
                if (itemCalories == null) {
                    result.addReviewReason("MISSING_CALORIE_ESTIMATE");
                    item.setSafetyWarning("MISSING_CALORIE_ESTIMATE");
                    item.setReviewRequired(true);
                    continue;
                }
                totalCalories += itemCalories;
                if (itemCalories > properties.getSafety().getMaxItemCalories()) {
                    result.addReviewReason("EXTREME_ITEM_CALORIE_ESTIMATE");
                    item.setSafetyWarning("EXTREME_ITEM_CALORIE_ESTIMATE");
                    item.setReviewRequired(true);
                }
            }
        }

        if (totalCalories > properties.getSafety().getMaxTotalCalories()) {
            result.addReviewReason("EXTREME_TOTAL_CALORIE_ESTIMATE");
        }

        return result;
    }

    private void validatePhotoReference(String imageReference) {
        if (imageReference == null || imageReference.isBlank()) {
            throw new IllegalArgumentException("Image reference is required.");
        }
        String trimmed = imageReference.trim();
        if (trimmed.length() > properties.getPhoto().getMaxImageReferenceLength()) {
            throw new IllegalArgumentException("Image reference is too long.");
        }

        Set<String> allowedPrefixes = Arrays.stream(properties.getPhoto().getAllowedReferencePrefixes().split(","))
                .map(String::trim)
                .filter(prefix -> !prefix.isBlank())
                .collect(Collectors.toSet());
        boolean allowed = allowedPrefixes.stream()
                .anyMatch(prefix -> trimmed.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new IllegalArgumentException("Image reference must use an approved storage prefix.");
        }
    }

    private boolean containsSensitiveTerms(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return SENSITIVE_TERMS.stream().anyMatch(text::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

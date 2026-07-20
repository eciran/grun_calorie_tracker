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

import java.net.URI;
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
        boolean allowed = isManagedPhotoReference(trimmed) || allowedPrefixes.stream()
                .filter(prefix -> !isGenericWebPrefix(prefix))
                .anyMatch(prefix -> trimmed.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new IllegalArgumentException("Image reference must use an approved storage prefix.");
        }
    }

    private boolean isManagedPhotoReference(String imageReference) {
        try {
            URI reference = URI.create(imageReference);
            URI publicBase = URI.create(properties.getPhoto().getPublicBaseUrl());
            if (!equalsIgnoreCase(reference.getScheme(), publicBase.getScheme())
                    || !equalsIgnoreCase(reference.getHost(), publicBase.getHost())
                    || effectivePort(reference) != effectivePort(publicBase)
                    || reference.getRawQuery() != null
                    || reference.getRawFragment() != null) {
                return false;
            }
            String basePath = publicBase.getPath() == null ? "" : publicBase.getPath().replaceAll("/+$", "");
            String requiredPath = basePath + "/api/v1/ai/meal-drafts/photo-references/";
            String referencePath = reference.getPath();
            return referencePath != null && referencePath.startsWith(requiredPath);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isGenericWebPrefix(String prefix) {
        String normalized = prefix.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("https://") || normalized.equals("http://");
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
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

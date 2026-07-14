package com.grun.calorietracker.service;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.service.impl.AiMealDraftSafetyServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiMealDraftSafetyServiceImplTest {

    @Test
    void validatePhotoRequest_whenReferenceIsManagedUpload_accepts() {
        AiMealDraftSafetyServiceImpl service = new AiMealDraftSafetyServiceImpl(properties());
        AiPhotoMealDraftRequestDto request = request(
                "https://api.grun.test/api/v1/ai/meal-drafts/photo-references/1234-token.jpg"
        );

        assertDoesNotThrow(() -> service.validatePhotoRequest(request));
    }

    @Test
    void validatePhotoRequest_whenReferenceUsesArbitraryHttpsHost_rejects() {
        AiMealDraftSafetyServiceImpl service = new AiMealDraftSafetyServiceImpl(properties());

        assertThrows(IllegalArgumentException.class,
                () -> service.validatePhotoRequest(request("https://tracking.example.test/meal.jpg")));
        assertThrows(IllegalArgumentException.class,
                () -> service.validatePhotoRequest(request(
                        "https://api.grun.test.evil.example/api/v1/ai/meal-drafts/photo-references/token.jpg"
                )));
    }

    @Test
    void validatePhotoRequest_whenReferenceUsesExplicitStoragePrefix_accepts() {
        AiMealDraftSafetyServiceImpl service = new AiMealDraftSafetyServiceImpl(properties());

        assertDoesNotThrow(() -> service.validatePhotoRequest(request("s3://grun-meals/u1/meal.jpg")));
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getPhoto().setPublicBaseUrl("https://api.grun.test");
        properties.getPhoto().setAllowedReferencePrefixes("https://,s3://grun-meals/");
        return properties;
    }

    private AiPhotoMealDraftRequestDto request(String imageReference) {
        AiPhotoMealDraftRequestDto request = new AiPhotoMealDraftRequestDto();
        request.setImageReference(imageReference);
        return request;
    }
}
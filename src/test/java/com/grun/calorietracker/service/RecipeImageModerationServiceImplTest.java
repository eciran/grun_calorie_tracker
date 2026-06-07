package com.grun.calorietracker.service;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.service.impl.RecipeImageModerationServiceImpl;
import com.grun.calorietracker.service.impl.RecipeImageModerationServiceImpl.ProviderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeImageModerationServiceImplTest {

    @Test
    void moderate_whenUrlIsInvalid_rejectsBeforeProviderCall() {
        RestOperations restOperations = mock(RestOperations.class);
        RecipeImageModerationServiceImpl service = new RecipeImageModerationServiceImpl(enabledProperties(), restOperations);

        RecipeImageModerationService.Result result = service.moderate("http://cdn.grun.app/recipe.jpg", ImageSource.USER_UPLOAD);

        assertEquals(ImageStatus.REJECTED, result.status());
        verify(restOperations, never()).postForObject(any(String.class), any(HttpEntity.class), any(Class.class));
    }

    @Test
    void moderate_whenProviderApprovesWithHighConfidence_approvesImage() {
        RestOperations restOperations = mock(RestOperations.class);
        AiProperties properties = enabledProperties();
        RecipeImageModerationServiceImpl service = new RecipeImageModerationServiceImpl(properties, restOperations);
        when(restOperations.postForObject(eq("https://moderation.example/check"), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ProviderResponse(true, false, 0.97, "Visual moderation approved.", "safe"));

        RecipeImageModerationService.Result result = service.moderate("https://cdn.grun.app/recipe.jpg", ImageSource.USER_UPLOAD);

        assertEquals(ImageStatus.APPROVED, result.status());
        assertEquals("Visual moderation approved.", result.note());
    }

    @Test
    void moderate_whenProviderRejectsWithHighConfidence_rejectsImage() {
        RestOperations restOperations = mock(RestOperations.class);
        AiProperties properties = enabledProperties();
        RecipeImageModerationServiceImpl service = new RecipeImageModerationServiceImpl(properties, restOperations);
        when(restOperations.postForObject(eq("https://moderation.example/check"), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ProviderResponse(false, true, 0.9, "Unsafe visual content.", "unsafe"));

        RecipeImageModerationService.Result result = service.moderate("https://cdn.grun.app/recipe.jpg", ImageSource.USER_UPLOAD);

        assertEquals(ImageStatus.REJECTED, result.status());
        assertEquals("Unsafe visual content.", result.note());
    }

    @Test
    void moderate_whenProviderConfidenceIsLow_keepsNeedsReview() {
        RestOperations restOperations = mock(RestOperations.class);
        AiProperties properties = enabledProperties();
        RecipeImageModerationServiceImpl service = new RecipeImageModerationServiceImpl(properties, restOperations);
        when(restOperations.postForObject(eq("https://moderation.example/check"), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ProviderResponse(true, false, 0.7, "Low confidence.", "safe"));

        RecipeImageModerationService.Result result = service.moderate("https://cdn.grun.app/recipe.jpg", ImageSource.USER_UPLOAD);

        assertEquals(ImageStatus.NEEDS_REVIEW, result.status());
    }

    private AiProperties enabledProperties() {
        AiProperties properties = new AiProperties();
        properties.getRecipeImageModeration().setEnabled(true);
        properties.getRecipeImageModeration().setEndpoint("https://moderation.example/check");
        properties.getRecipeImageModeration().setApiKey("test-key");
        properties.getRecipeImageModeration().setRejectThreshold(0.85);
        properties.getRecipeImageModeration().setApproveThreshold(0.95);
        return properties;
    }
}

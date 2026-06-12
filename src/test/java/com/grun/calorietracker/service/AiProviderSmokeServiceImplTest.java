package com.grun.calorietracker.service;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiProviderSmokeResponseDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.impl.AiProviderSmokeServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiProviderSmokeServiceImplTest {

    private final AiProviderConfigurationValidator validator = mock(AiProviderConfigurationValidator.class);
    private final AiMealDraftProviderClient client = mock(AiMealDraftProviderClient.class);
    private final AiProperties properties = new AiProperties();

    @Test
    void smoke_whenProviderReturnsDraft_marksReachable() {
        properties.setEnabled(true);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("log-model");
        when(client.provider()).thenReturn(AiProvider.LOG);
        AiMealDraftResponseDto providerResponse = new AiMealDraftResponseDto();
        providerResponse.setItems(List.of(new AiMealDraftItemDto()));
        when(client.createVoiceFoodDraft(any())).thenReturn(providerResponse);

        AiProviderSmokeServiceImpl service = new AiProviderSmokeServiceImpl(properties, validator, List.of(client));

        AiProviderSmokeResponseDto result = service.smoke(AiRequestType.VOICE_FOOD_LOG);

        assertTrue(result.isConfigured());
        assertTrue(result.isProviderReachable());
        assertEquals("OK", result.getStatus());
        assertEquals(1, result.getReturnedItemCount());
    }

    @Test
    void smoke_whenConfigurationInvalid_returnsFailedWithoutThrowing() {
        properties.setProvider(AiProvider.HTTP_JSON);
        properties.setModel("provider-model");
        doThrow(new IllegalArgumentException("HTTP JSON AI provider API key is not configured."))
                .when(validator).validateConfiguredForDraft();

        AiProviderSmokeServiceImpl service = new AiProviderSmokeServiceImpl(properties, validator, List.of(client));

        AiProviderSmokeResponseDto result = service.smoke(AiRequestType.PHOTO_MEAL_LOG);

        assertFalse(result.isConfigured());
        assertFalse(result.isProviderReachable());
        assertEquals("FAILED", result.getStatus());
        assertEquals("HTTP JSON AI provider API key is not configured.", result.getMessage());
    }
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.service.impl.AiProviderConfigurationValidatorImpl;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProviderConfigurationValidatorImplTest {

    @Test
    void validateConfiguredForDraft_whenDisabled_throwsBeforeProviderUse() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(false);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("log-draft-v1");

        assertThrows(IllegalArgumentException.class,
                () -> new AiProviderConfigurationValidatorImpl(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenModelMissing_throws() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("not-configured");

        assertThrows(IllegalArgumentException.class,
                () -> new AiProviderConfigurationValidatorImpl(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenHttpJsonMissingSecret_throws() {
        AiProperties properties = httpJsonProperties();
        properties.getHttpJson().setApiKey("");

        assertThrows(IllegalArgumentException.class,
                () -> new AiProviderConfigurationValidatorImpl(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenHttpJsonUsesPlainHttp_throws() {
        AiProperties properties = httpJsonProperties();
        properties.getHttpJson().setEndpoint("http://ai-provider.example.test");

        assertThrows(IllegalArgumentException.class,
                () -> new AiProviderConfigurationValidatorImpl(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenHttpJsonConfigured_accepts() {
        AiProperties properties = httpJsonProperties();

        assertDoesNotThrow(() -> new AiProviderConfigurationValidatorImpl(properties).validateConfiguredForDraft());
    }

    private AiProperties httpJsonProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.HTTP_JSON);
        properties.setModel("provider-model-v1");
        properties.getHttpJson().setEndpoint("https://ai-provider.example.test/meal-drafts");
        properties.getHttpJson().setApiKey("secret-test-key");
        properties.getHttpJson().setTimeout(Duration.ofSeconds(20));
        return properties;
    }
}

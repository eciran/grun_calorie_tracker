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
                () -> validator(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenModelMissing_throws() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("not-configured");

        assertThrows(IllegalArgumentException.class,
                () -> validator(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenHttpJsonMissingSecret_throws() {
        AiProperties properties = httpJsonProperties();
        properties.getHttpJson().setApiKey("");

        assertThrows(IllegalArgumentException.class,
                () -> validator(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenHttpJsonUsesPlainHttp_throws() {
        AiProperties properties = httpJsonProperties();
        properties.getHttpJson().setEndpoint("http://ai-provider.example.test");

        assertThrows(IllegalArgumentException.class,
                () -> validator(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenHttpJsonConfigured_accepts() {
        AiProperties properties = httpJsonProperties();

        assertDoesNotThrow(() -> validator(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenOpenAiMissingSecret_throws() {
        AiProperties properties = openAiProperties();
        properties.getOpenai().setApiKey("");

        assertThrows(IllegalArgumentException.class,
                () -> validator(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenOpenAiConfigured_accepts() {
        AiProperties properties = openAiProperties();

        assertDoesNotThrow(() -> validator(properties).validateConfiguredForDraft());
    }
    @Test
    void validateConfiguredForDraft_whenPromptVersionMissing_throws() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("log-draft-v1");
        properties.setPromptVersion(" ");

        assertThrows(IllegalArgumentException.class,
                () -> validator(properties).validateConfiguredForDraft());
    }

    @Test
    void validateConfiguredForDraft_whenOpenAiRepairAttemptsExceedOne_throws() {
        AiProperties properties = openAiProperties();
        properties.getOpenai().setMaxRepairAttempts(2);

        assertThrows(IllegalArgumentException.class,
                () -> validator(properties).validateConfiguredForDraft());
    }
    private AiProviderConfigurationValidatorImpl validator(AiProperties properties) {
        return new AiProviderConfigurationValidatorImpl(properties, org.mockito.Mockito.mock(AiOperationsPolicyService.class));
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
    private AiProperties openAiProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.OPENAI);
        properties.setModel("gpt-5.4-mini");
        properties.getOpenai().setBaseUrl("https://api.openai.com/v1/responses");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setTimeout(Duration.ofSeconds(20));
        return properties;
    }
}

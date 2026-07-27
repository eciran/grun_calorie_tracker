package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.AiOperationsPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiProviderConfigurationValidatorImpl implements AiProviderConfigurationValidator {

    private final AiProperties properties;
    private final AiOperationsPolicyService operationsPolicyService;

    @Override
    public void validateConfiguredForDraft() {
        operationsPolicyService.assertRequestAllowed();
        if (!properties.isEnabled() || properties.getProvider() == AiProvider.DISABLED) {
            throw new IllegalArgumentException("AI meal draft provider is disabled.");
        }
        if (isBlank(properties.getModel()) || "not-configured".equalsIgnoreCase(properties.getModel().trim())) {
            throw new IllegalArgumentException("AI model is not configured.");
        }
        if (isBlank(properties.getPromptVersion())) {
            throw new IllegalArgumentException("AI prompt version is not configured.");
        }
        if (properties.getProvider() == AiProvider.HTTP_JSON) {
            validateHttpJson();
        }
        if (properties.getProvider() == AiProvider.OPENAI) {
            validateOpenAi();
        }
    }

    private void validateHttpJson() {
        AiProperties.HttpJson httpJson = properties.getHttpJson();
        if (httpJson == null || isBlank(httpJson.getEndpoint())) {
            throw new IllegalArgumentException("HTTP JSON AI provider endpoint is not configured.");
        }
        if (httpJson.getEndpoint().startsWith("http://")) {
            throw new IllegalArgumentException("HTTP JSON AI provider endpoint must use HTTPS.");
        }
        if (isBlank(httpJson.getApiKey())) {
            throw new IllegalArgumentException("HTTP JSON AI provider API key is not configured.");
        }
        if (httpJson.getTimeout() == null || httpJson.getTimeout().isZero() || httpJson.getTimeout().isNegative()) {
            throw new IllegalArgumentException("HTTP JSON AI provider timeout must be positive.");
        }
    }

    private void validateOpenAi() {
        AiProperties.OpenAi openai = properties.getOpenai();
        if (openai == null || isBlank(openai.getBaseUrl())) {
            throw new IllegalArgumentException("OpenAI provider base URL is not configured.");
        }
        if (openai.getBaseUrl().startsWith("http://")) {
            throw new IllegalArgumentException("OpenAI provider base URL must use HTTPS.");
        }
        if (isBlank(openai.getApiKey())) {
            throw new IllegalArgumentException("OpenAI provider API key is not configured.");
        }
        if (openai.getTimeout() == null || openai.getTimeout().isZero() || openai.getTimeout().isNegative()) {
            throw new IllegalArgumentException("OpenAI provider timeout must be positive.");
        }
        if (openai.getMaxRepairAttempts() < 0 || openai.getMaxRepairAttempts() > 1) {
            throw new IllegalArgumentException("OpenAI provider max repair attempts must be 0 or 1.");
        }
    }
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

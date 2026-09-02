package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
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
        validateConfiguredForDraft(null);
    }

    @Override
    public void validateConfiguredForDraft(AiRequestType requestType) {
        operationsPolicyService.assertRequestAllowed();
        AiProvider provider = properties.resolveProvider(requestType);
        String model = properties.resolveModel(requestType);
        if (!properties.isEnabled() || provider == null || provider == AiProvider.DISABLED) {
            throw new IllegalArgumentException("AI meal draft provider is disabled.");
        }
        if (isBlank(model) || "not-configured".equalsIgnoreCase(model.trim())) {
            throw new IllegalArgumentException("AI model is not configured.");
        }
        if (isBlank(properties.getPromptVersion())) {
            throw new IllegalArgumentException("AI prompt version is not configured.");
        }
        if (provider == AiProvider.HTTP_JSON) {
            validateHttpJson();
        }
        if (provider == AiProvider.OPENAI) {
            validateOpenAi();
        }
        if (provider == AiProvider.GEMINI) {
            validateGemini();
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

    private void validateGemini() {
        AiProperties.Gemini gemini = properties.getGemini();
        if (gemini == null || isBlank(gemini.getBaseUrl())) {
            throw new IllegalArgumentException("Gemini provider base URL is not configured.");
        }
        if (gemini.getBaseUrl().startsWith("http://")) {
            throw new IllegalArgumentException("Gemini provider base URL must use HTTPS.");
        }
        if (isBlank(gemini.getApiKey())) {
            throw new IllegalArgumentException("Gemini provider API key is not configured.");
        }
        if (gemini.getTimeout() == null || gemini.getTimeout().isZero() || gemini.getTimeout().isNegative()) {
            throw new IllegalArgumentException("Gemini provider timeout must be positive.");
        }
        if (gemini.getMaxOutputTokens() <= 0) {
            throw new IllegalArgumentException("Gemini provider max output tokens must be positive.");
        }
    }
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

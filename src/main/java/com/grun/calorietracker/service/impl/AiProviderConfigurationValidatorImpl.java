package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiProviderConfigurationValidatorImpl implements AiProviderConfigurationValidator {

    private final AiProperties properties;

    @Override
    public void validateConfiguredForDraft() {
        if (!properties.isEnabled() || properties.getProvider() == AiProvider.DISABLED) {
            throw new IllegalArgumentException("AI meal draft provider is disabled.");
        }
        if (isBlank(properties.getModel()) || "not-configured".equalsIgnoreCase(properties.getModel().trim())) {
            throw new IllegalArgumentException("AI model is not configured.");
        }
        if (properties.getProvider() == AiProvider.HTTP_JSON) {
            validateHttpJson();
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

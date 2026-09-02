package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.AiRequestType;

public interface AiProviderConfigurationValidator {
    void validateConfiguredForDraft();
    void validateConfiguredForDraft(AiRequestType requestType);
}

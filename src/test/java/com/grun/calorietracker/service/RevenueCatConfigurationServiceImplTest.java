package com.grun.calorietracker.service;

import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.RevenueCatConfigStatusDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationRequestDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationResponseDto;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.service.impl.RevenueCatConfigurationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RevenueCatConfigurationServiceImplTest {

    private RevenueCatProperties properties;
    private RevenueCatConfigurationService service;

    @BeforeEach
    void setUp() {
        properties = new RevenueCatProperties();
        properties.setWebhookAuthorization("Bearer rc-secret");
        properties.getProducts().getPlus().add("grun_plus_monthly");
        properties.getProducts().getPro().add("grun_pro_monthly");
        properties.getProducts().getAiAddonQuotas().put("grun_ai_15_credits", 15);
        properties.getProducts().getAiAddonValidityDays().put("grun_ai_15_credits", 14);
        service = new RevenueCatConfigurationServiceImpl(properties);
    }

    @Test
    void getConfigStatus_doesNotExposeSecretValue() {
        RevenueCatConfigStatusDto result = service.getConfigStatus();

        assertThat(result.isWebhookAuthorizationConfigured()).isTrue();
        assertThat(result.isStrictProductMapping()).isTrue();
        assertThat(result.isProductionReady()).isTrue();
        assertThat(result.getMissingRequiredConfig()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getProProductIds()).contains("grun_pro_monthly");
        assertThat(result.getAiAddonQuotas()).containsEntry("grun_ai_15_credits", 15);
    }

    @Test
    void getConfigStatus_whenProductionValuesAreMissing_reportsMissingConfig() {
        RevenueCatProperties incompleteProperties = new RevenueCatProperties();
        RevenueCatConfigurationService incompleteService = new RevenueCatConfigurationServiceImpl(incompleteProperties);

        RevenueCatConfigStatusDto result = incompleteService.getConfigStatus();

        assertThat(result.isProductionReady()).isFalse();
        assertThat(result.getMissingRequiredConfig())
                .contains(
                        "grun.revenuecat.webhook-authorization",
                        "grun.revenuecat.products.plus",
                        "grun.revenuecat.products.pro",
                        "grun.revenuecat.products.ai-addon-quotas"
                );
    }

    @Test
    void getConfigStatus_whenStrictMappingDisabled_reportsWarning() {
        properties.setStrictProductMapping(false);

        RevenueCatConfigStatusDto result = service.getConfigStatus();

        assertThat(result.isProductionReady()).isTrue();
        assertThat(result.getWarnings()).contains("Strict product mapping is disabled; unknown subscription products may be ignored instead of failed.");
    }

    @Test
    void validateMapping_whenProEntitlementMatches_returnsProSubscription() {
        RevenueCatMappingValidationRequestDto request = request("INITIAL_PURCHASE", "unknown_product", List.of("pro"));

        RevenueCatMappingValidationResponseDto result = service.validateMapping(request);

        assertThat(result.isRecognized()).isTrue();
        assertThat(result.getMappingType()).isEqualTo("SUBSCRIPTION");
        assertThat(result.getSubscriptionPlan()).isEqualTo(SubscriptionPlan.PRO);
    }

    @Test
    void validateMapping_whenProductTokenMatches_returnsSubscription() {
        RevenueCatMappingValidationRequestDto request = request("INITIAL_PURCHASE", "store_pro_yearly", List.of());

        RevenueCatMappingValidationResponseDto result = service.validateMapping(request);

        assertThat(result.isRecognized()).isTrue();
        assertThat(result.getSubscriptionPlan()).isEqualTo(SubscriptionPlan.PRO);
    }

    @Test
    void validateMapping_whenConfiguredAddonMatches_returnsQuotaAndValidity() {
        RevenueCatMappingValidationRequestDto request = request("NON_RENEWING_PURCHASE", "grun_ai_15_credits", List.of());

        RevenueCatMappingValidationResponseDto result = service.validateMapping(request);

        assertThat(result.isRecognized()).isTrue();
        assertThat(result.getMappingType()).isEqualTo("AI_ADDON");
        assertThat(result.getAiAddonQuota()).isEqualTo(15);
        assertThat(result.getAiAddonValidityDays()).isEqualTo(14);
    }

    @Test
    void validateMapping_whenUnknownSubscriptionAndStrict_returnsFailed() {
        RevenueCatMappingValidationRequestDto request = request("INITIAL_PURCHASE", "unknown_product", List.of("unknown"));

        RevenueCatMappingValidationResponseDto result = service.validateMapping(request);

        assertThat(result.isRecognized()).isFalse();
        assertThat(result.getMappingType()).isEqualTo("FAILED");
        assertThat(result.getMessage()).contains("not mapped");
    }

    @Test
    void validateMapping_whenIgnoredRevenueCatEvent_returnsIgnored() {
        RevenueCatMappingValidationRequestDto request = request("TRANSFER", "grun_pro_monthly", List.of("pro"));

        RevenueCatMappingValidationResponseDto result = service.validateMapping(request);

        assertThat(result.isRecognized()).isTrue();
        assertThat(result.getMappingType()).isEqualTo("IGNORED");
    }

    private RevenueCatMappingValidationRequestDto request(String eventType, String productId, List<String> entitlements) {
        RevenueCatMappingValidationRequestDto request = new RevenueCatMappingValidationRequestDto();
        request.setEventType(eventType);
        request.setProductId(productId);
        request.setEntitlementIds(entitlements);
        return request;
    }
}

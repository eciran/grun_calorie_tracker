package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.RevenueCatConfigStatusDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationRequestDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationResponseDto;
import com.grun.calorietracker.enums.RevenueCatEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.service.RevenueCatConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RevenueCatConfigurationServiceImpl implements RevenueCatConfigurationService {

    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");

    private final RevenueCatProperties properties;

    @Override
    public RevenueCatConfigStatusDto getConfigStatus() {
        RevenueCatConfigStatusDto dto = new RevenueCatConfigStatusDto();
        dto.setWebhookAuthorizationConfigured(properties.getWebhookAuthorization() != null && !properties.getWebhookAuthorization().isBlank());
        dto.setStrictProductMapping(properties.isStrictProductMapping());
        dto.setPlusEntitlements(List.copyOf(properties.getEntitlements().getPlus()));
        dto.setProEntitlements(List.copyOf(properties.getEntitlements().getPro()));
        dto.setPlusProductIds(List.copyOf(properties.getProducts().getPlus()));
        dto.setProProductIds(List.copyOf(properties.getProducts().getPro()));
        dto.setAiAddonQuotas(properties.getProducts().getAiAddonQuotas());
        dto.setAiAddonValidityDays(properties.getProducts().getAiAddonValidityDays());
        dto.setDefaultAiAddonValidityDays(properties.getProducts().getDefaultAiAddonValidityDays());
        return dto;
    }

    @Override
    public RevenueCatMappingValidationResponseDto validateMapping(RevenueCatMappingValidationRequestDto request) {
        RevenueCatEventType eventType = RevenueCatEventType.from(request.getEventType());
        List<String> entitlementIds = request.getEntitlementIds() == null ? List.of() : request.getEntitlementIds();
        String productId = request.getProductId();

        return switch (eventType) {
            case INITIAL_PURCHASE, RENEWAL, UNCANCELLATION -> validateSubscriptionMapping(productId, entitlementIds);
            case NON_RENEWING_PURCHASE -> validateAiAddonMapping(productId);
            case UNKNOWN -> response(false, "FAILED", null, null, null, "Unknown RevenueCat event type.");
            default -> response(true, "IGNORED", null, null, null, "Event type does not change backend entitlement state.");
        };
    }

    private RevenueCatMappingValidationResponseDto validateSubscriptionMapping(String productId, List<String> entitlementIds) {
        SubscriptionPlan plan = resolvePlan(productId, entitlementIds);
        if (plan == null) {
            return response(
                    false,
                    properties.isStrictProductMapping() ? "FAILED" : "IGNORED",
                    null,
                    null,
                    null,
                    "Subscription product or entitlement is not mapped to PLUS or PRO."
            );
        }
        return response(true, "SUBSCRIPTION", plan, null, null, "Subscription event maps to " + plan + ".");
    }

    private RevenueCatMappingValidationResponseDto validateAiAddonMapping(String productId) {
        Integer quota = resolveAddonQuota(productId);
        if (quota == null) {
            return response(
                    false,
                    properties.isStrictProductMapping() ? "FAILED" : "IGNORED",
                    null,
                    null,
                    null,
                    "AI add-on product is not mapped to a quota amount."
            );
        }
        return response(true, "AI_ADDON", null, quota, resolveAddonValidityDays(productId), "AI add-on product maps to quota.");
    }

    private RevenueCatMappingValidationResponseDto response(
            boolean recognized,
            String mappingType,
            SubscriptionPlan plan,
            Integer aiAddonQuota,
            Integer aiAddonValidityDays,
            String message) {
        RevenueCatMappingValidationResponseDto dto = new RevenueCatMappingValidationResponseDto();
        dto.setRecognized(recognized);
        dto.setMappingType(mappingType);
        dto.setSubscriptionPlan(plan);
        dto.setAiAddonQuota(aiAddonQuota);
        dto.setAiAddonValidityDays(aiAddonValidityDays);
        dto.setStrictProductMapping(properties.isStrictProductMapping());
        dto.setMessage(message);
        return dto;
    }

    private SubscriptionPlan resolvePlan(String productId, List<String> entitlementIds) {
        if (matchesAny(entitlementIds, properties.getEntitlements().getPro()) || matchesProduct(productId, properties.getProducts().getPro(), "pro")) {
            return SubscriptionPlan.PRO;
        }
        if (matchesAny(entitlementIds, properties.getEntitlements().getPlus()) || matchesProduct(productId, properties.getProducts().getPlus(), "plus")) {
            return SubscriptionPlan.PLUS;
        }
        return null;
    }

    private Integer resolveAddonQuota(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        Integer configured = properties.getProducts().getAiAddonQuotas().get(productId);
        if (configured != null) {
            return configured;
        }
        String lower = productId.toLowerCase(Locale.ROOT);
        if (!lower.contains("ai") || !lower.contains("credit")) {
            return null;
        }
        Matcher matcher = FIRST_NUMBER.matcher(lower);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private int resolveAddonValidityDays(String productId) {
        if (productId != null && properties.getProducts().getAiAddonValidityDays().containsKey(productId)) {
            return properties.getProducts().getAiAddonValidityDays().get(productId);
        }
        return properties.getProducts().getDefaultAiAddonValidityDays();
    }

    private boolean matchesProduct(String productId, List<String> configuredProducts, String fallbackToken) {
        if (productId == null || productId.isBlank()) {
            return false;
        }
        if (configuredProducts.contains(productId)) {
            return true;
        }
        return hasProductToken(productId, fallbackToken);
    }

    private boolean hasProductToken(String productId, String token) {
        String[] parts = productId.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String part : parts) {
            if (part.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAny(List<String> incoming, List<String> configured) {
        return incoming.stream().anyMatch(value -> configured.stream().anyMatch(config -> config.equalsIgnoreCase(value)));
    }
}

package com.grun.calorietracker.exception;

import com.grun.calorietracker.enums.SubscriptionFeature;
import org.springframework.security.access.AccessDeniedException;

public class SubscriptionFeatureAccessDeniedException extends AccessDeniedException {

    private final SubscriptionFeature feature;

    public SubscriptionFeatureAccessDeniedException(SubscriptionFeature feature) {
        super("Subscription feature access denied");
        this.feature = feature;
    }

    public SubscriptionFeature getFeature() {
        return feature;
    }
}
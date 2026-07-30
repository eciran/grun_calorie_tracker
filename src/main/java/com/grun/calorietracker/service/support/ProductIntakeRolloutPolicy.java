package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.ProductIntakeRolloutProperties;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@Component
public class ProductIntakeRolloutPolicy {
    public static final String DISABLED = "DISABLED";
    public static final String KILL_SWITCH = "KILL_SWITCH";
    public static final String MARKET_NOT_ENABLED = "MARKET_NOT_ENABLED";
    public static final String OUTSIDE_COHORT = "OUTSIDE_COHORT";
    public static final String INTERNAL_DOGFOOD = "INTERNAL_DOGFOOD";
    public static final String ROLLOUT_ELIGIBLE = "ROLLOUT_ELIGIBLE";

    private final ProductIntakeRolloutProperties properties;
    private final FoodProductIntakeMetrics metrics;

    public ProductIntakeRolloutPolicy(
            ProductIntakeRolloutProperties properties,
            FoodProductIntakeMetrics metrics
    ) {
        this.properties = properties;
        this.metrics = metrics;
    }

    public Decision evaluate(UserEntity user) {
        MarketRegion market = user.getMarketRegion() == null ? MarketRegion.GLOBAL : user.getMarketRegion();
        if (properties.isKillSwitch()) return recorded(new Decision(false, KILL_SWITCH, market));
        if (!properties.isEnabled()) return recorded(new Decision(false, DISABLED, market));
        String normalizedEmail = normalize(user.getEmail());
        if (properties.getInternalDogfoodEmails().stream().map(this::normalize).anyMatch(normalizedEmail::equals)) {
            return recorded(new Decision(true, INTERNAL_DOGFOOD, market));
        }
        if (!properties.getMarkets().contains(market)) {
            return recorded(new Decision(false, MARKET_NOT_ENABLED, market));
        }
        if (bucket(normalizedEmail) >= properties.getPercentage()) {
            return recorded(new Decision(false, OUTSIDE_COHORT, market));
        }
        return recorded(new Decision(true, ROLLOUT_ELIGIBLE, market));
    }

    public void requireAvailable(UserEntity user) {
        if (!evaluate(user).available()) {
            throw new AccessDeniedException("Product contribution intake is not currently available.");
        }
    }

    int bucket(String stableUserKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((properties.getCohortSalt() + ":" + stableUserKey).getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xff) << 24) | ((hash[1] & 0xff) << 16)
                    | ((hash[2] & 0xff) << 8) | (hash[3] & 0xff);
            return Math.floorMod(value, 100);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private Decision recorded(Decision decision) {
        metrics.recordRolloutDecision(
                decision.reason().toLowerCase(Locale.ROOT),
                decision.marketRegion().name().toLowerCase(Locale.ROOT)
        );
        return decision;
    }

    public record Decision(boolean available, String reason, MarketRegion marketRegion) { }
}
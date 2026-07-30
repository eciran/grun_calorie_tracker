package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.ProductIntakeRolloutProperties;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductIntakeRolloutPolicyTest {
    private final ProductIntakeRolloutProperties properties = new ProductIntakeRolloutProperties();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ProductIntakeRolloutPolicy policy =
            new ProductIntakeRolloutPolicy(properties, new FoodProductIntakeMetrics(meterRegistry));

    @Test
    void defaultsAreFailClosed() {
        var decision = policy.evaluate(user("person@example.com", MarketRegion.GLOBAL));
        assertFalse(decision.available());
        assertEquals(ProductIntakeRolloutPolicy.DISABLED, decision.reason());
    }

    @Test
    void killSwitchOverridesInternalDogfoodImmediately() {
        properties.setEnabled(true);
        properties.setKillSwitch(true);
        properties.setInternalDogfoodEmails(Set.of("DOGFOOD@example.com"));
        var user = user("dogfood@example.com", MarketRegion.EU);
        assertEquals(ProductIntakeRolloutPolicy.KILL_SWITCH, policy.evaluate(user).reason());
        assertThrows(AccessDeniedException.class, () -> policy.requireAvailable(user));
    }

    @Test
    void dogfoodCanRunOutsideConfiguredMarketAndCohort() {
        properties.setEnabled(true);
        properties.setPercentage(0);
        properties.setMarkets(Set.of(MarketRegion.UK_IE));
        properties.setInternalDogfoodEmails(Set.of("staff@example.com"));
        var decision = policy.evaluate(user("staff@example.com", MarketRegion.EU));
        assertTrue(decision.available());
        assertEquals(ProductIntakeRolloutPolicy.INTERNAL_DOGFOOD, decision.reason());
    }

    @Test
    void configuredMarketsAreEvaluatedWithoutCountrySpecificRules() {
        properties.setEnabled(true);
        properties.setPercentage(100);
        properties.setMarkets(Set.of(MarketRegion.EU, MarketRegion.UK_IE));
        assertTrue(policy.evaluate(user("one@example.com", MarketRegion.EU)).available());
        assertTrue(policy.evaluate(user("two@example.com", MarketRegion.UK_IE)).available());
        assertEquals(ProductIntakeRolloutPolicy.MARKET_NOT_ENABLED,
                policy.evaluate(user("three@example.com", MarketRegion.TR)).reason());
    }

    @Test
    void cohortIsStableAndZeroPercentIsClosed() {
        properties.setEnabled(true);
        properties.setMarkets(Set.of(MarketRegion.GLOBAL));
        properties.setPercentage(0);
        var user = user("stable@example.com", null);
        assertEquals(ProductIntakeRolloutPolicy.OUTSIDE_COHORT, policy.evaluate(user).reason());
        int first = policy.bucket("stable@example.com");
        assertEquals(first, policy.bucket("stable@example.com"));
        properties.setPercentage(100);
        assertTrue(policy.evaluate(user).available());
    }

    @Test
    void recordsBoundedPilotMetricsWithoutUserIdentity() {
        properties.setEnabled(true);
        properties.setInternalDogfoodEmails(Set.of("staff@example.com"));
        policy.evaluate(user("staff@example.com", MarketRegion.EU));
        assertEquals(1.0, meterRegistry.get("grun.food.product.intake.rollout.decisions")
                .tags("reason", "internal_dogfood", "market", "eu").counter().count());
        assertFalse(meterRegistry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> tag.getValue().contains("@")));
    }

    private UserEntity user(String email, MarketRegion market) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setMarketRegion(market);
        return user;
    }
}
package com.grun.calorietracker.config;

import com.grun.calorietracker.enums.MarketRegion;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Data
@Validated
@ConfigurationProperties(prefix = "grun.product-intake.rollout")
public class ProductIntakeRolloutProperties {
    private boolean enabled = false;
    private boolean killSwitch = false;
    @Min(0)
    @Max(100)
    private int percentage = 0;
    private Set<MarketRegion> markets = EnumSet.noneOf(MarketRegion.class);
    private Set<String> internalDogfoodEmails = new HashSet<>();
    private String cohortSalt = "product-intake-v1";

    @AssertTrue(message = "product intake rollout percentage must be one of 0, 1, 10, 50 or 100")
    public boolean isSupportedRolloutStage() {
        return percentage == 0
                || percentage == 1
                || percentage == 10
                || percentage == 50
                || percentage == 100;
    }

    @AssertTrue(message = "product intake external rollout requires at least one configured market")
    public boolean isMarketConfiguredForExternalRollout() {
        return !enabled || percentage == 0 || (markets != null && !markets.isEmpty());
    }

    @AssertTrue(message = "product intake enabled rollout requires a stable cohort salt of at least 16 characters")
    public boolean isCohortSaltSafe() {
        return !enabled || (cohortSalt != null && cohortSalt.trim().length() >= 16);
    }
}
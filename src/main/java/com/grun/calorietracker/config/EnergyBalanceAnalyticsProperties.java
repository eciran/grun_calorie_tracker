package com.grun.calorietracker.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "grun.progress.energy-balance")
public class EnergyBalanceAnalyticsProperties {

    @Min(1)
    @Max(366)
    private int maxRangeDays = 366;

    @NotBlank
    private String weightModelCode = "STATIC_ENERGY_DENSITY_V1";

    @DecimalMin("1000.0")
    @DecimalMax("20000.0")
    private double energyPerKgCoefficient = 7700.0;

    @DecimalMin("0.0")
    @DecimalMax("0.5")
    private double weightModelUncertaintyPercent = 0.15;

    @DecimalMin("0.0")
    private double balancedAbsoluteToleranceKcal = 100.0;

    @DecimalMin("0.0")
    @DecimalMax("0.25")
    private double balancedRelativeTolerance = 0.05;

    @Min(2)
    private int minimumWeightModelDays = 4;

    @Min(1)
    private int minimumEvaluatedDaysForConfidence = 4;

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private double mediumCoveragePercent = 40.0;

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private double highCoveragePercent = 80.0;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double highProviderShare = 0.50;

    @AssertTrue(message = "Energy balance confidence thresholds must be ordered.")
    public boolean isConfidenceThresholdOrderValid() {
        return mediumCoveragePercent <= highCoveragePercent;
    }
}

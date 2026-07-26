package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RuntimeRolloutSegment;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRuntimeOperationsPolicyUpdateRequestDto {
    @NotNull
    private Long version;
    @NotNull
    private Boolean maintenanceEnabled;
    @NotBlank @Size(max = 240)
    private String maintenanceMessage;
    @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9._+-]+")
    private String releaseVersion;
    @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String deploymentEnvironment;
    @NotBlank @Size(max = 40) @Pattern(regexp = "[0-9A-Za-z._+-]+")
    private String minimumIosVersion;
    @NotBlank @Size(max = 40) @Pattern(regexp = "[0-9A-Za-z._+-]+")
    private String minimumAndroidVersion;
    @NotNull
    private SubscriptionFeature rolloutFeature;
    @NotNull
    private Boolean rolloutEnabled;
    private SubscriptionPlan rolloutPlan;
    private MarketRegion rolloutRegion;
    @NotNull
    private RuntimeRolloutSegment rolloutSegment;
    @NotNull @Min(0) @Max(100)
    private Integer rolloutPercentage;
    @NotNull @Min(50) @Max(120000)
    private Long apiLatencyWarningMs;
    @NotNull @DecimalMin("0.001") @DecimalMax("1.0")
    private Double apiErrorRateThreshold;
    @Email @Size(max = 255)
    private String escalationTarget;
    @NotBlank @Size(min = 8, max = 500)
    private String reason;
}

package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.*;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminPromoRequestDto {
    @NotBlank @Size(max = 80)
    private String code;
    @NotBlank @Size(max = 160)
    private String name;
    @Size(max = 600)
    private String description;
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
    private Double discountPercent;
    @NotNull
    private PromoType promoType;
    @NotNull
    private PromoStore targetStore;
    private SubscriptionPlan targetPlan;
    private MarketRegion targetRegion;
    @Size(max = 120)
    private String targetProductId;
    @NotBlank @Pattern(regexp = "[A-Za-z]{3}")
    private String currency;
    @NotNull
    private PromoEligibilityRule eligibilityRule;
    @NotNull @Min(1)
    private Integer perUserLimit;
    @Min(1)
    private Integer globalLimit;
    @Size(max = 120)
    private String campaignKey;
    @Size(max = 160)
    private String providerOfferId;
    @Size(max = 160)
    private String providerProductId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}

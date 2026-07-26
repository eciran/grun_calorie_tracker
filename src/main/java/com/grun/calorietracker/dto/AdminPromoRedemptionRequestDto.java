package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PromoRedemptionStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AdminPromoRedemptionRequestDto {
    @NotNull @Positive
    private Long userId;
    @NotBlank @Size(max = 120)
    private String idempotencyKey;
    @Size(max = 160)
    private String providerEventId;
    @NotNull
    private PromoRedemptionStatus status;
    @PositiveOrZero
    private Long amountMinor;
    @Pattern(regexp = "[A-Za-z]{3}")
    private String currency;
    @Size(max = 500)
    private String rejectionReason;
}

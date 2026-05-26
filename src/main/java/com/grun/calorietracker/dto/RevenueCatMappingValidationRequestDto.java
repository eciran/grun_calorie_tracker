package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RevenueCatMappingValidationRequestDto {

    @NotBlank
    @Schema(description = "RevenueCat event type to validate.", example = "INITIAL_PURCHASE")
    private String eventType;

    @Schema(description = "RevenueCat product id configured in App Store / Google Play / RevenueCat.", example = "grun_pro_monthly")
    private String productId;

    @Schema(description = "RevenueCat entitlement ids attached to the event.", example = "[\"pro\"]")
    private List<String> entitlementIds = new ArrayList<>();
}

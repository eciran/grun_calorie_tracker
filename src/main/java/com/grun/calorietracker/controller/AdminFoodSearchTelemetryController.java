package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodSearchTelemetrySummaryDto;
import com.grun.calorietracker.service.FoodSearchTelemetryAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products/search-telemetry")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Product Search Telemetry")
public class AdminFoodSearchTelemetryController {
    private final FoodSearchTelemetryAdminService telemetryAdminService;

    @GetMapping("/summary")
    @Operation(summary = "Get product-search quality summary")
    public ResponseEntity<FoodSearchTelemetrySummaryDto> getSummary(
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
        return ResponseEntity.ok(telemetryAdminService.getSummary(hours));
    }
}
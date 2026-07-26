package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminEngagementAnalyticsDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.service.AdminEngagementAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/engagement/analytics")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Product Engagement Analytics")
public class AdminEngagementAnalyticsController {

    private final AdminEngagementAnalyticsService analyticsService;

    @GetMapping
    @Operation(summary = "Get privacy-safe product engagement analytics",
            description = "Returns versioned aggregate funnel, search, feature adoption and segment metrics. Raw prompts, notes and health details are never returned.")
    public ResponseEntity<AdminEngagementAnalyticsDto> getSummary(
            @RequestParam(defaultValue = "168") @Min(1) @Max(2160) int hours,
            @RequestParam(required = false) MarketRegion region,
            @RequestParam(required = false) PreferredLanguage language,
            @RequestParam(required = false) SubscriptionPlan plan) {
        return ResponseEntity.ok(analyticsService.getSummary(hours, region, language, plan));
    }
}

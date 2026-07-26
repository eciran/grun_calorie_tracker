package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminOnboardingAnalyticsDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.service.AdminOnboardingAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/admin/onboarding/analytics")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Onboarding Analytics", description = "Privacy-safe onboarding funnel monitoring.")
public class AdminOnboardingAnalyticsController {

    private final AdminOnboardingAnalyticsService analyticsService;

    @GetMapping
    @Operation(summary = "Get onboarding funnel counts", description = "Returns raw onboarding lifecycle event counts without profile or health payloads.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Onboarding funnel counts returned."),
            @ApiResponse(responseCode = "400", description = "Time window is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdminOnboardingAnalyticsDto> getSummary(
            @RequestParam(defaultValue = "168") @Min(1) @Max(2160) int hours
    ) {
        return ResponseEntity.ok(analyticsService.getSummary(hours));
    }
}

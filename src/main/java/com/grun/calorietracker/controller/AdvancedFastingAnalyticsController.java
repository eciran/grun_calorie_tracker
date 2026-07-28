package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdvancedFastingAnalyticsDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.service.AdvancedFastingAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/fasting/advanced/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Advanced Fasting Analytics", description = "Advanced fasting adherence and consistency analytics.")
public class AdvancedFastingAnalyticsController {

    private final AdvancedFastingAnalyticsService analyticsService;

    @GetMapping
    @Operation(
            summary = "Get advanced fasting analytics",
            description = "Returns adherence, timing consistency, duration, early-stop, weekday and 5:2 analytics. "
                    + "Missing metrics remain null and the inclusive range is capped at 366 days. "
                    + "FASTING_ADVANCED and ADVANCED_ANALYTICS access are required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Advanced fasting analytics returned.",
                    content = @Content(schema = @Schema(implementation = AdvancedFastingAnalyticsDto.class))),
            @ApiResponse(responseCode = "400", description = "Date range is invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Required subscription access is unavailable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdvancedFastingAnalyticsDto> getAnalytics(
            @Parameter(description = "Inclusive range start date.", example = "2026-07-01")
            @RequestParam LocalDate start,
            @Parameter(description = "Inclusive range end date.", example = "2026-07-31")
            @RequestParam LocalDate end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analyticsService.getAnalytics(userDetails.getUsername(), start, end));
    }
}
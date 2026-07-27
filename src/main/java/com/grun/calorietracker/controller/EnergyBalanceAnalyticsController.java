package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.EnergyBalanceAnalyticsDto;
import com.grun.calorietracker.service.EnergyBalanceAnalyticsService;
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
@RequestMapping("/api/v1/progress/energy-balance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Energy Balance Analytics", description = "Pro energy intake, expenditure, and modeled weight analytics.")
public class EnergyBalanceAnalyticsController {

    private final EnergyBalanceAnalyticsService energyBalanceAnalyticsService;

    @GetMapping
    @Operation(
            summary = "Get advanced energy balance analytics",
            description = "Returns a user-local daily energy series, coverage, meal and activity breakdowns, and a "
                    + "versioned modeled-versus-observed weight change for users with ADVANCED_ANALYTICS access. "
                    + "Missing intake or expenditure remains null and is excluded from balance calculations. Provider "
                    + "energy is combined with manual exercise and manual step calories without re-adding provider workouts. The inclusive "
                    + "date range is capped by the configured analytics limit."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Energy balance analytics returned.",
                    content = @Content(schema = @Schema(implementation = EnergyBalanceAnalyticsDto.class))),
            @ApiResponse(responseCode = "400", description = "Date range is invalid or extends into the future.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Advanced analytics is not available in the active entitlement.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<EnergyBalanceAnalyticsDto> getAnalytics(
            @Parameter(description = "Inclusive user-local range start date.", example = "2026-07-01")
            @RequestParam LocalDate start,
            @Parameter(description = "Inclusive user-local range end date.", example = "2026-07-27")
            @RequestParam LocalDate end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(energyBalanceAnalyticsService.getAnalytics(
                userDetails.getUsername(), start, end));
    }
}
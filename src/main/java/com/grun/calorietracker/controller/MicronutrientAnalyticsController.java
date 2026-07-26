package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.MicronutrientAnalyticsDto;
import com.grun.calorietracker.service.MicronutrientAnalyticsService;
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
@RequestMapping("/api/v1/progress/micronutrients")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Micronutrient Analytics", description = "Pro vitamin and mineral progress analytics.")
public class MicronutrientAnalyticsController {

    private final MicronutrientAnalyticsService micronutrientAnalyticsService;

    @GetMapping
    @Operation(
            summary = "Get advanced micronutrient analytics",
            description = "Returns daily vitamin and mineral trends, data coverage, target adherence, and optional "
                    + "previous-period comparisons for users with MICRONUTRIENT_ANALYTICS access. The response also "
                    + "contains deterministic data-confidence and non-diagnostic insight codes. Missing values remain "
                    + "null. The inclusive range is capped at 366 days."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Micronutrient analytics returned.",
                    content = @Content(schema = @Schema(implementation = MicronutrientAnalyticsDto.class))),
            @ApiResponse(responseCode = "400", description = "Date range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Micronutrient analytics is not available in the active entitlement.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<MicronutrientAnalyticsDto> getAnalytics(
            @Parameter(description = "Inclusive range start date.", example = "2026-07-01")
            @RequestParam LocalDate start,
            @Parameter(description = "Inclusive range end date.", example = "2026-07-31")
            @RequestParam LocalDate end,
            @Parameter(description = "Include the immediately preceding range with the same day count.")
            @RequestParam(defaultValue = "true") boolean comparePrevious,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(micronutrientAnalyticsService.getAnalytics(
                userDetails.getUsername(), start, end, comparePrevious));
    }
}

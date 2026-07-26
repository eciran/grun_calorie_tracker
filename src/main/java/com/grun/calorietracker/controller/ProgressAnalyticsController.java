package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.ProgressAnalyticsDto;
import com.grun.calorietracker.service.ProgressAnalyticsService;
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
@RequestMapping("/api/v1/progress/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Progress Analytics", description = "Pro progress, nutrition, activity and habit analytics.")
public class ProgressAnalyticsController {

    private final ProgressAnalyticsService progressAnalyticsService;

    @GetMapping
    @Operation(
            summary = "Get advanced progress analytics",
            description = "Returns deterministic analytics for an inclusive user-local date range. "
                    + "Missing diary days remain distinguishable from zero intake. The range is capped at 366 days."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress analytics returned."),
            @ApiResponse(responseCode = "400", description = "Date range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Advanced analytics is not available in the active entitlement.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ProgressAnalyticsDto> getAnalytics(
            @Parameter(description = "Inclusive range start date.", example = "2026-06-01")
            @RequestParam LocalDate start,
            @Parameter(description = "Inclusive range end date.", example = "2026-06-30")
            @RequestParam LocalDate end,
            @Parameter(description = "Include the immediately preceding range with the same day count.")
            @RequestParam(defaultValue = "true") boolean comparePrevious,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(progressAnalyticsService.getAnalytics(
                userDetails.getUsername(), start, end, comparePrevious));
    }
}

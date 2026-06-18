package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.StepDailySummaryDto;
import com.grun.calorietracker.dto.StepGoalDto;
import com.grun.calorietracker.dto.StepGoalRequestDto;
import com.grun.calorietracker.dto.StepManualLogRequestDto;
import com.grun.calorietracker.dto.StepManualLogResponseDto;
import com.grun.calorietracker.dto.StepRangeSummaryDto;
import com.grun.calorietracker.service.StepTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/steps")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Step Tracking", description = "Daily step goal, summary, range trend and manual step entries.")
public class StepTrackingController {

    private final StepTrackingService stepTrackingService;

    @GetMapping("/goal")
    @Operation(summary = "Get step goal", description = "Returns the authenticated user's daily step target settings.")
    public ResponseEntity<StepGoalDto> getGoal(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(stepTrackingService.getGoal(userDetails.getUsername()));
    }

    @PutMapping("/goal")
    @Operation(summary = "Update step goal", description = "Updates the authenticated user's daily step target settings.")
    @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<StepGoalDto> updateGoal(@AuthenticationPrincipal UserDetails userDetails,
                                                  @RequestBody @Valid StepGoalRequestDto request) {
        return ResponseEntity.ok(stepTrackingService.updateGoal(userDetails.getUsername(), request));
    }

    @GetMapping("/daily-summary")
    @Operation(summary = "Get daily step summary", description = "Returns daily step total, goal progress, providers and current target streak.")
    public ResponseEntity<StepDailySummaryDto> getDailySummary(@AuthenticationPrincipal UserDetails userDetails,
                                                               @RequestParam(required = false) LocalDate date) {
        return ResponseEntity.ok(stepTrackingService.getDailySummary(userDetails.getUsername(), date));
    }

    @GetMapping("/range-summary")
    @Operation(summary = "Get step range summary", description = "Returns step totals and daily trends for a selected date range.")
    public ResponseEntity<StepRangeSummaryDto> getRangeSummary(@AuthenticationPrincipal UserDetails userDetails,
                                                               @RequestParam(required = false) LocalDate startDate,
                                                               @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(stepTrackingService.getRangeSummary(userDetails.getUsername(), startDate, endDate));
    }

    @PostMapping("/manual-logs")
    @Operation(summary = "Add manual step log", description = "Adds a manual step metric when a connected provider is unavailable or needs correction.")
    @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<StepManualLogResponseDto> addManualLog(@AuthenticationPrincipal UserDetails userDetails,
                                                                 @RequestBody @Valid StepManualLogRequestDto request) {
        return ResponseEntity.ok(stepTrackingService.addManualLog(userDetails.getUsername(), request));
    }
}

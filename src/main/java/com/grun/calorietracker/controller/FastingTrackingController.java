package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.FastingDailySummaryDto;
import com.grun.calorietracker.dto.FastingPlanDto;
import com.grun.calorietracker.dto.FastingPlanRequestDto;
import com.grun.calorietracker.dto.FastingRangeSummaryDto;
import com.grun.calorietracker.dto.FastingSessionCancelRequestDto;
import com.grun.calorietracker.dto.FastingSessionDto;
import com.grun.calorietracker.dto.FastingSessionFinishRequestDto;
import com.grun.calorietracker.dto.FastingSessionStartRequestDto;
import com.grun.calorietracker.service.FastingTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/fasting")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fasting Tracking", description = "Authenticated intermittent fasting plan and session tracking.")
public class FastingTrackingController {

    private final FastingTrackingService fastingTrackingService;

    @GetMapping("/plan")
    @Operation(
            summary = "Get fasting plan",
            description = "Returns the authenticated user's fasting plan. If no plan exists yet, a default 16:8 plan is returned."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fasting plan returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FastingPlanDto> getPlan(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(fastingTrackingService.getPlan(userDetails.getUsername()));
    }

    @PutMapping("/plan")
    @Operation(
            summary = "Update fasting plan",
            description = "Creates or updates the authenticated user's fasting plan, including fasting duration, eating window, preferred start time and reminder preference."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fasting plan updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FastingPlanDto> updatePlan(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FastingPlanRequestDto request) {
        return ResponseEntity.ok(fastingTrackingService.updatePlan(userDetails.getUsername(), request));
    }

    @PostMapping("/sessions/start")
    @Operation(
            summary = "Start fasting session",
            description = "Starts a fasting session for the authenticated user. Only one active fasting session is allowed at a time."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fasting session started."),
            @ApiResponse(responseCode = "400", description = "Request validation failed or an active session already exists.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FastingSessionDto> startSession(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FastingSessionStartRequestDto request) {
        return ResponseEntity.ok(fastingTrackingService.startSession(userDetails.getUsername(), request));
    }

    @PostMapping("/sessions/{id}/finish")
    @Operation(
            summary = "Finish fasting session",
            description = "Finishes an active fasting session owned by the authenticated user and calculates actual duration and target completion."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fasting session finished."),
            @ApiResponse(responseCode = "400", description = "Request validation failed or session is not active.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Fasting session was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FastingSessionDto> finishSession(
            @Parameter(description = "Fasting session id.", example = "12") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FastingSessionFinishRequestDto request) {
        return ResponseEntity.ok(fastingTrackingService.finishSession(userDetails.getUsername(), id, request));
    }

    @PostMapping("/sessions/{id}/cancel")
    @Operation(
            summary = "Cancel fasting session",
            description = "Cancels an active fasting session owned by the authenticated user. Use this when the user started a session by mistake or decides not to continue it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fasting session cancelled."),
            @ApiResponse(responseCode = "400", description = "Request validation failed or session is not active.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Fasting session was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FastingSessionDto> cancelSession(
            @Parameter(description = "Fasting session id.", example = "12") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FastingSessionCancelRequestDto request) {
        return ResponseEntity.ok(fastingTrackingService.cancelSession(userDetails.getUsername(), id, request));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Get fasting daily summary",
            description = "Returns fasting sessions, active session progress, daily completed minutes and current fasting streak for one date."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fasting daily summary returned."),
            @ApiResponse(responseCode = "400", description = "Date format is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FastingDailySummaryDto> getDailySummary(
            @Parameter(description = "Summary date in ISO format. Defaults to today if omitted.", example = "2026-06-05")
            @RequestParam(required = false) String date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LocalDate summaryDate = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        return ResponseEntity.ok(fastingTrackingService.getDailySummary(userDetails.getUsername(), summaryDate));
    }

    @GetMapping("/summary/range")
    @Operation(
            summary = "Get fasting range summary",
            description = "Returns fasting history metrics and daily trend points for a selected date range. Range length is capped at 366 days."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fasting range summary returned."),
            @ApiResponse(responseCode = "400", description = "Date format or date range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FastingRangeSummaryDto> getRangeSummary(
            @Parameter(description = "Range start date in ISO format.", example = "2026-06-01")
            @RequestParam String startDate,
            @Parameter(description = "Range end date in ISO format.", example = "2026-06-07")
            @RequestParam String endDate,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(fastingTrackingService.getRangeSummary(
                userDetails.getUsername(),
                LocalDate.parse(startDate),
                LocalDate.parse(endDate)
        ));
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.WaterDailySummaryDto;
import com.grun.calorietracker.dto.WaterGoalDto;
import com.grun.calorietracker.dto.WaterGoalRequestDto;
import com.grun.calorietracker.dto.WaterLogDto;
import com.grun.calorietracker.dto.WaterLogRequestDto;
import com.grun.calorietracker.dto.WaterRangeSummaryDto;
import com.grun.calorietracker.dto.WaterReminderSettingsDto;
import com.grun.calorietracker.dto.WaterReminderSettingsRequestDto;
import com.grun.calorietracker.service.WaterTrackingService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/water-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Water Tracking", description = "Authenticated water intake logging and daily hydration summaries.")
public class WaterTrackingController {

    private final WaterTrackingService waterTrackingService;

    @PostMapping
    @Operation(
            summary = "Create a water log",
            description = "Adds a water intake entry in milliliters for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Water log created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<WaterLogDto> addWaterLog(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid WaterLogRequestDto request) {
        return ResponseEntity.ok(waterTrackingService.addWaterLog(userDetails.getUsername(), request));
    }


    @GetMapping("/daily-summary")
    @Operation(
            summary = "Get daily water summary",
            description = "Returns total water intake, configured target, remaining amount, progress percentage, and logs for one day."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daily water summary returned."),
            @ApiResponse(responseCode = "400", description = "Date format is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<WaterDailySummaryDto> getDailySummary(
            @Parameter(description = "Diary date in ISO format.", example = "2026-06-05")
            @RequestParam String date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(waterTrackingService.getDailySummary(userDetails.getUsername(), LocalDate.parse(date)));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a water log",
            description = "Updates a water intake entry owned by the authenticated user. Amount is stored in milliliters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Water log updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Water log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<WaterLogDto> updateWaterLog(
            @Parameter(description = "Water log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid WaterLogRequestDto request) {
        return ResponseEntity.ok(waterTrackingService.updateWaterLog(userDetails.getUsername(), id, request));
    }

    @GetMapping("/range-summary")
    @Operation(
            summary = "Get water range summary",
            description = "Returns hydration totals and daily chart points for a selected date range. Defaults to the last 7 user-local days when dates are omitted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Water range summary returned."),
            @ApiResponse(responseCode = "400", description = "Date range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<WaterRangeSummaryDto> getRangeSummary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Range start date in ISO format.", example = "2026-06-01")
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Range end date in ISO format.", example = "2026-06-07")
            @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(waterTrackingService.getRangeSummary(userDetails.getUsername(), startDate, endDate));
    }

    @GetMapping("/goal")
    @Operation(
            summary = "Get daily water goal",
            description = "Returns the authenticated user's daily hydration target in milliliters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Water goal returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<WaterGoalDto> getGoal(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(waterTrackingService.getGoal(userDetails.getUsername()));
    }

    @PutMapping("/goal")
    @Operation(
            summary = "Update daily water goal",
            description = "Updates the authenticated user's daily hydration target in milliliters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Water goal updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<WaterGoalDto> updateGoal(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid WaterGoalRequestDto request) {
        return ResponseEntity.ok(waterTrackingService.updateGoal(userDetails.getUsername(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a water log",
            description = "Deletes a water intake entry owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Water log deleted.", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Water log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteWaterLog(
            @Parameter(description = "Water log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        waterTrackingService.deleteWaterLog(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reminder-settings")
    @Operation(
            summary = "Get water reminder settings",
            description = "Returns the authenticated user's water reminder preferences. Reminders currently create in-app notifications; mobile push delivery can attach to the same reminder event later."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Water reminder settings returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<WaterReminderSettingsDto> getReminderSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(waterTrackingService.getReminderSettings(userDetails.getUsername()));
    }

    @PutMapping("/reminder-settings")
    @Operation(
            summary = "Update water reminder settings",
            description = "Enables or disables water reminders and configures the reminder interval and daily active time window."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Water reminder settings updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<WaterReminderSettingsDto> updateReminderSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid WaterReminderSettingsRequestDto request) {
        return ResponseEntity.ok(waterTrackingService.updateReminderSettings(userDetails.getUsername(), request));
    }
}

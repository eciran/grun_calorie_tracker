package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.HealthConnectionDto;
import com.grun.calorietracker.dto.HealthConnectionRequestDto;
import com.grun.calorietracker.dto.HealthDataDeleteResponseDto;
import com.grun.calorietracker.dto.HealthDailySummaryDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncResponseDto;
import com.grun.calorietracker.dto.HealthMetricSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricSyncResponseDto;
import com.grun.calorietracker.dto.HealthRangeSummaryDto;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.service.HealthIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Health Integrations", description = "Mobile health provider connection and normalized metric sync endpoints.")
public class HealthIntegrationController {

    private final HealthIntegrationService healthIntegrationService;

    @GetMapping("/connections")
    @Operation(
            summary = "List connected health providers",
            description = "Returns third-party health provider connection states for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health connections returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<List<HealthConnectionDto>> getConnections(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.getConnections(userDetails.getUsername()));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Get daily health summary",
            description = "Returns daily aggregated health metrics synced from connected mobile health providers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health summary returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<HealthDailySummaryDto> getDailySummary(
            @Parameter(description = "Summary date. Defaults to today.", example = "2026-05-26")
            @RequestParam(required = false) LocalDate date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.getDailySummary(userDetails.getUsername(), date));
    }

    @GetMapping("/summary/range")
    @Operation(
            summary = "Get health summary for a date range",
            description = "Returns daily health summaries and aggregate totals for charts. Date range cannot exceed 90 days."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health range summary returned."),
            @ApiResponse(responseCode = "400", description = "Date range is invalid."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<HealthRangeSummaryDto> getRangeSummary(
            @Parameter(description = "Range start date. Defaults to 6 days before endDate.", example = "2026-05-20")
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Range end date. Defaults to today.", example = "2026-05-26")
            @RequestParam(required = false) LocalDate endDate,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.getRangeSummary(userDetails.getUsername(), startDate, endDate));
    }

    @PutMapping("/connections/{provider}")
    @Operation(
            summary = "Mark a health provider as connected",
            description = "Called after the mobile app obtains provider permission on-device. Apple Health and Health Connect permissions remain client-side; backend stores the connection state and accepts normalized sync payloads."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health provider connected."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<HealthConnectionDto> connect(
            @Parameter(description = "Provider to connect.", example = "APPLE_HEALTH") @PathVariable HealthProvider provider,
            @RequestBody(required = false) @Valid HealthConnectionRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.connect(userDetails.getUsername(), provider, request));
    }

    @DeleteMapping("/connections/{provider}")
    @Operation(
            summary = "Disconnect a health provider",
            description = "Marks the provider as disconnected for the authenticated user. Existing synced metrics are preserved for history integrity."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health provider disconnected."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<HealthConnectionDto> disconnect(
            @Parameter(description = "Provider to disconnect.", example = "APPLE_HEALTH") @PathVariable HealthProvider provider,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.disconnect(userDetails.getUsername(), provider));
    }

    @PostMapping("/{provider}/metrics")
    @Operation(
            summary = "Sync a normalized health metric",
            description = "Stores or updates a normalized metric sent by the mobile app. Use a stable externalId for idempotent sync."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health metric synced."),
            @ApiResponse(responseCode = "400", description = "Payload is invalid or provider is not connected."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<HealthMetricSyncResponseDto> syncMetric(
            @Parameter(description = "Provider that produced the metric.", example = "APPLE_HEALTH") @PathVariable HealthProvider provider,
            @RequestBody @Valid HealthMetricSyncRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.syncMetric(userDetails.getUsername(), provider, request));
    }

    @PostMapping("/{provider}/metrics/batch")
    @Operation(
            summary = "Sync normalized health metrics in batch",
            description = "Stores or updates multiple normalized metrics sent by the mobile app. Use this for background sync instead of one request per sample."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health metrics synced."),
            @ApiResponse(responseCode = "400", description = "Payload is invalid or provider is not connected."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<HealthMetricBatchSyncResponseDto> syncMetrics(
            @Parameter(description = "Provider that produced the metrics.", example = "APPLE_HEALTH") @PathVariable HealthProvider provider,
            @RequestBody @Valid HealthMetricBatchSyncRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.syncMetrics(userDetails.getUsername(), provider, request));
    }

    @DeleteMapping("/{provider}/data")
    @Operation(
            summary = "Delete synced health data for one provider",
            description = "Deletes stored metric rows for the provider and marks the provider connection as revoked."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider health data deleted."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<HealthDataDeleteResponseDto> deleteProviderData(
            @Parameter(description = "Provider whose synced data should be deleted.", example = "APPLE_HEALTH") @PathVariable HealthProvider provider,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.deleteProviderData(userDetails.getUsername(), provider));
    }

    @DeleteMapping("/data")
    @Operation(
            summary = "Delete all synced health data",
            description = "Deletes all stored health metric rows for the authenticated user and marks all health provider connections as revoked."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All health data deleted."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<HealthDataDeleteResponseDto> deleteAllHealthData(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthIntegrationService.deleteAllHealthData(userDetails.getUsername()));
    }
}

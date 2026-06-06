package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminMailMonitoringDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.service.AdminMailMonitoringService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/mail")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Mail Monitoring", description = "Admin-only safe mail provider monitoring endpoints. Secrets are never returned.")
public class AdminMailMonitoringController {

    private final AdminMailMonitoringService adminMailMonitoringService;

    @GetMapping("/monitoring")
    @Operation(
            summary = "Get mail provider monitoring",
            description = "Returns safe mail provider status, aggregated transactional counters and recent events for admin monitoring."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mail monitoring data returned.",
                    content = @Content(schema = @Schema(implementation = AdminMailMonitoringDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdminMailMonitoringDto> getMonitoring(
            @Parameter(description = "Lookback window in days. Capped at 30.", example = "7")
            @RequestParam(defaultValue = "7") int days,
            @Parameter(description = "Recent event limit. Capped at 50.", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(adminMailMonitoringService.getMonitoring(days, limit));
    }
}

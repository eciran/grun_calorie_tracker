package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminDashboardSummaryDto;
import com.grun.calorietracker.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Dashboard", description = "Admin-only summary metrics for users and food catalog quality.")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/summary")
    @Operation(
            summary = "Get admin dashboard summary",
            description = "Returns high-level user counts and food catalog quality metrics for admin monitoring."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Admin dashboard summary returned.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminDashboardSummaryDto.class))
            ),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content)
    })
    public ResponseEntity<AdminDashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(adminDashboardService.getSummary());
    }
}

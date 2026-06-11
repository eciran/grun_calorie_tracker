package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.DashboardService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Dashboard summary endpoints")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;
    private final UserTimeZoneSupport userTimeZoneSupport;

    @GetMapping("/daily-summary")
    @Operation(
            summary = "Get daily dashboard summary",
            description = "Returns daily calorie, macro, exercise, weight, and goal summary for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daily dashboard summary returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<DailySummaryDto> getDailySummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Optional summary date. Defaults to today.", example = "2026-05-15")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : userTimeZoneSupport.today(currentUser(userDetails.getUsername()));
        DailySummaryDto response = dashboardService.getDailySummary(userDetails.getUsername(), targetDate);
        return ResponseEntity.ok(response);
    }

    private UserEntity currentUser(String email) {
        return userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }
}

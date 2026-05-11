package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.service.DashboardService;
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
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard summary endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/daily-summary")
    @Operation(summary = "Get daily dashboard summary")
    public ResponseEntity<DailySummaryDto> getDailySummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        DailySummaryDto response = dashboardService.getDailySummary(userDetails.getUsername(), targetDate);
        return ResponseEntity.ok(response);
    }
}
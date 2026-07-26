package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.service.SleepTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sleep")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Sleep", description = "Canonical sleep sessions, stages, goals and summaries.")
public class SleepTrackingController {

    private final SleepTrackingService service;

    @PostMapping("/sessions")
    @Operation(summary = "Create a manual sleep session", description = "Duration, sleep date and quality are calculated by the backend.")
    public ResponseEntity<SleepSessionDto> createManual(
            @RequestBody @Valid SleepSessionRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createManual(userDetails.getUsername(), request));
    }

    @PostMapping("/providers/{provider}/sessions")
    @Operation(summary = "Synchronize a provider sleep session", description = "Provider plus externalId is idempotent and requires an active health connection.")
    public ResponseEntity<SleepSessionDto> syncProvider(
            @PathVariable HealthProvider provider,
            @RequestBody @Valid SleepSessionRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.syncProvider(userDetails.getUsername(), provider, request));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List sleep sessions")
    public ResponseEntity<List<SleepSessionDto>> list(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.list(userDetails.getUsername(), start, end));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Delete a manual sleep session")
    public ResponseEntity<Void> deleteManual(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        service.deleteManual(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/goal")
    @Operation(summary = "Get the sleep goal", description = "Returns an eight-hour default when no goal has been saved.")
    public ResponseEntity<SleepGoalDto> getGoal(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.getGoal(userDetails.getUsername()));
    }

    @PutMapping("/goal")
    @Operation(summary = "Create or update the sleep goal")
    public ResponseEntity<SleepGoalDto> upsertGoal(
            @RequestBody @Valid SleepGoalRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.upsertGoal(userDetails.getUsername(), request));
    }

    @GetMapping("/summary/daily")
    @Operation(summary = "Get a daily sleep summary")
    public ResponseEntity<SleepDailySummaryDto> dailySummary(
            @RequestParam LocalDate date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.dailySummary(userDetails.getUsername(), date));
    }

    @GetMapping("/summary/weekly")
    @Operation(summary = "Get a seven-day sleep summary")
    public ResponseEntity<SleepWeeklySummaryDto> weeklySummary(
            @RequestParam LocalDate end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.weeklySummary(userDetails.getUsername(), end));
    }
}

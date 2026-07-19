package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiWorkoutPlanConfirmRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftResponseDto;
import com.grun.calorietracker.dto.AiWorkoutPlanCreditEstimateDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.WorkoutPlanDto;
import com.grun.calorietracker.dto.WorkoutPlanScheduleUpdateRequestDto;
import com.grun.calorietracker.service.AiWorkoutPlanService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/workout-plans")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Workout Plans", description = "AI-generated workout plan drafts. Drafts require user review before activation.")
public class AiWorkoutPlanController {

    private final AiWorkoutPlanService aiWorkoutPlanService;

    @GetMapping("/credit-cost")
    @Operation(summary = "Estimate workout-plan AI credit cost",
            description = "Calculates the backend-authoritative cost from training days and session length without consuming credit.")
    public ResponseEntity<AiWorkoutPlanCreditEstimateDto> estimateCreditCost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int daysPerWeek,
            @RequestParam int minutesPerSession) {
        return ResponseEntity.ok(aiWorkoutPlanService.estimateCreditCost(
                userDetails.getUsername(), daysPerWeek, minutesPerSession));
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate an AI workout plan draft", description = "Creates a reviewed-first AI workout plan draft. It does not create exercise logs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workout plan draft created.", content = @Content(schema = @Schema(implementation = AiWorkoutPlanDraftResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "AI provider disabled, quota unavailable, or request invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiWorkoutPlanDraftResponseDto> generateDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AiWorkoutPlanDraftRequestDto request) {
        return ResponseEntity.ok(aiWorkoutPlanService.createDraft(userDetails.getUsername(), request));
    }

    @PostMapping("/{requestId}/confirm")
    @Operation(summary = "Confirm an AI workout plan draft", description = "Persists the user-reviewed draft as an active workout plan snapshot.")
    public ResponseEntity<WorkoutPlanDto> confirmDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long requestId,
            @RequestBody @Valid AiWorkoutPlanConfirmRequestDto request) {
        return ResponseEntity.ok(aiWorkoutPlanService.confirmDraft(userDetails.getUsername(), requestId, request));
    }

    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject an AI workout plan draft", description = "Closes the draft without creating an active workout plan.")
    public ResponseEntity<Void> rejectDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long requestId) {
        aiWorkoutPlanService.rejectDraft(userDetails.getUsername(), requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List workout plans", description = "Returns user-owned workout plan snapshots. Archived plans are included when requested.")
    public ResponseEntity<List<WorkoutPlanDto>> listPlans(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(includeInactive
                ? aiWorkoutPlanService.listAllPlans(userDetails.getUsername())
                : aiWorkoutPlanService.listActivePlans(userDetails.getUsername()));
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Get workout plan detail", description = "Returns a single user-owned workout plan snapshot.")
    public ResponseEntity<WorkoutPlanDto> getPlan(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId) {
        return ResponseEntity.ok(aiWorkoutPlanService.getPlan(userDetails.getUsername(), planId));
    }

    @PutMapping("/{planId}/schedule")
    @Operation(summary = "Save workout schedule", description = "Stores user-confirmed dates, optional approximate start times, and intensity for every workout day. This trusted schedule can be used by workout-aligned nutrition plans.")
    public ResponseEntity<WorkoutPlanDto> updateSchedule(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId,
            @RequestBody @Valid WorkoutPlanScheduleUpdateRequestDto request) {
        return ResponseEntity.ok(aiWorkoutPlanService.updateSchedule(
                userDetails.getUsername(), planId, request));
    }
    @DeleteMapping("/{planId}")
    @Operation(summary = "Archive workout plan", description = "Archives a user-owned workout plan. The snapshot remains stored for audit/history.")
    public ResponseEntity<Void> archivePlan(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId) {
        aiWorkoutPlanService.archivePlan(userDetails.getUsername(), planId);
        return ResponseEntity.noContent().build();
    }
}

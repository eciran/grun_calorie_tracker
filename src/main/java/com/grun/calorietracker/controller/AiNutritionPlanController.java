package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiNutritionPlanConfirmRequestDto;
import com.grun.calorietracker.dto.AiNutritionPlanCreditEstimateDto;
import com.grun.calorietracker.dto.AiNutritionPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiNutritionPlanDraftResponseDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.enums.NutritionPlanGenerationMode;
import com.grun.calorietracker.service.AiNutritionPlanService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/nutrition-plans")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Nutrition Plans",
        description = "Review-first AI nutrition plans backed by immutable nutrition snapshots.")
public class AiNutritionPlanController {

    private final AiNutritionPlanService service;

    @GetMapping("/credit-cost")
    @Operation(summary = "Estimate nutrition-plan AI credit cost",
            description = "Returns the backend-authoritative credit cost for the current plan, requested duration, and generation mode. No provider call is made and no credit is consumed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credit cost calculated.",
                    content = @Content(schema = @Schema(implementation = AiNutritionPlanCreditEstimateDto.class))),
            @ApiResponse(responseCode = "400", description = "Day count must be between 1 and 7.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiNutritionPlanCreditEstimateDto> estimateCreditCost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            @RequestParam int dayCount,
            @RequestParam(defaultValue = "GENERAL") NutritionPlanGenerationMode generationMode) {
        return ResponseEntity.ok(service.estimateCreditCost(
                user.getUsername(), dayCount, generationMode));
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate an AI nutrition-plan draft",
            description = "Uses backend-owned profile and nutrition targets. A successful usable draft consumes the duration-weighted credit cost returned by the credit-cost endpoint; failed generation does not consume credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nutrition-plan draft created.",
                    content = @Content(schema = @Schema(implementation = AiNutritionPlanDraftResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Request, onboarding, feature, or quota validation failed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "502", description = "AI provider did not return a usable plan.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiNutritionPlanDraftResponseDto> generate(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid AiNutritionPlanDraftRequestDto request) {
        return ResponseEntity.ok(service.createDraft(
                user.getUsername(), idempotencyKey, request));
    }

    @PostMapping("/{requestId}/confirm")
    @Operation(summary = "Confirm a reviewed nutrition-plan draft",
            description = "Creates an immutable snapshot meal plan. It does not create food diary entries.")
    public ResponseEntity<MealPlanDto> confirm(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            @PathVariable Long requestId,
            @RequestBody @Valid AiNutritionPlanConfirmRequestDto request) {
        return ResponseEntity.ok(service.confirmDraft(user.getUsername(), requestId, request));
    }

    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject a nutrition-plan draft",
            description = "Stores optional quality feedback without automatically refunding used quota.")
    public ResponseEntity<Void> reject(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            @PathVariable Long requestId,
            @RequestBody(required = false) @Valid AiMealDraftRejectRequestDto request) {
        service.rejectDraft(user.getUsername(), requestId, request);
        return ResponseEntity.noContent().build();
    }
}
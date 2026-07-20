package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.service.MealPlanService;
import com.grun.calorietracker.service.MealPlanTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/meal-plans")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Meal Planner", description = "Weekly meal planning, active-plan tracking, and grocery list generation.")
public class MealPlanController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final MealPlanService mealPlanService;
    private final MealPlanTrackingService mealPlanTrackingService;

    @PostMapping
    @Operation(summary = "Create meal plan", description = "Creates a weekly meal plan from recipes, food items, or immutable nutrition snapshots.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meal plan created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<MealPlanDto> createMealPlan(@AuthenticationPrincipal UserDetails userDetails,
                                                      @RequestBody @Valid MealPlanRequestDto request) {
        return ResponseEntity.ok(mealPlanService.createMealPlan(userDetails.getUsername(), request));
    }

    @GetMapping
    @Operation(summary = "List meal plans", description = "Returns non-archived meal plans owned by the authenticated user.")
    public ResponseEntity<List<MealPlanDto>> getMealPlans(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(mealPlanService.getMealPlans(userDetails.getUsername()));
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Get meal plan", description = "Returns one meal plan owned by the authenticated user.")
    public ResponseEntity<MealPlanDto> getMealPlan(@AuthenticationPrincipal UserDetails userDetails,
                                                   @PathVariable Long planId) {
        return ResponseEntity.ok(mealPlanService.getMealPlan(userDetails.getUsername(), planId));
    }

    @PutMapping("/{planId}")
    @Operation(summary = "Update meal plan", description = "Replaces meal plan metadata and items.")
    public ResponseEntity<MealPlanDto> updateMealPlan(@AuthenticationPrincipal UserDetails userDetails,
                                                      @PathVariable Long planId,
                                                      @RequestBody @Valid MealPlanRequestDto request) {
        return ResponseEntity.ok(mealPlanService.updateMealPlan(userDetails.getUsername(), planId, request));
    }

    @PostMapping("/{planId}/duplicate")
    @Operation(summary = "Duplicate meal plan", description = "Copies an existing meal plan into a new date range while preserving item day offsets.")
    public ResponseEntity<MealPlanDto> duplicateMealPlan(@AuthenticationPrincipal UserDetails userDetails,
                                                         @PathVariable Long planId,
                                                         @RequestBody @Valid MealPlanDuplicateRequestDto request) {
        return ResponseEntity.ok(mealPlanService.duplicateMealPlan(userDetails.getUsername(), planId, request));
    }

    @PostMapping("/{planId}/activate")
    @Operation(summary = "Activate meal plan", description = "Makes this the user's only active plan. Any previous active plan returns to draft.")
    public ResponseEntity<MealPlanDto> activate(@AuthenticationPrincipal UserDetails userDetails,
                                                @PathVariable Long planId) {
        return ResponseEntity.ok(mealPlanTrackingService.activate(userDetails.getUsername(), planId));
    }

    @PostMapping("/{planId}/deactivate")
    @Operation(summary = "Deactivate meal plan", description = "Returns the active plan to draft without deleting its plan or diary history.")
    public ResponseEntity<MealPlanDto> deactivate(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable Long planId) {
        return ResponseEntity.ok(mealPlanTrackingService.deactivate(userDetails.getUsername(), planId));
    }

    @GetMapping("/active/today")
    @Operation(summary = "Get active plan for a day", description = "Returns planned items and their tracking state. An empty response means normal diary logging remains available.")
    public ResponseEntity<MealPlanTodayDto> getActiveForDate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(mealPlanTrackingService.getActiveForDate(userDetails.getUsername(), date));
    }

    @PostMapping("/{planId}/items/{itemId}/log")
    @Operation(summary = "Log planned item", description = "Adds the user-confirmed quantity to the diary. Repeating the same Idempotency-Key returns the original result.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planned item logged."),
            @ApiResponse(responseCode = "400", description = "Invalid quantity, unit, date, or missing idempotency key.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "The item already has another tracking decision.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<MealPlanItemConsumptionDto> logItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestBody @Valid MealPlanItemLogRequestDto request) {
        return ResponseEntity.ok(mealPlanTrackingService.logItem(
                userDetails.getUsername(), planId, itemId, idempotencyKey, request));
    }

    @PostMapping("/{planId}/meals/log")
    @Operation(summary = "Log a planned meal", description = "Adds every planned item in one date and meal slot to the diary in a single transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planned meal logged."),
            @ApiResponse(responseCode = "400", description = "Invalid meal, date, or idempotency key.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "One or more meal items already have another tracking decision.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<MealPlanMealLogResponseDto> logMeal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId,
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestBody @Valid MealPlanMealLogRequestDto request) {
        return ResponseEntity.ok(mealPlanTrackingService.logMeal(
                userDetails.getUsername(), planId, idempotencyKey, request));
    }

    @PostMapping("/{planId}/items/{itemId}/skip")
    @Operation(summary = "Skip planned item", description = "Marks the item as skipped without writing nutrition to the diary.")
    public ResponseEntity<MealPlanItemConsumptionDto> skipItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey) {
        return ResponseEntity.ok(mealPlanTrackingService.skipItem(
                userDetails.getUsername(), planId, itemId, idempotencyKey));
    }

    @PostMapping("/{planId}/items/{itemId}/replace")
    @Operation(summary = "Replace planned item", description = "Links one existing same-day diary record as the user's replacement choice.")
    public ResponseEntity<MealPlanItemConsumptionDto> replaceItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestBody @Valid MealPlanItemReplaceRequestDto request) {
        return ResponseEntity.ok(mealPlanTrackingService.replaceItem(
                userDetails.getUsername(), planId, itemId, idempotencyKey, request));
    }

    @GetMapping("/{planId}/grocery-list")
    @Operation(summary = "Generate grocery list", description = "Aggregates planned recipe ingredients and planned food items into a grocery list.")
    public ResponseEntity<GroceryListDto> getGroceryList(@AuthenticationPrincipal UserDetails userDetails,
                                                         @PathVariable Long planId) {
        return ResponseEntity.ok(mealPlanService.getGroceryList(userDetails.getUsername(), planId));
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "Archive meal plan", description = "Soft-deletes a meal plan owned by the authenticated user.")
    public ResponseEntity<Void> archiveMealPlan(@AuthenticationPrincipal UserDetails userDetails,
                                                @PathVariable Long planId) {
        mealPlanService.archiveMealPlan(userDetails.getUsername(), planId);
        return ResponseEntity.noContent().build();
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.dto.MealPlanDuplicateRequestDto;
import com.grun.calorietracker.dto.MealPlanRequestDto;
import com.grun.calorietracker.service.MealPlanService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meal-plans")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Meal Planner", description = "Weekly meal planning and grocery list generation.")
public class MealPlanController {

    private final MealPlanService mealPlanService;

    @PostMapping
    @Operation(summary = "Create meal plan", description = "Creates a weekly meal plan from recipes and food items.")
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

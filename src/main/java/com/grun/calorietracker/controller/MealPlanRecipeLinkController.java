package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.MealPlanRecipeCandidateDto;
import com.grun.calorietracker.dto.MealPlanRecipeLinkDto;
import com.grun.calorietracker.service.MealPlanRecipeLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meal-plans/{planId}/items/{itemId}/recipe-link")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Meal Planner", description = "Weekly meal planning, active-plan tracking, and grocery list generation.")
public class MealPlanRecipeLinkController {

    private final MealPlanRecipeLinkService mealPlanRecipeLinkService;

    @GetMapping("/candidates")
    @Operation(summary = "Find optional recipe candidates", description = "Returns a small list of owned or verified regional recipes without changing the meal-plan snapshot or consuming AI quota.")
    public ResponseEntity<List<MealPlanRecipeCandidateDto>> findCandidates(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(mealPlanRecipeLinkService.findCandidates(
                userDetails.getUsername(), planId, itemId, limit));
    }

    @PostMapping("/{recipeId}")
    @Operation(summary = "Link a confirmed recipe", description = "Links an accessible recipe after explicit user selection. Snapshot nutrition and preparation data remain unchanged.")
    public ResponseEntity<MealPlanRecipeLinkDto> linkRecipe(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @PathVariable Long recipeId) {
        return ResponseEntity.ok(mealPlanRecipeLinkService.linkRecipe(
                userDetails.getUsername(), planId, itemId, recipeId));
    }

    @DeleteMapping
    @Operation(summary = "Remove optional recipe link", description = "Removes only recipe navigation metadata and preserves the immutable meal-plan snapshot.")
    public ResponseEntity<MealPlanRecipeLinkDto> unlinkRecipe(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long planId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(mealPlanRecipeLinkService.unlinkRecipe(
                userDetails.getUsername(), planId, itemId));
    }
}
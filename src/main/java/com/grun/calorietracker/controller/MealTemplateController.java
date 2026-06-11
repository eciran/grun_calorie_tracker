package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.service.MealTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meal-templates")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Meal Templates", description = "User-saved reusable meals separated from recent diary history.")
public class MealTemplateController {

    private final MealTemplateService mealTemplateService;

    @PostMapping
    @Operation(summary = "Save a meal template", description = "Stores a reusable template from one meal already logged in the user's diary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meal template saved."),
            @ApiResponse(responseCode = "400", description = "Request is invalid or the source meal has no entries."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<MealTemplateDto> createTemplate(
            @RequestBody @Valid MealTemplateCreateRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(mealTemplateService.createFromLoggedMeal(userDetails.getUsername(), request));
    }

    @GetMapping
    @Operation(summary = "List saved meal templates", description = "Returns explicit user-saved reusable meals.")
    public ResponseEntity<List<MealTemplateDto>> getTemplates(
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "50")
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(mealTemplateService.getTemplates(userDetails.getUsername(), page, size));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a saved meal template",
            description = "Replaces a saved template name, default meal type, and editable food item list."
    )
    public ResponseEntity<MealTemplateDto> updateTemplate(
            @Parameter(description = "Saved meal template id.", example = "3") @PathVariable Long id,
            @RequestBody @Valid MealTemplateUpdateRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(mealTemplateService.updateTemplate(userDetails.getUsername(), id, request));
    }

    @PostMapping({"/{id}/log", "/{id}/apply"})
    @Operation(summary = "Apply a saved meal template", description = "Adds all template items to a target diary day. /apply is the preferred mobile path; /log remains supported for compatibility.")
    public ResponseEntity<List<FoodLogsDto>> applyTemplate(
            @Parameter(description = "Saved meal template id.", example = "3") @PathVariable Long id,
            @RequestBody @Valid MealTemplateApplyRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(mealTemplateService.applyTemplate(userDetails.getUsername(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved meal template", description = "Deletes a reusable meal template owned by the authenticated user.")
    public ResponseEntity<Void> deleteTemplate(
            @Parameter(description = "Saved meal template id.", example = "3") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        mealTemplateService.deleteTemplate(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}

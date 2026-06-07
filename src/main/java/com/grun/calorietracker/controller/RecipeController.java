package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeInteractionDto;
import com.grun.calorietracker.dto.RecipeInteractionRequestDto;
import com.grun.calorietracker.dto.RecipeLogDto;
import com.grun.calorietracker.dto.RecipeLogRequestDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.service.RecipeLogService;
import com.grun.calorietracker.service.RecipeService;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recipes", description = "Authenticated custom recipe builder for home-cooked meals.")
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipeLogService recipeLogService;

    @PostMapping
    @Operation(summary = "Create a custom recipe", description = "Creates a private recipe for the authenticated user and calculates nutrition snapshots from ingredient gram amounts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Ingredient food item was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeDto> createRecipe(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                  @RequestBody @Valid RecipeRequestDto request) {
        return ResponseEntity.ok(recipeService.createRecipe(userDetails.getUsername(), request));
    }

    @GetMapping
    @Operation(summary = "List my custom recipes", description = "Returns non-archived private recipes owned by the authenticated user. Supports optional name search and meal type filtering.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipes returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<RecipeDto>> getMyRecipes(
            @Parameter(description = "Optional case-insensitive name search.", example = "soup") @RequestParam(required = false) String query,
            @Parameter(description = "Optional meal type filter.", example = "LUNCH") @RequestParam(required = false) String mealType,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(recipeService.getMyRecipes(userDetails.getUsername(), query, mealType));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get my custom recipe", description = "Returns one non-archived recipe owned by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeDto> getRecipe(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                               @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(recipeService.getRecipe(userDetails.getUsername(), id));
    }

    @GetMapping("/saved")
    @Operation(summary = "List saved recipes", description = "Returns recipes saved by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saved recipes returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<RecipeDto>> getSavedRecipes(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(recipeService.getSavedRecipes(userDetails.getUsername()));
    }

    @GetMapping("/favorites")
    @Operation(summary = "List favorite recipes", description = "Returns recipes favorited by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Favorite recipes returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<RecipeDto>> getFavoriteRecipes(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(recipeService.getFavoriteRecipes(userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update my custom recipe", description = "Replaces recipe metadata and ingredients, then recalculates nutrition snapshots.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe or ingredient food item was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeDto> updateRecipe(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                  @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id,
                                                  @RequestBody @Valid RecipeRequestDto request) {
        return ResponseEntity.ok(recipeService.updateRecipe(userDetails.getUsername(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive my custom recipe", description = "Soft-deletes an owned recipe by marking it archived. Existing future diary recipe logs will use nutrition snapshots and should not be recalculated retroactively.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recipe archived."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> archiveRecipe(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                              @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id) {
        recipeService.archiveRecipe(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/interaction")
    @Operation(summary = "Update recipe interaction", description = "Saves, favorites, or rates an accessible recipe for the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe interaction updated.", content = @Content(schema = @Schema(implementation = RecipeInteractionDto.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeInteractionDto> updateRecipeInteraction(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id,
            @RequestBody @Valid RecipeInteractionRequestDto request) {
        return ResponseEntity.ok(recipeService.updateInteraction(userDetails.getUsername(), id, request));
    }

    @DeleteMapping("/{id}/interaction")
    @Operation(summary = "Clear recipe interaction", description = "Removes saved, favorite, and rating state for one accessible recipe.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recipe interaction cleared."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> clearRecipeInteraction(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id) {
        recipeService.clearInteraction(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/logs")
    @Operation(summary = "Log a recipe to diary", description = "Adds the selected recipe to the authenticated user's diary and captures recipe nutrition as an immutable log snapshot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe log created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeLogDto> logRecipe(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                  @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id,
                                                  @RequestBody @Valid RecipeLogRequestDto request) {
        return ResponseEntity.ok(recipeLogService.logRecipe(userDetails.getUsername(), id, request));
    }

    @GetMapping("/logs")
    @Operation(summary = "List recipe diary logs", description = "Returns recipe diary logs for one day or a date range. The end date is inclusive.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe logs returned."),
            @ApiResponse(responseCode = "400", description = "Date range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<RecipeLogDto>> getRecipeLogs(
            @Parameter(description = "Single diary date in ISO format.", example = "2026-06-06") @RequestParam(required = false) String date,
            @Parameter(description = "Range start date in ISO format.", example = "2026-06-01") @RequestParam(required = false) String start,
            @Parameter(description = "Range end date in ISO format.", example = "2026-06-06") @RequestParam(required = false) String end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LocalDate startDate;
        LocalDate endDate;
        if (date != null && !date.isBlank()) {
            startDate = LocalDate.parse(date);
            endDate = startDate;
        } else {
            if (start == null || start.isBlank() || end == null || end.isBlank()) {
                throw new IllegalArgumentException("Use either date or both start and end parameters.");
            }
            startDate = LocalDate.parse(start);
            endDate = LocalDate.parse(end);
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Recipe log end date must not be before start date.");
        }
        return ResponseEntity.ok(recipeLogService.getRecipeLogs(
                userDetails.getUsername(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        ));
    }

    @PutMapping("/logs/{logId}")
    @Operation(summary = "Update a recipe diary log", description = "Updates meal type, log date, and consumed amount for one recipe diary log owned by the authenticated user. Nutrition snapshot is recalculated from the recipe snapshot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe log updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe log was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeLogDto> updateRecipeLog(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                        @Parameter(description = "Recipe log id.", example = "1") @PathVariable Long logId,
                                                        @RequestBody @Valid RecipeLogRequestDto request) {
        return ResponseEntity.ok(recipeLogService.updateRecipeLog(userDetails.getUsername(), logId, request));
    }

    @DeleteMapping("/logs/{logId}")
    @Operation(summary = "Delete a recipe diary log", description = "Deletes one recipe log owned by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recipe log deleted."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe log was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteRecipeLog(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                @Parameter(description = "Recipe log id.", example = "1") @PathVariable Long logId) {
        recipeLogService.deleteRecipeLog(userDetails.getUsername(), logId);
        return ResponseEntity.noContent().build();
    }
}

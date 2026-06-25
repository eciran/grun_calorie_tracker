package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeInteractionDto;
import com.grun.calorietracker.dto.RecipeInteractionRequestDto;
import com.grun.calorietracker.dto.RecipeCategoryDto;
import com.grun.calorietracker.dto.RecipeLogDto;
import com.grun.calorietracker.dto.RecipeLogRequestDto;
import com.grun.calorietracker.dto.RecipePageDto;
import com.grun.calorietracker.dto.RecipeReportDto;
import com.grun.calorietracker.dto.RecipeReportRequestDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipePublicSort;
import com.grun.calorietracker.service.RecipeImageService;
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
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recipes", description = "Authenticated custom recipe builder for home-cooked meals.")
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipeLogService recipeLogService;
    private final RecipeImageService recipeImageService;

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

    @GetMapping("/public")
    @Operation(summary = "Discover public recipes", description = "Returns admin-approved public recipes. Supports query, meal type, region, language, and category filtering for mobile discovery screens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public recipes returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipePageDto> getPublicRecipes(
            @Parameter(description = "Optional case-insensitive name or description search.", example = "soup") @RequestParam(required = false) String query,
            @Parameter(description = "Optional meal type filter.", example = "LUNCH") @RequestParam(required = false) String mealType,
            @Parameter(description = "Optional region filter.", example = "TR") @RequestParam(required = false) MarketRegion marketRegion,
            @Parameter(description = "Optional language code filter.", example = "tr") @RequestParam(required = false) String language,
            @Parameter(description = "Required categories. Recipe must include all selected categories.", example = "VEGAN,HIGH_PROTEIN") @RequestParam(required = false) Set<RecipeCategory> categories,
            @Parameter(description = "Public recipe sort mode.", example = "POPULAR") @RequestParam(defaultValue = "NEWEST") RecipePublicSort sort,
            @Parameter(description = "Page number.", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size.", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(recipeService.getPublicRecipes(
                userDetails.getUsername(),
                query,
                mealType,
                marketRegion,
                language,
                categories,
                sort,
                page,
                size
        ));
    }

    @GetMapping("/public/categories")
    @Operation(summary = "List public recipe categories", description = "Returns stable category codes and default labels for mobile public recipe filters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe categories returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<RecipeCategoryDto>> getPublicRecipeCategories() {
        return ResponseEntity.ok(Arrays.stream(RecipeCategory.values())
                .map(category -> new RecipeCategoryDto(category, toCategoryLabel(category)))
                .toList());
    }

    @GetMapping("/public/{id}")
    @Operation(summary = "Get public recipe", description = "Returns one admin-approved public recipe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public recipe returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Public recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeDto> getPublicRecipe(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Public recipe id.", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(recipeService.getPublicRecipe(userDetails.getUsername(), id));
    }

    @PostMapping("/public/{id}/copy")
    @Operation(summary = "Copy public recipe", description = "Copies an admin-approved public recipe into the authenticated user's private recipe list.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe copied to private list."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Public recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeDto> copyPublicRecipe(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Public recipe id.", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(recipeService.copyPublicRecipe(userDetails.getUsername(), id));
    }

    @PostMapping("/public/{id}/report")
    @Operation(summary = "Report public recipe", description = "Creates or updates the authenticated user's open report for one public recipe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe report accepted.", content = @Content(schema = @Schema(implementation = RecipeReportDto.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Public recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeReportDto> reportPublicRecipe(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Public recipe id.", example = "1") @PathVariable Long id,
            @RequestBody @Valid RecipeReportRequestDto request) {
        return ResponseEntity.ok(recipeService.reportPublicRecipe(userDetails.getUsername(), id, request));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload recipe image", description = "Stores a JPEG, PNG, or WebP image for an owned recipe. The image becomes USER_UPLOAD and NEEDS_REVIEW. Admin approval is required only before public discovery.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe image uploaded and recipe returned."),
            @ApiResponse(responseCode = "400", description = "File is missing, too large, or has an unsupported content type.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe was not found or is not owned by the authenticated user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeDto> uploadRecipeImage(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Owned recipe id.", example = "1") @PathVariable Long id,
            @Parameter(description = "Recipe image file. Supported content types are image/jpeg, image/png, and image/webp.")
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(recipeImageService.uploadRecipeImage(userDetails.getUsername(), id, file));
    }

    @GetMapping("/images/{filename}")
    @Operation(summary = "Get uploaded recipe image", description = "Returns a public recipe image by storage filename.")
    public ResponseEntity<Resource> getRecipeImage(@PathVariable String filename) {
        Resource resource = recipeImageService.loadRecipeImage(filename);
        return ResponseEntity.ok()
                .contentType(mediaType(filename))
                .body(resource);
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

    @PostMapping("/{id}/publish-request")
    @Operation(summary = "Request public recipe review", description = "Moves an owned private recipe into the community pending queue. Admin approval is required before it appears in public discovery.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe submitted for review."),
            @ApiResponse(responseCode = "400", description = "Recipe is not ready for publication.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Recipe is already pending review or already published.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeDto> requestRecipePublication(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(recipeService.requestPublication(userDetails.getUsername(), id));
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
    @Operation(summary = "Update a recipe diary log", description = "Updates meal type, log date, and consumed amount for one recipe diary log owned by the authenticated user. Nutrition remains based on the immutable log snapshot captured when it was created.")
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

    private MediaType mediaType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
    private String toCategoryLabel(RecipeCategory category) {
        String value = category.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}

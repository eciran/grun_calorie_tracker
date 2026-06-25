package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminRecipeCreateRequestDto;
import com.grun.calorietracker.dto.AdminRecipeDto;
import com.grun.calorietracker.dto.AdminRecipePageDto;
import com.grun.calorietracker.dto.AdminRecipeReviewRequestDto;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.AdminRecipeService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/recipes")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Recipes", description = "Admin-only recipe monitoring and moderation operations.")
public class AdminRecipeController {

    private final AdminRecipeService adminRecipeService;

    @GetMapping
    @Operation(
            summary = "List recipes for admin review",
            description = "Returns user-created recipes with admin filters. This does not expose a public recipe library; it is for internal monitoring and moderation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipes returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.")
    })
    public ResponseEntity<AdminRecipePageDto> listRecipes(
            @Parameter(description = "Optional recipe name search.", example = "soup")
            @RequestParam(required = false) String query,
            @Parameter(description = "Optional verification status filter.", example = "NEEDS_REVIEW")
            @RequestParam(required = false) VerificationStatus verificationStatus,
            @Parameter(description = "Optional visibility filter.", example = "PRIVATE")
            @RequestParam(required = false) RecipeVisibility visibility,
            @Parameter(description = "Optional archive status filter.", example = "false")
            @RequestParam(required = false) Boolean archived,
            @Parameter(description = "Optional owner email search.", example = "user@example.com")
            @RequestParam(required = false) String ownerEmail,
            @Parameter(description = "Optional meal type filter.", example = "DINNER")
            @RequestParam(required = false) String mealType,
            @Parameter(description = "Optional market region filter.", example = "TR")
            @RequestParam(required = false) MarketRegion marketRegion,
            @Parameter(description = "Optional image moderation status filter.", example = "NEEDS_REVIEW")
            @RequestParam(required = false) ImageStatus imageStatus,
            @Parameter(description = "Optional image source filter.", example = "USER_UPLOAD")
            @RequestParam(required = false) ImageSource imageSource,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25")
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminRecipeService.listRecipes(
                query,
                verificationStatus,
                visibility,
                archived,
                ownerEmail,
                mealType,
                marketRegion,
                imageStatus,
                imageSource,
                page,
                size
        ));
    }


    @PostMapping
    @Operation(
            summary = "Create recipe from admin panel",
            description = "Creates a recipe on behalf of a user or the authenticated admin. Nutrition is calculated from ingredient amounts using the normal recipe builder rules."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Owner or ingredient food item was not found.")
    })
    public ResponseEntity<AdminRecipeDto> createRecipe(
            @RequestBody @Valid AdminRecipeCreateRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(adminRecipeService.createRecipe(
                request,
                userDetails == null ? null : userDetails.getUsername()
        ));
    }
    @GetMapping("/{id}")
    @Operation(summary = "Get recipe detail for admin", description = "Returns recipe owner, metadata, nutrition snapshot, and ingredient details for admin review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.")
    })
    public ResponseEntity<AdminRecipeDto> getRecipe(
            @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(adminRecipeService.getRecipe(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Archive recipe from admin panel",
            description = "Soft-deletes a recipe by marking it archived. Existing recipe diary logs retain their immutable nutrition snapshots."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recipe archived."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.")
    })
    public ResponseEntity<Void> archiveRecipe(
            @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        adminRecipeService.archiveRecipe(id, userDetails == null ? null : userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}/review")
    @Operation(
            summary = "Update recipe admin review state",
            description = "Updates recipe verification, visibility, public categories, image moderation, and archive state for admin review."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe review state updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Recipe was not found.")
    })
    public ResponseEntity<AdminRecipeDto> updateRecipeReview(
            @Parameter(description = "Recipe id.", example = "1") @PathVariable Long id,
            @RequestBody @Valid AdminRecipeReviewRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(adminRecipeService.updateRecipeReview(
                id,
                request,
                userDetails == null ? null : userDetails.getUsername()
        ));
    }
}

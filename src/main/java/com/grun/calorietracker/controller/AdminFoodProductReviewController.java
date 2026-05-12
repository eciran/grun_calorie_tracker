package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.FoodProductReviewService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Product Review", description = "Admin-only product and image review operations for imported food catalog items.")
public class AdminFoodProductReviewController {

    private final FoodProductReviewService foodProductReviewService;

    @GetMapping("/review")
    @Operation(
            summary = "List products pending review",
            description = "Returns imported products filtered by product verification status and image review status. Defaults to RAW_IMPORTED and NEEDS_REVIEW."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products returned for admin review."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.")
    })
    public ResponseEntity<FoodProductReviewPageDto> getProductsForReview(
            @Parameter(description = "Optional product verification status.", example = "RAW_IMPORTED")
            @RequestParam(required = false) VerificationStatus verificationStatus,
            @Parameter(description = "Optional image review status.", example = "NEEDS_REVIEW")
            @RequestParam(required = false) ImageStatus imageStatus,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25")
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(foodProductReviewService.getProductsForReview(
                verificationStatus,
                imageStatus,
                page,
                size
        ));
    }

    @PatchMapping("/{id}/review")
    @Operation(
            summary = "Update product review state",
            description = "Updates curated product name, approved display image URL, product verification status, image source, and image status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product review state updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Product was not found.")
    })
    public ResponseEntity<FoodProductDto> updateProductReview(
            @Parameter(description = "Food product id.", example = "1") @PathVariable Long id,
            @RequestBody @Valid FoodProductReviewRequestDto request) {
        return ResponseEntity.ok(foodProductReviewService.updateProductReview(id, request));
    }
}

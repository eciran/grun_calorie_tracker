package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.FoodProductReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/admin/products", "/api/v1/admin/products"})
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

    @GetMapping("/duplicates")
    @Operation(
            summary = "List duplicate product groups",
            description = "Returns product groups that share the same normalized barcode. This endpoint is for analysis before adding stricter duplicate constraints or merge operations."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Duplicate product groups returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.")
    })
    public ResponseEntity<FoodProductDuplicateGroupPageDto> getDuplicateProductGroups(
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25")
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(foodProductReviewService.getDuplicateProductGroups(page, size));
    }

    @PostMapping("/duplicates/merge")
    @Operation(
            summary = "Merge duplicate products",
            description = "Merges duplicate products that share the same normalized barcode. Food logs and favorites are reassigned to the target product before duplicate products are deleted. The merge is recorded in product review audit history."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Duplicate products merged into the target product."),
            @ApiResponse(responseCode = "400", description = "Request validation failed or products do not share the same normalized barcode."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Target or duplicate product was not found.")
    })
    public ResponseEntity<FoodProductMergeResponseDto> mergeDuplicateProducts(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Target product id and duplicate product ids to merge into it.",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FoodProductMergeRequestDto.class),
                            examples = @ExampleObject(
                                    name = "Merge duplicate Nutella products",
                                    value = """
                                            {
                                              "targetProductId": 1,
                                              "duplicateProductIds": [2, 3]
                                            }
                                            """
                            )
                    )
            )
            @RequestBody @Valid FoodProductMergeRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodProductReviewService.mergeDuplicateProducts(
                request,
                userDetails == null ? null : userDetails.getUsername()
        ));
    }

    @PatchMapping("/{id}/review")
    @Operation(
            summary = "Update product review state",
            description = "Updates curated product name, approved display image URL, product verification status, image source, and image status. A review note is required when rejecting product data or image quality, and every changed field is written to audit history."
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
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Admin product review decision. Send only fields that should change.",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FoodProductReviewRequestDto.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Approve curated product and image",
                                            value = """
                                                    {
                                                      "productName": "Nutella Hazelnut Spread",
                                                      "displayImageUrl": "https://cdn.grun.app/products/3017620422003.jpg",
                                                      "verificationStatus": "VERIFIED",
                                                      "imageSource": "ADMIN_UPLOAD",
                                                      "imageStatus": "APPROVED",
                                                      "reviewNote": "Verified from readable product label."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Reject low quality image",
                                            value = """
                                                    {
                                                      "imageStatus": "REJECTED",
                                                      "reviewNote": "Image is blurry and product label is unreadable."
                                                    }
                                                    """
                                    )
                            }
                    )
            )
            @RequestBody @Valid FoodProductReviewRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodProductReviewService.updateProductReview(
                id,
                request,
                userDetails == null ? null : userDetails.getUsername()
        ));
    }

    @GetMapping("/{id}/audit")
    @Operation(
            summary = "List product review audit history",
            description = "Returns admin review audit entries for a food product ordered by newest change first. Use this to inspect who changed curated fields, status values, image decisions, or duplicate merge state."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Product review audit entries returned.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FoodProductReviewAuditPageDto.class))
            ),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Product was not found.")
    })
    public ResponseEntity<FoodProductReviewAuditPageDto> getProductReviewAudits(
            @Parameter(description = "Food product id.", example = "1") @PathVariable Long id,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25")
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(foodProductReviewService.getProductReviewAudits(id, page, size));
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductNutritionCorrectionImportResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueBackfillResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.dto.FoodSearchAliasDto;
import com.grun.calorietracker.dto.FoodSearchAliasRequestDto;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductImportFormat;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.FoodProductImportService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Product Review", description = "Admin-only product and image review operations for imported food catalog items.")
public class AdminFoodProductReviewController {

    private final FoodProductReviewService foodProductReviewService;
    private final FoodProductImportService foodProductImportService;

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @Operation(
            summary = "Import curated food products from CSV",
            description = "Imports or updates food products by normalized barcode. CURATED_ADMIN marks reviewed admin CSV rows as verified. RAW_EXTERNAL keeps bulk external data in Open Food Facts review state. Required CSV headers: barcode and name. Optional headers include calories, protein, fat, carbs, fiber, sugar, sodium, potassium, cholesterol, calcium, iron, magnesium, zinc, vitamin_a, vitamin_c, vitamin_d, vitamin_e, vitamin_b12, saturated_fat, trans_fat, sugar_alcohol, serving_size_grams, serving_unit, image_url, external_image_url, display_image_url, allergens, and nutri_score."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV import completed."),
            @ApiResponse(responseCode = "400", description = "CSV file is missing or invalid."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.")
    })
    public ResponseEntity<FoodProductImportResultDto> importProducts(
            @Parameter(description = "CSV file containing curated food products.")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Controls whether the CSV is curated admin data or raw external bulk data.", example = "RAW_EXTERNAL")
            @RequestParam(defaultValue = "CURATED_ADMIN") FoodProductImportMode importMode,
            @Parameter(description = "Column mapping format. AUTO detects GRun, Open Food Facts export, or USDA-like files.", example = "AUTO")
            @RequestParam(defaultValue = "AUTO") FoodProductImportFormat importFormat,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodProductImportService.importCsv(
                file,
                userDetails == null ? null : userDetails.getUsername(),
                importMode,
                importFormat
        ));
    }

    @PostMapping("/quality-issues/backfill")
    @Operation(
            summary = "Backfill product quality issues",
            description = "Scans existing food products and rebuilds persistent quality issue records. Use after adding the quality issue table or after large catalog imports."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quality issue backfill completed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.")
    })
    public ResponseEntity<FoodProductQualityIssueBackfillResultDto> backfillQualityIssues(
            @Parameter(description = "Number of products to process per batch. Maximum 1000.", example = "500")
            @RequestParam(defaultValue = "500") @Min(1) @Max(1000) int pageSize,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodProductReviewService.backfillQualityIssues(
                pageSize,
                userDetails == null ? null : userDetails.getUsername()
        ));
    }

    @PostMapping(value = "/nutrition-corrections/import", consumes = "multipart/form-data")
    @Operation(
            summary = "Import product nutrition corrections",
            description = "Applies admin CSV/TSV corrections to existing products matched by id, source_key, or barcode. Use markVerified=true to approve valid imported rows after correction. Supported correction headers include product_name, calories, protein, fat, carbs, fiber, sugar, sodium, potassium, cholesterol, calcium, iron, magnesium, zinc, vitamin_a, vitamin_c, vitamin_d, vitamin_e, vitamin_b12, saturated_fat, trans_fat, sugar_alcohol, serving_size_grams, serving_unit, display_image_url, market_region, catalog_type, verification_status, and image_status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nutrition correction import completed."),
            @ApiResponse(responseCode = "400", description = "Correction file is missing or invalid."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.")
    })
    public ResponseEntity<FoodProductNutritionCorrectionImportResultDto> importNutritionCorrections(
            @Parameter(description = "CSV/TSV file containing product correction rows.")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Validate the file and report candidate rows without applying changes.", example = "true")
            @RequestParam(defaultValue = "false") boolean dryRun,
            @Parameter(description = "Mark successfully imported products as VERIFIED when the row does not provide verification_status.", example = "true")
            @RequestParam(defaultValue = "false") boolean markVerified,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodProductReviewService.importNutritionCorrections(
                file,
                userDetails == null ? null : userDetails.getUsername(),
                dryRun,
                markVerified
        ));
    }

    @GetMapping(value = "/review/export", produces = "text/csv")
    @Operation(
            summary = "Export products matching review filters",
            description = "Exports the current admin review filter as a CSV that can be corrected externally and re-imported through the nutrition correction import endpoint. The export is capped at 10,000 rows."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV export returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.")
    })
    public ResponseEntity<byte[]> exportProductsForReview(
            @Parameter(description = "Optional product verification status.", example = "RAW_IMPORTED")
            @RequestParam(required = false) VerificationStatus verificationStatus,
            @Parameter(description = "Optional image review status.", example = "NEEDS_REVIEW")
            @RequestParam(required = false) ImageStatus imageStatus,
            @Parameter(description = "Optional market region filter. Supported values: GLOBAL, TR, UK_IE, EU.", example = "UK_IE")
            @RequestParam(required = false) MarketRegion region,
            @Parameter(description = "Optional catalog type filter.", example = "LOCAL_DISH")
            @RequestParam(required = false) FoodCatalogType catalogType,
            @Parameter(description = "Optional data source filter.", example = "OPEN_FOOD_FACTS")
            @RequestParam(required = false) FoodDataSource dataSource,
            @Parameter(description = "Optional derived quality issue filter.", example = "MISSING_CALORIES")
            @RequestParam(required = false) FoodProductQualityIssue qualityIssue,
            @Parameter(description = "Optional product name, brand, barcode, or source key search text.", example = "almond")
            @RequestParam(required = false) String query,
            @Parameter(description = "Maximum number of exported rows. Maximum 10000.", example = "1000")
            @RequestParam(defaultValue = "1000") @Min(1) @Max(10000) int limit) {
        byte[] csv = foodProductReviewService.exportProductsForReview(
                verificationStatus,
                imageStatus,
                region,
                catalogType,
                dataSource,
                qualityIssue,
                query,
                limit
        );
        String filename = "grun-product-review-export-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
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
            @Parameter(description = "Optional market region filter. Supported values: GLOBAL, TR, UK_IE, EU.", example = "UK_IE")
            @RequestParam(required = false) MarketRegion region,
            @Parameter(description = "Optional catalog type filter.", example = "LOCAL_DISH")
            @RequestParam(required = false) FoodCatalogType catalogType,
            @Parameter(description = "Optional data source filter.", example = "OPEN_FOOD_FACTS")
            @RequestParam(required = false) FoodDataSource dataSource,
            @Parameter(description = "Optional derived quality issue filter.", example = "MISSING_CALORIES")
            @RequestParam(required = false) FoodProductQualityIssue qualityIssue,
            @Parameter(description = "Optional product name, brand, barcode, or source key search text.", example = "almond")
            @RequestParam(required = false) String query,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25")
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(foodProductReviewService.getProductsForReview(
                verificationStatus,
                imageStatus,
                region,
                catalogType,
                dataSource,
                qualityIssue,
                query,
                page,
                size
        ));
    }


    @GetMapping("/{id}/search-aliases")
    @Operation(
            summary = "List product search aliases",
            description = "Returns multilingual search aliases for a product. Aliases allow users to find a single product through multiple languages without duplicating product rows."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search aliases returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Product was not found.")
    })
    public ResponseEntity<List<FoodSearchAliasDto>> getProductSearchAliases(
            @Parameter(description = "Product id.", example = "123")
            @PathVariable Long id,
            @Parameter(description = "When true, only active aliases are returned.", example = "true")
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(foodProductReviewService.getProductSearchAliases(id, activeOnly));
    }

    @PostMapping("/{id}/search-aliases")
    @Operation(
            summary = "Create or reactivate a product search alias",
            description = "Adds a multilingual search alias to a product. If the same normalized alias and language already exist for the product, the existing alias is updated/reactivated instead of creating duplicate product data."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search alias created or updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Product was not found.")
    })
    public ResponseEntity<FoodSearchAliasDto> addProductSearchAlias(
            @Parameter(description = "Product id.", example = "123")
            @PathVariable Long id,
            @Valid @RequestBody FoodSearchAliasRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodProductReviewService.addProductSearchAlias(
                id,
                request,
                userDetails == null ? null : userDetails.getUsername()
        ));
    }

    @PatchMapping("/{id}/search-aliases/{aliasId}/status")
    @Operation(
            summary = "Enable or disable a product search alias",
            description = "Toggles whether an alias participates in product search. This keeps historical alias data without deleting it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search alias status updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Product or alias was not found.")
    })
    public ResponseEntity<FoodSearchAliasDto> updateProductSearchAliasStatus(
            @Parameter(description = "Product id.", example = "123")
            @PathVariable Long id,
            @Parameter(description = "Alias id.", example = "10")
            @PathVariable Long aliasId,
            @Parameter(description = "Whether alias should be active in search.", example = "false")
            @RequestParam boolean active,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodProductReviewService.updateProductSearchAliasStatus(
                id,
                aliasId,
                active,
                userDetails == null ? null : userDetails.getUsername()
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

    @GetMapping("/{id}/quality-issues")
    @Operation(
            summary = "List product quality issues",
            description = "Returns active or full quality issue history for a single food product."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product quality issues returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin."),
            @ApiResponse(responseCode = "404", description = "Product was not found.")
    })
    public ResponseEntity<List<FoodProductQualityIssueDto>> getProductQualityIssues(
            @Parameter(description = "Food product id.", example = "1") @PathVariable Long id,
            @Parameter(description = "When true, returns only unresolved issues.", example = "true")
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(foodProductReviewService.getProductQualityIssues(id, activeOnly));
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

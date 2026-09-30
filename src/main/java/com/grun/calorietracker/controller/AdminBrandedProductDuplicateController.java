package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.BrandedProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateDecisionDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateDecisionRequestDto;
import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseDto;
import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseRequestDto;
import com.grun.calorietracker.service.BrandedDuplicateSearchCollapseService;
import com.grun.calorietracker.service.BrandedProductDuplicateAnalysisService;
import com.grun.calorietracker.service.BrandedProductDuplicateDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

@RestController
@RequestMapping("/api/v1/admin/products/duplicates/branded")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Product Review", description = "Admin-only product catalog review operations.")
public class AdminBrandedProductDuplicateController {

    private final BrandedProductDuplicateAnalysisService analysisService;
    private final BrandedProductDuplicateDecisionService decisionService;
    private final BrandedDuplicateSearchCollapseService collapseService;

    @GetMapping
    @Operation(
            summary = "List branded duplicate candidates",
            description = "Returns read-only candidate groups sharing normalized brand and product names. Different GTINs are always routed to review and are never merged automatically. Groups without market, preparation, serving, or nutrition variation are prioritized."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branded duplicate candidates returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.")
    })
    public ResponseEntity<BrandedProductDuplicateGroupPageDto> getCandidates(
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25")
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @Parameter(description = "Optional case-insensitive brand or product-name filter.", example = "Wispa")
            @RequestParam(required = false) String query,
            @Parameter(description = "Optional variant evidence filter. false returns the highest-priority identity-review cohort.")
            @RequestParam(required = false) Boolean variantSignal) {
        return ResponseEntity.ok(analysisService.getCandidates(page, size, query, variantSignal));
    }

    @GetMapping("/decision")
    @Operation(summary = "Get a branded duplicate decision",
            description = "Returns the current reviewed decision for one exact brand/name identity group.")
    public ResponseEntity<BrandedProductDuplicateDecisionDto> getDecision(
            @RequestParam String brandKey,
            @RequestParam String nameKey) {
        return ResponseEntity.ok(decisionService.get(brandKey, nameKey));
    }

    @PostMapping("/decision")
    @Operation(summary = "Record a branded duplicate decision",
            description = "Records KEEP_SEPARATE, BLOCKED, or SURVIVOR_SELECTED after validating that the candidate fingerprint is still current. This endpoint never merges or deletes product records.")
    public ResponseEntity<BrandedProductDuplicateDecisionDto> decide(
            @RequestBody @jakarta.validation.Valid BrandedProductDuplicateDecisionRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(decisionService.decide(
                request, userDetails == null ? null : userDetails.getUsername()
        ));
    }

    @DeleteMapping("/decision")
    @Operation(summary = "Clear a branded duplicate decision",
            description = "Clears the current decision and writes an immutable audit entry. Product records remain unchanged.")
    public ResponseEntity<Void> clearDecision(
            @RequestParam String brandKey,
            @RequestParam String nameKey,
            @RequestParam @Size(min = 10, max = 1000) String reason,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        decisionService.clear(brandKey, nameKey, reason,
                userDetails == null ? null : userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search-collapse")
    @Operation(summary = "Get reviewed branded search collapse",
            description = "Returns the reversible search-visibility state for an exact reviewed brand/name group.")
    public ResponseEntity<BrandedDuplicateSearchCollapseDto> getSearchCollapse(
            @RequestParam String brandKey,
            @RequestParam String nameKey) {
        return ResponseEntity.ok(collapseService.get(brandKey, nameKey));
    }

    @PostMapping("/search-collapse")
    @Operation(summary = "Apply reviewed branded search collapse",
            description = "Keeps the selected survivor in search and suppresses only the reviewed non-survivors. Product rows, barcodes and source provenance remain intact. Stale decisions are rejected.")
    public ResponseEntity<BrandedDuplicateSearchCollapseDto> applySearchCollapse(
            @RequestBody @jakarta.validation.Valid BrandedDuplicateSearchCollapseRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(collapseService.apply(
                request, userDetails == null ? null : userDetails.getUsername()
        ));
    }

    @DeleteMapping("/search-collapse")
    @Operation(summary = "Revert reviewed branded search collapse",
            description = "Restores all reviewed group members to normal search visibility and writes an immutable audit entry.")
    public ResponseEntity<BrandedDuplicateSearchCollapseDto> revertSearchCollapse(
            @RequestParam String brandKey,
            @RequestParam String nameKey,
            @RequestParam @Size(min = 10, max = 1000) String reason,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(collapseService.revert(
                brandKey, nameKey, reason, userDetails == null ? null : userDetails.getUsername()
        ));
    }
}

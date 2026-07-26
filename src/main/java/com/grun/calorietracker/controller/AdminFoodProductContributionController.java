package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductContributionDto;
import com.grun.calorietracker.dto.FoodProductContributionPageDto;
import com.grun.calorietracker.dto.FoodProductContributionReviewRequestDto;
import com.grun.calorietracker.enums.FoodProductContributionStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.service.FoodProductContributionService;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.EvidenceContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products/contributions")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Product Contributions", description = "Admin review and S9 evidence-ledger export for user-submitted labels.")
public class AdminFoodProductContributionController {
    private final FoodProductContributionService contributionService;

    @GetMapping
    @Operation(summary = "List food-label contributions for review")
    public ResponseEntity<FoodProductContributionPageDto> list(
            @RequestParam(required = false) FoodProductContributionStatus status,
            @RequestParam(required = false) MarketRegion marketRegion,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(contributionService.listForReview(status, marketRegion, page, size));
    }

    @GetMapping("/{id}/evidence")
    @Operation(summary = "View private contribution evidence", description = "Streams a private label object to an authenticated admin without exposing its storage key.")
    public ResponseEntity<byte[]> viewEvidence(@PathVariable Long id) {
        EvidenceContent evidence = contributionService.loadEvidenceForAdmin(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(safeMediaType(evidence.contentType()));
        headers.setContentDisposition(ContentDisposition.inline().filename("product-label").build());
        headers.setCacheControl("private, no-store, max-age=0");
        return ResponseEntity.ok().headers(headers).body(evidence.bytes());
    }

    @PatchMapping("/{id}/review")
    @Operation(summary = "Approve or reject food-label evidence", description = "Approval makes evidence exportable; it still does not write a catalog product.")
    public ResponseEntity<FoodProductContributionDto> review(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FoodProductContributionReviewRequestDto request) {
        return ResponseEntity.ok(contributionService.review(id, userDetails.getUsername(), request));
    }

    @GetMapping(value = "/evidence-ledger.tsv", produces = "text/tab-separated-values")
    @Operation(summary = "Export approved TR evidence ledger", description = "Returns the exact evidence contract consumed by the S9 assessment pipeline.")
    public ResponseEntity<byte[]> exportEvidenceLedger() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/tab-separated-values"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("tr-user-label-evidence-ledger.tsv")
                .build());
        return ResponseEntity.ok().headers(headers).body(contributionService.exportApprovedTrEvidenceLedger());
    }

    private MediaType safeMediaType(String value) {
        try {
            return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
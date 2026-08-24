package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminFoodProductEvidenceReadDto;
import com.grun.calorietracker.service.AdminFoodProductEvidenceService;
import com.grun.calorietracker.service.support.FoodProductIntakeMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products/review-cases/assets")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Product Evidence", description = "Strictly scoped private product-review evidence access.")
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class AdminFoodProductEvidenceController {
    private final AdminFoodProductEvidenceService service;
    private final FoodProductIntakeMetrics metrics;

    @GetMapping("/{assetId}/evidence-url")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN_CATALOG')")
    @Operation(summary = "Issue a short-lived private evidence read URL")
    public ResponseEntity<AdminFoodProductEvidenceReadDto> authorizeRead(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long assetId
    ) {
        try {
            var response = service.authorizeRead(userDetails.getUsername(), assetId);
            metrics.record("evidence_authorize", "success");
            return ResponseEntity.ok(response);
        } catch (RuntimeException failure) {
            metrics.record("evidence_authorize", "failure");
            throw failure;
        }
    }
}

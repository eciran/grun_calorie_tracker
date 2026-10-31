package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductFamilyDeactivateRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyDto;
import com.grun.calorietracker.dto.FoodProductFamilyPreviewDto;
import com.grun.calorietracker.dto.FoodProductFamilyRequestDto;
import com.grun.calorietracker.service.FoodProductFamilyAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/product-families")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Product Families", description = "Controlled package-family preview, apply and rollback operations.")
public class AdminFoodProductFamilyController {
    private final FoodProductFamilyAdminService service;

    @PostMapping("/preview")
    @Operation(summary = "Validate a package family without changing catalog data")
    public ResponseEntity<FoodProductFamilyPreviewDto> preview(
            @RequestBody @Valid FoodProductFamilyRequestDto request
    ) {
        return ResponseEntity.ok(service.preview(request));
    }

    @PostMapping
    @Operation(summary = "Apply a source-reviewed package family")
    public ResponseEntity<FoodProductFamilyDto> apply(
            @RequestBody @Valid FoodProductFamilyRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(service.apply(request, username(userDetails)));
    }

    @DeleteMapping("/{familyId}")
    @Operation(summary = "Deactivate a package family while preserving its audit evidence")
    public ResponseEntity<FoodProductFamilyDto> deactivate(
            @PathVariable Long familyId,
            @RequestBody @Valid FoodProductFamilyDeactivateRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(service.deactivate(familyId, request.reason(), username(userDetails)));
    }

    private String username(UserDetails userDetails) {
        return userDetails == null ? null : userDetails.getUsername();
    }
}

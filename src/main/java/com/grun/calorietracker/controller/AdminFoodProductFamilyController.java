package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductFamilyDeactivateRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyDto;
import com.grun.calorietracker.dto.FoodProductFamilyPreviewDto;
import com.grun.calorietracker.dto.FoodProductFamilyRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyApplyRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyPageDto;
import com.grun.calorietracker.dto.FoodProductFamilyMembershipDto;
import com.grun.calorietracker.dto.FoodProductFamilyAddMemberPreviewRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyAddMemberApplyRequestDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;

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
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_CATALOG_MANAGE')")
    @Operation(summary = "Apply a source-reviewed package family")
    public ResponseEntity<FoodProductFamilyDto> apply(
            @RequestBody @Valid FoodProductFamilyApplyRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(service.apply(request.familyRequest(), request.previewFingerprint(),
                request.acknowledgedWarnings(), username(userDetails)));
    }

    @GetMapping
    @Operation(summary = "Search package families and audit history")
    public ResponseEntity<FoodProductFamilyPageDto> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(service.list(query, active,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "appliedAt"))));
    }

    @GetMapping("/{familyId}")
    @Operation(summary = "Inspect a package family and its audit history")
    public ResponseEntity<FoodProductFamilyDto> get(@PathVariable Long familyId) {
        return ResponseEntity.ok(service.get(familyId));
    }

    @PostMapping("/{familyId}/members/preview")
    @Operation(summary = "Validate adding a product to an active package family")
    public ResponseEntity<FoodProductFamilyPreviewDto> previewAddition(
            @PathVariable Long familyId,
            @RequestBody @Valid FoodProductFamilyAddMemberPreviewRequestDto request
    ) {
        return ResponseEntity.ok(service.previewAddition(familyId, request.member(), request.reason()));
    }

    @PostMapping("/{familyId}/members")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_CATALOG_MANAGE')")
    @Operation(summary = "Add one source-reviewed product to an active package family")
    public ResponseEntity<FoodProductFamilyDto> addMember(
            @PathVariable Long familyId,
            @RequestBody @Valid FoodProductFamilyAddMemberApplyRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(service.addMember(familyId, request.member(), request.reason(),
                request.previewFingerprint(), request.acknowledgedWarnings(), username(userDetails)));
    }

    @GetMapping("/memberships")
    @Operation(summary = "Resolve active package-family memberships for catalog rows")
    public ResponseEntity<List<FoodProductFamilyMembershipDto>> memberships(
            @RequestParam List<Long> productIds
    ) {
        if (productIds.size() > 100) throw new IllegalArgumentException("At most 100 product ids may be resolved at once");
        return ResponseEntity.ok(service.memberships(productIds));
    }

    @DeleteMapping("/{familyId}")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_CATALOG_MANAGE')")
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

package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminProductIntakeAssignmentDto;
import com.grun.calorietracker.dto.AdminProductIntakeActionDto;
import com.grun.calorietracker.dto.AdminProductIntakeManualRequestDto;
import com.grun.calorietracker.dto.AdminProductIntakeDetailDto;
import com.grun.calorietracker.dto.AdminProductIntakeActionRequestDto;
import com.grun.calorietracker.dto.AdminProductIntakeAttachRequestDto;
import com.grun.calorietracker.dto.AdminProductIntakeApplyRequestDto;
import com.grun.calorietracker.dto.AdminProductIntakePageDto;
import com.grun.calorietracker.dto.AdminProductIntakeReassignRequestDto;
import com.grun.calorietracker.enums.AdminProductIntakeQueue;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.service.AdminProductIntakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/product-intakes")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Product Intakes", description = "Paginated review queues without private evidence URLs.")
public class AdminProductIntakeController {
    private final AdminProductIntakeService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG','ADMIN_READ_ONLY')")
    @Operation(summary = "List product-intake review cases")
    public ResponseEntity<AdminProductIntakePageDto> list(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "ALL") AdminProductIntakeQueue queue,
            @RequestParam(required = false) FoodProductReviewCaseStatus status,
            @RequestParam(required = false) MarketRegion marketRegion,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(service.list(userDetails.getUsername(), queue, status, marketRegion, page, size));
    }

    @PatchMapping("/{caseId}/claim")
    @PreAuthorize("hasRole('ADMIN_CATALOG')")
    public ResponseEntity<AdminProductIntakeAssignmentDto> claim(
            @PathVariable Long caseId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(service.claim(caseId, userDetails.getUsername()));
    }

    @PatchMapping("/{caseId}/release")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG')")
    public ResponseEntity<AdminProductIntakeAssignmentDto> release(
            @PathVariable Long caseId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(service.release(caseId, userDetails.getUsername()));
    }

    @PatchMapping("/{caseId}/reassign")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<AdminProductIntakeAssignmentDto> reassign(
            @PathVariable Long caseId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminProductIntakeReassignRequestDto request
    ) {
        return ResponseEntity.ok(service.reassign(caseId, userDetails.getUsername(), request.adminEmail()));
    }

    @PatchMapping("/{caseId}/request-better-evidence")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG')")
    public ResponseEntity<AdminProductIntakeActionDto> requestBetterEvidence(
            @PathVariable Long caseId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminProductIntakeActionRequestDto request
    ) {
        return ResponseEntity.ok(service.requestBetterEvidence(caseId, userDetails.getUsername(), request.note()));
    }

    @PatchMapping("/{caseId}/evidence/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG')")
    public ResponseEntity<AdminProductIntakeActionDto> approveEvidence(
            @PathVariable Long caseId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminProductIntakeActionRequestDto request
    ) {
        return ResponseEntity.ok(service.decideEvidence(caseId, userDetails.getUsername(), true, request.note()));
    }

    @PatchMapping("/{caseId}/evidence/reject")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG')")
    public ResponseEntity<AdminProductIntakeActionDto> rejectEvidence(
            @PathVariable Long caseId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminProductIntakeActionRequestDto request
    ) {
        return ResponseEntity.ok(service.decideEvidence(caseId, userDetails.getUsername(), false, request.note()));
    }

    @PatchMapping("/{caseId}/attach-existing-product")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG')")
    public ResponseEntity<AdminProductIntakeActionDto> attachExistingProduct(
            @PathVariable Long caseId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminProductIntakeAttachRequestDto request
    ) {
        return ResponseEntity.ok(service.attachExistingProduct(caseId, userDetails.getUsername(), request.foodItemId()));
    }
    @PatchMapping("/{caseId}/apply-existing")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG')")
    public ResponseEntity<AdminProductIntakeActionDto> applyExistingProduct(
            @PathVariable Long caseId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminProductIntakeApplyRequestDto request
    ) {
        return ResponseEntity.ok(service.applyExistingProduct(caseId, userDetails.getUsername(), request.fields()));
    }
    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG')")
    public ResponseEntity<AdminProductIntakeActionDto> createManual(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminProductIntakeManualRequestDto request
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(service.createManual(userDetails.getUsername(), request));
    }
    @GetMapping("/{caseId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN_CATALOG','ADMIN_READ_ONLY')")
    public ResponseEntity<AdminProductIntakeDetailDto> detail(@PathVariable Long caseId) {
        return ResponseEntity.ok(service.detail(caseId));
    }}
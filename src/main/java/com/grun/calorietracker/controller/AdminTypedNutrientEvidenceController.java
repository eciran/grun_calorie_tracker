package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.TypedNutrientEvidenceRequest;
import com.grun.calorietracker.dto.TypedSourceObservationRequest;
import com.grun.calorietracker.service.TypedNutrientEvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/products/{productId}/typed-nutrient-evidence")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Typed Nutrient Evidence", description = "Reviewed source observations; never rewrites or certifies legacy nutrient units.")
public class AdminTypedNutrientEvidenceController {
    private final TypedNutrientEvidenceService service;

    @PostMapping("/source-observations/preview")
    @Operation(summary = "Preview an explicit v2 source-only nutrient observation", description = "Keeps numeric differences held; never certifies legacy units or approves a nutrition patch.")
    public TypedNutrientEvidenceService.Comparison previewObservation(@PathVariable long productId,
            @Valid @RequestBody TypedSourceObservationRequest request) {
        return service.previewObservation(productId, request);
    }

    @PostMapping("/source-observations")
    @Operation(summary = "Append a reviewed v2 source-only observation", description = "Disabled by default. Numeric review remains open even when source evidence is appended. All identity/freshness/dependency guards must pass.")
    public TypedNutrientEvidenceService.AppendResult appendObservation(@PathVariable long productId,
            @Valid @RequestBody TypedSourceObservationRequest request, @AuthenticationPrincipal UserDetails reviewer) {
        return service.appendObservation(productId, request, reviewer == null ? null : reviewer.getUsername());
    }

    @PostMapping("/preview")
    @Operation(summary = "Compare a typed source observation with the current product and legacy evidence")
    public TypedNutrientEvidenceService.Comparison preview(@PathVariable long productId,
            @Valid @RequestBody TypedNutrientEvidenceRequest request) {
        return service.preview(productId, request);
    }

    @PostMapping
    @Operation(summary = "Append explicitly approved source evidence", description = "Disabled by default. Requires the exact current preview fingerprint. Does not change nutrition, serving, publication or legacy evidence.")
    public TypedNutrientEvidenceService.AppendResult append(@PathVariable long productId,
            @Valid @RequestBody TypedNutrientEvidenceRequest request,
            @AuthenticationPrincipal UserDetails reviewer) {
        return service.append(productId, request, reviewer == null ? null : reviewer.getUsername());
    }

    @GetMapping
    @Operation(summary = "List the latest 100 immutable typed source observations")
    public List<Map<String, Object>> history(@PathVariable long productId) {
        return service.history(productId);
    }

    @GetMapping("/{evidenceId}/comparison")
    @Operation(summary = "Recompare an immutable observation against the current product without changing history")
    public TypedNutrientEvidenceService.StoredComparison comparison(@PathVariable long productId, @PathVariable long evidenceId) {
        return service.compareStored(productId, evidenceId);
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminPromoService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/promotions")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Commercial Operations", description = "Audit-safe promotion lifecycle, targeting, provider mapping and aggregate redemption metrics.")
public class AdminPromoController {
    private final AdminPromoService promoService;

    @GetMapping
    @Operation(summary = "List promotions with server-side filters")
    public ResponseEntity<AdminPromoPageDto> list(
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) PromoStatus status,
            @RequestParam(required = false) PromoType type,
            @RequestParam(required = false) PromoStore store,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(promoService.list(search, status, type, store, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminPromoDto> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(promoService.get(id));
    }

    @PostMapping
    public ResponseEntity<AdminPromoDto> create(@RequestBody @Valid AdminPromoRequestDto request,
            @AuthenticationPrincipal UserDetails admin, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(promoService.create(request, admin.getUsername(), correlationId(servletRequest)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminPromoDto> update(@PathVariable @Positive Long id,
            @RequestBody @Valid AdminPromoRequestDto request,
            @AuthenticationPrincipal UserDetails admin, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(promoService.update(id, request, admin.getUsername(), correlationId(servletRequest)));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<AdminPromoPreviewDto> preview(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(promoService.preview(id));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<AdminPromoDto> activate(@PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails admin, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(promoService.activate(id, admin.getUsername(), correlationId(servletRequest)));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<AdminPromoDto> deactivate(@PathVariable @Positive Long id,
            @RequestBody @Valid AdminPromoDeactivateRequestDto request,
            @AuthenticationPrincipal UserDetails admin, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(promoService.deactivate(id, request.getReason(), admin.getUsername(), correlationId(servletRequest)));
    }

    @PostMapping("/{id}/reconcile")
    public ResponseEntity<AdminPromoReconciliationDto> reconcile(@PathVariable @Positive Long id,
            @AuthenticationPrincipal UserDetails admin, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(promoService.reconcile(id, admin.getUsername(), correlationId(servletRequest)));
    }

    @PostMapping("/{id}/redemptions")
    @Operation(summary = "Record an idempotent provider redemption without granting entitlement")
    public ResponseEntity<AdminPromoRedemptionDto> recordRedemption(@PathVariable @Positive Long id,
            @RequestBody @Valid AdminPromoRedemptionRequestDto request,
            @AuthenticationPrincipal UserDetails admin, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(promoService.recordRedemption(id, request, admin.getUsername(), correlationId(servletRequest)));
    }

    @GetMapping("/redemptions")
    @Operation(summary = "List sanitized promotion redemption and abuse signals")
    public ResponseEntity<AdminPromoRedemptionPageDto> redemptions(
            @RequestParam(required = false) @Positive Long promoId,
            @RequestParam(required = false) PromoRedemptionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(promoService.redemptions(promoId, status, page, size));
    }
    @GetMapping("/metrics")
    public ResponseEntity<AdminPromoMetricsDto> metrics(@RequestParam(required = false) @Positive Long promoId) {
        return ResponseEntity.ok(promoService.metrics(promoId));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}

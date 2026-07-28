package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.ProductionVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/system/production-verifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductionVerificationController {
    private final ProductionVerificationService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_READ')")
    public ResponseEntity<ProductionVerificationRunPageDto> list(@RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.list(provider, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_MANAGE')")
    public ResponseEntity<ProductionVerificationRunDto> record(@RequestBody @Valid ProductionVerificationRunRequestDto request,
            @AuthenticationPrincipal UserDetails admin, HttpServletRequest servletRequest) {
        Object correlation = servletRequest.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return ResponseEntity.ok(service.record(request, admin.getUsername(),
                correlation == null ? null : correlation.toString()));
    }
}

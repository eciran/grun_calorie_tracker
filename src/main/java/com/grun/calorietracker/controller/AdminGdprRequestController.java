package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.GdprRequestStatus;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.GdprRequestTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/legal/gdpr-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminGdprRequestController {
    private final GdprRequestTrackingService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_COMPLIANCE_READ')")
    public ResponseEntity<AdminGdprRequestPageDto> list(
            @RequestParam(required = false) GdprRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.list(status, page, size));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_COMPLIANCE_MANAGE')")
    public ResponseEntity<AdminGdprRequestDto> update(
            @PathVariable Long id,
            @RequestBody @Valid AdminGdprRequestUpdateDto request,
            @AuthenticationPrincipal UserDetails admin,
            HttpServletRequest servletRequest) {
        Object correlation = servletRequest.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return ResponseEntity.ok(service.update(id, request, admin.getUsername(),
                correlation == null ? null : correlation.toString()));
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminNotificationDefinitionDto;
import com.grun.calorietracker.dto.AdminNotificationDefinitionRequestDto;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminNotificationDefinitionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/notification-definitions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Notification Definitions", description = "Managed system-notification copy and delivery policy.")
public class AdminNotificationDefinitionController {
    private final AdminNotificationDefinitionService service;

    @GetMapping
    public ResponseEntity<List<AdminNotificationDefinitionDto>> list() { return ResponseEntity.ok(service.list()); }

    @PostMapping
    public ResponseEntity<AdminNotificationDefinitionDto> create(@RequestBody @Valid AdminNotificationDefinitionRequestDto request,
            @AuthenticationPrincipal UserDetails user, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.create(request, user.getUsername(), correlationId(servletRequest)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminNotificationDefinitionDto> update(@PathVariable Long id,
            @RequestBody @Valid AdminNotificationDefinitionRequestDto request,
            @AuthenticationPrincipal UserDetails user, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.update(id, request, user.getUsername(), correlationId(servletRequest)));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}

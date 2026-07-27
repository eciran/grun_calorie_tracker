package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.NotificationCampaignRecipientStatus;
import com.grun.calorietracker.enums.NotificationCampaignStatus;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminNotificationCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/admin/notification-campaigns")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Notification Campaigns", description = "Admin-only audience targeting and controlled notification campaign operations.")
public class AdminNotificationCampaignController {
    private final AdminNotificationCampaignService campaignService;

    @GetMapping
    @Operation(summary = "List notification campaigns")
    public ResponseEntity<AdminNotificationCampaignPageDto> list(
            @RequestParam(required = false) NotificationCampaignStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(campaignService.list(status, page, size));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get privacy-safe notification campaign performance summary")
    public ResponseEntity<AdminNotificationCampaignSummaryDto> summary(
            @RequestParam(defaultValue = "31") @Min(1) @Max(90) int windowDays) {
        return ResponseEntity.ok(campaignService.summary(windowDays));
    }
    @GetMapping("/{id}/recipients")
    @Operation(summary = "List privacy-safe campaign delivery and engagement rows")
    public ResponseEntity<AdminNotificationCampaignRecipientPageDto> recipients(
            @PathVariable Long id,
            @RequestParam(required = false) NotificationCampaignRecipientStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(campaignService.recipients(id, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification campaign")
    public ResponseEntity<AdminNotificationCampaignDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create draft notification campaign")
    public ResponseEntity<AdminNotificationCampaignDto> create(
            @RequestBody @Valid AdminNotificationCampaignRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(campaignService.create(request, user.getUsername(), correlationId(servletRequest)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update draft notification campaign")
    public ResponseEntity<AdminNotificationCampaignDto> update(
            @PathVariable Long id,
            @RequestBody @Valid AdminNotificationCampaignRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(campaignService.update(id, request, user.getUsername(), correlationId(servletRequest)));
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "Preview campaign audience")
    public ResponseEntity<AdminNotificationCampaignPreviewDto> preview(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.preview(id));
    }

    @PostMapping("/{id}/schedule")
    @Operation(summary = "Schedule or send notification campaign")
    public ResponseEntity<AdminNotificationCampaignDto> schedule(
            @PathVariable Long id,
            @RequestBody(required = false) AdminNotificationCampaignScheduleRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(campaignService.schedule(id, request == null ? null : request.getScheduledAt(),
                user.getUsername(), correlationId(servletRequest)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel scheduled or processing notification campaign")
    public ResponseEntity<AdminNotificationCampaignDto> cancel(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(campaignService.cancel(id, user.getUsername(), correlationId(servletRequest)));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}
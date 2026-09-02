package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.AdminApprovalActionType;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminApprovalService;
import com.grun.calorietracker.service.AdminSubscriptionNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/subscription-notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSubscriptionNotificationController {
    private final AdminSubscriptionNotificationService service;
    private final AdminApprovalService approvalService;
    private final ObjectMapper objectMapper;

    @GetMapping("/policy")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_FINANCE_READ')")
    public ResponseEntity<AdminSubscriptionNotificationPolicyDto> policy() {
        return ResponseEntity.ok(service.getPolicy());
    }

    @PostMapping("/policy/publish-request")
    @PreAuthorize("hasAnyAuthority('ADMIN_PERMISSION_FINANCE_MANAGE','ADMIN_PERMISSION_ADMIN_TEAM_MANAGE')")
    public ResponseEntity<AdminApprovalRequestDto> requestPolicyPublish(
            @RequestBody @Valid AdminSubscriptionNotificationPolicyRequestDto request,
            @AuthenticationPrincipal UserDetails user, HttpServletRequest servletRequest) {
        var approval = new AdminApprovalCreateRequestDto(AdminApprovalActionType.SUBSCRIPTION_NOTIFICATION_POLICY_PUBLISH,
                "1", objectMapper.valueToTree(request), request.reason());
        return ResponseEntity.accepted().body(approvalService.create(user.getUsername(), approval, correlationId(servletRequest)));
    }

    @PostMapping("/emergency-stop")
    @PreAuthorize("hasAnyAuthority('ADMIN_PERMISSION_FINANCE_MANAGE','ADMIN_PERMISSION_TECHNICAL_MANAGE','ADMIN_PERMISSION_ADMIN_TEAM_MANAGE')")
    public ResponseEntity<AdminSubscriptionNotificationPolicyDto> emergencyStop(
            @RequestBody @Valid AdminSubscriptionNotificationEmergencyStopRequestDto request,
            @AuthenticationPrincipal UserDetails user, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.emergencyStop(request.reason(), user.getUsername(), correlationId(servletRequest)));
    }

    @PostMapping("/definitions/{id}/publish-request")
    @PreAuthorize("hasAnyAuthority('ADMIN_PERMISSION_FINANCE_MANAGE','ADMIN_PERMISSION_ADMIN_TEAM_MANAGE')")
    public ResponseEntity<AdminApprovalRequestDto> requestDefinitionPublish(@PathVariable Long id,
            @RequestBody @Valid AdminSubscriptionNotificationDefinitionPublishRequestDto request,
            @AuthenticationPrincipal UserDetails user, HttpServletRequest servletRequest) {
        var approval = new AdminApprovalCreateRequestDto(
                AdminApprovalActionType.SUBSCRIPTION_NOTIFICATION_DEFINITION_PUBLISH,
                String.valueOf(id), objectMapper.valueToTree(request.definition()), request.reason());
        return ResponseEntity.accepted().body(approvalService.create(user.getUsername(), approval, correlationId(servletRequest)));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_FINANCE_READ')")
    public ResponseEntity<AdminSubscriptionNotificationPreviewDto> preview(
            @RequestBody @Valid AdminSubscriptionNotificationPreviewRequestDto request) {
        return ResponseEntity.ok(service.preview(request));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_FINANCE_READ')")
    public ResponseEntity<AdminSubscriptionNotificationLedgerPageDto> ledger(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.ledger(page, size));
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_FINANCE_READ')")
    public ResponseEntity<AdminSubscriptionNotificationMetricsDto> metrics() {
        return ResponseEntity.ok(service.metrics());
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}

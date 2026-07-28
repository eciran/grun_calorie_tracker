package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminRuntimeApiMetricsDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationRecordDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationRecordRequestDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationsPolicyDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationsPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AdminRuntimePolicyRollbackRequestDto;
import com.grun.calorietracker.dto.AdminSystemReliabilityAnalyticsDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminSystemReliabilityAnalyticsService;
import com.grun.calorietracker.service.RuntimeOperationsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/system/operations")
public class AdminRuntimeOperationsController {
    private final RuntimeOperationsService runtimeOperationsService;
    private final AdminAuditService adminAuditService;
    private final AdminSystemReliabilityAnalyticsService reliabilityAnalyticsService;

    @GetMapping("/policy")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_READ')")
    public ResponseEntity<AdminRuntimeOperationsPolicyDto> getPolicy() {
        return ResponseEntity.ok(runtimeOperationsService.getPolicy());
    }

    @PutMapping("/policy")
    @PreAuthorize("denyAll()")
    public ResponseEntity<AdminRuntimeOperationsPolicyDto> updatePolicy(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody @Valid AdminRuntimeOperationsPolicyUpdateRequestDto request,
            HttpServletRequest servletRequest) {
        AdminRuntimeOperationsPolicyDto oldValue = runtimeOperationsService.getPolicy();
        AdminRuntimeOperationsPolicyDto result = runtimeOperationsService.updatePolicy(user.getUsername(), request);
        audit(user, AdminAuditActionType.RUNTIME_POLICY_UPDATE, "POLICY", oldValue, result, servletRequest);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/policy/rollback")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_MANAGE')")
    public ResponseEntity<AdminRuntimeOperationsPolicyDto> rollbackPolicy(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody @Valid AdminRuntimePolicyRollbackRequestDto request,
            HttpServletRequest servletRequest) {
        AdminRuntimeOperationsPolicyDto oldValue = runtimeOperationsService.getPolicy();
        AdminRuntimeOperationsPolicyDto result = runtimeOperationsService.rollbackPolicy(user.getUsername(), request);
        audit(user, AdminAuditActionType.RUNTIME_POLICY_ROLLBACK, "POLICY", oldValue, result, servletRequest);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api-metrics")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_READ')")
    public ResponseEntity<AdminRuntimeApiMetricsDto> getApiMetrics() {
        return ResponseEntity.ok(runtimeOperationsService.getApiMetrics());
    }

    @GetMapping("/reliability-analytics")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_READ')")
    public ResponseEntity<AdminSystemReliabilityAnalyticsDto> getReliabilityAnalytics(
            @RequestParam(defaultValue = "24") int windowHours) {
        return ResponseEntity.ok(reliabilityAnalyticsService.getAnalytics(windowHours));
    }
    @GetMapping("/records")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_READ')")
    public ResponseEntity<Page<AdminRuntimeOperationRecordDto>> getRecords(
            @RequestParam(required = false) RuntimeOperationRecordType type,
            @RequestParam(required = false) RuntimeOperationStatus status,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(runtimeOperationsService.getRecords(type, status, pageable));
    }

    @PostMapping("/records")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_MANAGE')")
    public ResponseEntity<AdminRuntimeOperationRecordDto> createRecord(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody @Valid AdminRuntimeOperationRecordRequestDto request,
            HttpServletRequest servletRequest) {
        AdminRuntimeOperationRecordDto result = runtimeOperationsService.createRecord(user.getUsername(), request);
        audit(user, AdminAuditActionType.RUNTIME_RECORD_CREATE,
                result.recordType().name() + ":" + result.id(), null, result, servletRequest);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/records/{id}/retry")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_MANAGE')")
    public ResponseEntity<AdminRuntimeOperationRecordDto> retryRecord(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            HttpServletRequest servletRequest) {
        AdminRuntimeOperationRecordDto result = runtimeOperationsService.retryRecord(user.getUsername(), id);
        audit(user, AdminAuditActionType.RUNTIME_JOB_RETRY,
                "SCHEDULED_JOB:" + id, null, result, servletRequest);
        return ResponseEntity.ok(result);
    }

    private void audit(
            UserDetails user, AdminAuditActionType action, String targetKey,
            Object oldValue, Object newValue, HttpServletRequest request) {
        adminAuditService.record(user.getUsername(), action, AdminAuditTargetType.RUNTIME_OPERATIONS,
                targetKey, oldValue, newValue,
                (String) request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE));
    }
}

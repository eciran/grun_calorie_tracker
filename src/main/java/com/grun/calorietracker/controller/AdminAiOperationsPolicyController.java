package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminAiOperationsPolicyDto;
import com.grun.calorietracker.dto.AdminAiOperationsPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AdminAiOperationsRollbackRequestDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AiOperationsPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai/monitoring/policy")
@RequiredArgsConstructor
public class AdminAiOperationsPolicyController {
    private final AiOperationsPolicyService policyService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_READ')")
    public ResponseEntity<AdminAiOperationsPolicyDto> getPolicy() {
        return ResponseEntity.ok(policyService.getPolicy());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_MANAGE')")
    public ResponseEntity<AdminAiOperationsPolicyDto> updatePolicy(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminAiOperationsPolicyUpdateRequestDto request,
            HttpServletRequest servletRequest) {
        AdminAiOperationsPolicyDto oldValue = policyService.getPolicy();
        AdminAiOperationsPolicyDto result = policyService.update(userDetails.getUsername(), request);
        audit(userDetails, AdminAuditActionType.AI_OPERATIONS_POLICY_UPDATE, oldValue, result, servletRequest);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rollback")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_MANAGE')")
    public ResponseEntity<AdminAiOperationsPolicyDto> rollback(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AdminAiOperationsRollbackRequestDto request,
            HttpServletRequest servletRequest) {
        AdminAiOperationsPolicyDto oldValue = policyService.getPolicy();
        AdminAiOperationsPolicyDto result = policyService.rollback(userDetails.getUsername(), request);
        audit(userDetails, AdminAuditActionType.AI_OPERATIONS_DEPLOYMENT_ROLLBACK, oldValue, result, servletRequest);
        return ResponseEntity.ok(result);
    }

    private void audit(UserDetails userDetails, AdminAuditActionType action,
                       AdminAiOperationsPolicyDto oldValue, AdminAiOperationsPolicyDto newValue,
                       HttpServletRequest request) {
        adminAuditService.record(userDetails.getUsername(), action,
                AdminAuditTargetType.AI_OPERATIONS_POLICY, "GLOBAL",
                oldValue, newValue, (String) request.getAttribute("correlationId"));
    }
}
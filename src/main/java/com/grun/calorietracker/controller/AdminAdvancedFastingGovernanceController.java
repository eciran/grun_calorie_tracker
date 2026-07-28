package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdvancedFastingGovernanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/fasting/advanced")
@RequiredArgsConstructor
public class AdminAdvancedFastingGovernanceController {
    private final AdvancedFastingGovernanceService governanceService;
    private final AdminAuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_READ')")
    public ResponseEntity<AdvancedFastingGovernanceDto> getGovernance() {
        return ResponseEntity.ok(governanceService.getGovernance());
    }

    @PutMapping("/operations")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_TECHNICAL_MANAGE')")
    public ResponseEntity<AdvancedFastingGovernanceDto> updateOperations(
            @AuthenticationPrincipal UserDetails admin,
            @RequestBody @Valid AdvancedFastingOperationsConfigRequestDto request,
            HttpServletRequest servletRequest) {
        AdvancedFastingGovernanceDto before = governanceService.getGovernance();
        AdvancedFastingGovernanceDto after = governanceService.updateOperations(request);
        auditService.record(admin.getUsername(), AdminAuditActionType.FASTING_OPERATIONS_CONFIG_UPDATE,
                AdminAuditTargetType.FASTING_GOVERNANCE, "GLOBAL", safeAudit(before), safeAudit(after),
                (String) servletRequest.getAttribute("correlationId"));
        return ResponseEntity.ok(after);
    }

    private Object safeAudit(AdvancedFastingGovernanceDto value) {
        return new AdvancedFastingOperationsConfigRequestDto(
                value.reminderEnabled(), value.preStartMinutes(), value.nearingCompletionMinutes(),
                value.missedPlanMinutes(), value.maxRetryAttempts());
    }
}
package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.AdminApprovalActionType;
import com.grun.calorietracker.enums.AdminApprovalStatus;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/approvals")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminApprovalController {
    private final AdminApprovalService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_ADMIN_TEAM_MANAGE')")
    public ResponseEntity<AdminApprovalPageDto> list(@RequestParam(required=false) AdminApprovalStatus status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return ResponseEntity.ok(service.list(status,page,size));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN_PERMISSION_FINANCE_MANAGE','ADMIN_PERMISSION_TECHNICAL_MANAGE','ADMIN_PERMISSION_GROWTH_MANAGE','ADMIN_PERMISSION_ADMIN_TEAM_MANAGE')")
    public ResponseEntity<AdminApprovalRequestDto> create(@RequestBody @Valid AdminApprovalCreateRequestDto request,
            @AuthenticationPrincipal UserDetails user,HttpServletRequest servletRequest) {
        requireMakerPermission(request.actionType(), user);
        return ResponseEntity.accepted().body(service.create(user.getUsername(),request,correlationId(servletRequest)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_ADMIN_TEAM_MANAGE')")
    public ResponseEntity<AdminApprovalRequestDto> approve(@PathVariable Long id,
            @RequestHeader("X-Admin-Reauth-Token") String reauthToken,
            @RequestBody @Valid AdminApprovalDecisionRequestDto request,
            @AuthenticationPrincipal UserDetails user,HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.approve(id,user.getUsername(),isOwner(user),reauthToken,request.reason(),correlationId(servletRequest)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_ADMIN_TEAM_MANAGE')")
    public ResponseEntity<AdminApprovalRequestDto> reject(@PathVariable Long id,
            @RequestHeader("X-Admin-Reauth-Token") String reauthToken,
            @RequestBody @Valid AdminApprovalDecisionRequestDto request,
            @AuthenticationPrincipal UserDetails user,HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.reject(id,user.getUsername(),isOwner(user),reauthToken,request.reason(),correlationId(servletRequest)));
    }

    private void requireMakerPermission(AdminApprovalActionType actionType, UserDetails user) {
        String requiredAuthority = switch (actionType) {
            case SUBSCRIPTION_UPDATE, AI_QUOTA_RESET, AI_ADDON_QUOTA_GRANT,
                    ENTITLEMENT_MATRIX_APPLY, PLAN_FEATURE_UPDATE, AI_QUOTA_REFUND ->
                    "ADMIN_PERMISSION_FINANCE_MANAGE";
            case NOTIFICATION_CAMPAIGN_SCHEDULE -> "ADMIN_PERMISSION_GROWTH_MANAGE";
            case RUNTIME_POLICY_UPDATE -> "ADMIN_PERMISSION_TECHNICAL_MANAGE";
        };
        boolean allowed = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(requiredAuthority)
                        || authority.equals("ADMIN_PERMISSION_ADMIN_TEAM_MANAGE"));
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Admin is not permitted to request this approval action.");
        }
    }
    private String correlationId(HttpServletRequest request) {
        Object value=request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value==null?request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER):value.toString();
    }

    private boolean isOwner(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OWNER"::equals);
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminCustomer360Dto;
import com.grun.calorietracker.dto.AdminUserRiskActionRequestDto;
import com.grun.calorietracker.dto.AdminUserSupportNoteDto;
import com.grun.calorietracker.dto.AdminUserSupportNoteRequestDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminCustomer360Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCustomer360Controller {

    private final AdminCustomer360Service customer360Service;
    private final AdminAuditService adminAuditService;

    @GetMapping("/{userId}/customer-360")
    public AdminCustomer360Dto getCustomer(@PathVariable Long userId) {
        return customer360Service.getCustomer(userId);
    }

    @PostMapping("/{userId}/support-notes")
    public AdminUserSupportNoteDto addSupportNote(
            @PathVariable Long userId,
            @RequestBody @Valid AdminUserSupportNoteRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        String adminEmail = adminEmail(userDetails);
        AdminUserSupportNoteDto note = customer360Service.addSupportNote(userId, request, adminEmail);
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.USER_SUPPORT_NOTE_CREATE,
                AdminAuditTargetType.USER_ACCOUNT,
                userId.toString(),
                null,
                note,
                correlationId(httpRequest)
        );
        return note;
    }

    @PostMapping("/{userId}/sessions/revoke")
    public Map<String, Object> revokeSessions(
            @PathVariable Long userId,
            @RequestBody @Valid AdminUserRiskActionRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        String adminEmail = adminEmail(userDetails);
        AdminCustomer360Dto customer = customer360Service.getCustomer(userId);
        if (customer.profile().email() != null
                && customer.profile().email().equalsIgnoreCase(adminEmail)) {
            throw new IllegalArgumentException("Admin cannot revoke their own active session from Customer 360.");
        }
        int revoked = customer360Service.revokeActiveSessions(userId);
        Map<String, Object> result = Map.of(
                "revokedSessions", revoked,
                "reason", request.reason().trim()
        );
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.USER_SESSION_REVOKE,
                AdminAuditTargetType.USER_ACCOUNT,
                userId.toString(),
                Map.of("activeSessions", customer.security().activeSessions()),
                result,
                correlationId(httpRequest)
        );
        return result;
    }

    private String adminEmail(UserDetails userDetails) {
        return userDetails == null ? "unknown-admin" : userDetails.getUsername();
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}

package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminAccessProfileDto;
import com.grun.calorietracker.dto.AdminAccessGrantRequestDto;
import com.grun.calorietracker.dto.AdminTeamMemberDto;
import com.grun.calorietracker.dto.AdminTeamMemberUpdateRequestDto;
import com.grun.calorietracker.dto.AdminTeamPageDto;
import com.grun.calorietracker.dto.AdminMfaCodeRequestDto;
import com.grun.calorietracker.dto.AdminMfaEnrollmentDto;
import com.grun.calorietracker.dto.AdminMfaEnrollmentRequestDto;
import com.grun.calorietracker.dto.AdminMfaStatusDto;
import com.grun.calorietracker.dto.AdminMfaVerificationDto;
import com.grun.calorietracker.dto.AdminReauthenticationDto;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminSecurityService;
import com.grun.calorietracker.service.AdminMfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/security")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSecurityController {

    private final AdminSecurityService adminSecurityService;
    private final AdminMfaService adminMfaService;

    @GetMapping("/me")
    public ResponseEntity<AdminAccessProfileDto> currentAccess(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(adminSecurityService.currentAccess(userDetails.getUsername()));
    }

    @GetMapping("/team")
    public ResponseEntity<AdminTeamPageDto> listTeam(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ResponseEntity.ok(adminSecurityService.listTeam(page, size));
    }

    @PostMapping("/team/grant")
    public ResponseEntity<AdminTeamMemberDto> grantAccess(
            @RequestBody @Valid AdminAccessGrantRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(adminSecurityService.grantAccess(
                userDetails.getUsername(), request, correlationId(httpRequest)
        ));
    }
    @PatchMapping("/team/{userId}")
    public ResponseEntity<AdminTeamMemberDto> updateMember(
            @PathVariable Long userId,
            @RequestBody @Valid AdminTeamMemberUpdateRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(adminSecurityService.updateMember(
                userDetails.getUsername(),
                userId,
                request,
                correlationId(httpRequest)
        ));
    }

    @GetMapping("/mfa")
    public ResponseEntity<AdminMfaStatusDto> mfaStatus(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(adminMfaService.status(userDetails.getUsername()));
    }

    @PostMapping("/mfa/enrollment")
    public ResponseEntity<AdminMfaEnrollmentDto> beginMfaEnrollment(
            @RequestBody @Valid AdminMfaEnrollmentRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminMfaService.beginEnrollment(userDetails.getUsername(), request.currentPassword(), correlationId(httpRequest)));
    }

    @PostMapping("/mfa/enrollment/verify")
    public ResponseEntity<AdminMfaVerificationDto> verifyMfaEnrollment(
            @RequestBody @Valid AdminMfaCodeRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminMfaService.verifyEnrollment(userDetails.getUsername(), request.code(), correlationId(httpRequest)));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<AdminMfaStatusDto> disableMfa(
            @RequestBody @Valid AdminMfaCodeRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminMfaService.disable(userDetails.getUsername(), request.code(), correlationId(httpRequest)));
    }

    @PostMapping("/mfa/reauthenticate")
    public ResponseEntity<AdminReauthenticationDto> reauthenticate(
            @RequestBody @Valid AdminMfaCodeRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminMfaService.reauthenticate(userDetails.getUsername(), request.code(), correlationId(httpRequest)));
    }
    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}

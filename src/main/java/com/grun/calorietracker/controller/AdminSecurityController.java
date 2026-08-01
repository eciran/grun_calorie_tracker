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
import com.grun.calorietracker.service.AdminSessionService;
import com.grun.calorietracker.service.PasswordResetService;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.dto.AdminSessionPageDto;
import com.grun.calorietracker.dto.AdminSessionRevokeRequestDto;
import com.grun.calorietracker.dto.OwnerAdminSessionPageDto;
import com.grun.calorietracker.dto.PasswordResetResponseDto;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/security")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSecurityController {

    private final AdminSecurityService adminSecurityService;
    private final AdminMfaService adminMfaService;
    private final AdminSessionService adminSessionService;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;

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
    @PreAuthorize("hasRole('OWNER')")
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
    @PreAuthorize("hasRole('OWNER')")
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

    @PostMapping("/team/{userId}/password-reset")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PasswordResetResponseDto> requestMemberPasswordReset(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(passwordResetService.requestAdminPasswordReset(
                userDetails.getUsername(), userId, correlationId(request)
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
        return ResponseEntity.ok(adminMfaService.reauthenticate(userDetails.getUsername(), request.code(), request.purpose(), correlationId(httpRequest)));
    }
    @GetMapping("/owner-sessions")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerAdminSessionPageDto> ownerSessions(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@AuthenticationPrincipal UserDetails user,HttpServletRequest request){return ResponseEntity.ok(adminSessionService.listAllForOwner(user.getUsername(),currentSessionId(request),page,size));}

    @DeleteMapping("/owner-sessions/{sessionId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> revokeAnySession(@PathVariable String sessionId,@RequestBody @Valid AdminSessionRevokeRequestDto body,@AuthenticationPrincipal UserDetails user,HttpServletRequest request){adminSessionService.revokeAnyForOwner(user.getUsername(),sessionId,currentSessionId(request),body.reason(),correlationId(request));return ResponseEntity.noContent().build();}

    @GetMapping("/sessions")
    public ResponseEntity<AdminSessionPageDto> sessions(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size, @AuthenticationPrincipal UserDetails user, HttpServletRequest request) {
        return ResponseEntity.ok(adminSessionService.list(user.getUsername(), currentSessionId(request), page, size));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId, @RequestBody @Valid AdminSessionRevokeRequestDto body, @AuthenticationPrincipal UserDetails user, HttpServletRequest request) {
        adminSessionService.revokeSession(user.getUsername(), sessionId, currentSessionId(request), body.reason(), correlationId(request));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/others")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> revokeOthers(@RequestBody @Valid AdminSessionRevokeRequestDto body, @AuthenticationPrincipal UserDetails user, HttpServletRequest request) {
        adminSessionService.revokeOtherSessions(user.getUsername(), currentSessionId(request), body.reason(), correlationId(request));
        return ResponseEntity.noContent().build();
    }

    private String currentSessionId(HttpServletRequest request) {
        String header=request.getHeader("Authorization");
        if(header==null || !header.startsWith("Bearer ")) throw new IllegalArgumentException("Admin access token is required.");
        return jwtUtil.extractAdminSessionId(header.substring(7));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}

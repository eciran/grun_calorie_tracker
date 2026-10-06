package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.AdminAuthenticationAttemptGuard;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminMfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/admin/mfa/enrollment")
@PreAuthorize("hasAuthority('ADMIN_MFA_ENROLLMENT')")
@RequiredArgsConstructor
public class AdminMfaEnrollmentAuthController {
    private final AdminMfaService mfa;
    private final UserRepository users;
    private final AdminAuthenticationAttemptGuard attemptGuard;

    @PostMapping
    public ResponseEntity<AdminMfaEnrollmentDto> begin(@AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid AdminMfaEnrollmentRequestDto body, HttpServletRequest request) {
        requireEnrollment(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mfa.beginEnrollment(principal.getUsername(), body.currentPassword(), correlationId(request)));
    }

    @PostMapping("/verify")
    public ResponseEntity<AdminMfaVerificationDto> verify(@AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid AdminMfaCodeRequestDto body, HttpServletRequest request) {
        requireEnrollment(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mfa.verifyEnrollment(principal.getUsername(), body.code(), correlationId(request)));
    }

    private void requireEnrollment(UserDetails principal) {
        if (principal == null) throw new AccessDeniedException("MFA enrollment authorization is required.");
        attemptGuard.requireAllowed(principal.getUsername());
        var user = users.findByEmail(principal.getUsername()).orElseThrow(() -> new AccessDeniedException("Admin account was not found."));
        if (!mfa.requiresEnrollment(user)) throw new AccessDeniedException("MFA enrollment authorization is no longer valid.");
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}

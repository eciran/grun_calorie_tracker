package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.FreePromotionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/free-promotion")
@RequiredArgsConstructor
public class AdminFreePromotionController {
    private final FreePromotionService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_READ')")
    public AdminFreePromotionPolicyDto get() { return service.getPolicy(); }

    @PutMapping
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')")
    public AdminFreePromotionPolicyDto update(@RequestBody @Valid AdminFreePromotionPolicyRequestDto request,
            @AuthenticationPrincipal UserDetails user, HttpServletRequest http) {
        Object cid = http.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return service.updatePolicy(request, user.getUsername(), cid == null ? null : cid.toString());
    }
}

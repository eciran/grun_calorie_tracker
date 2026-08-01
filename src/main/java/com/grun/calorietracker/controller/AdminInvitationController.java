package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AdminInvitationController {
 private final AdminInvitationService service;
 @GetMapping("/api/v1/admin/security/invitations") @PreAuthorize("hasRole('OWNER')") public ResponseEntity<AdminInvitationPageDto> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return ResponseEntity.ok(service.list(page,size));}
 @PostMapping("/api/v1/admin/security/invitations") @PreAuthorize("hasRole('OWNER')") public ResponseEntity<AdminInvitationDto> create(@Valid @RequestBody AdminInvitationCreateRequestDto body,@AuthenticationPrincipal UserDetails user,HttpServletRequest request){return ResponseEntity.ok(service.create(user.getUsername(),body,cid(request)));}
 @PostMapping("/api/v1/admin/security/invitations/{id}/resend") @PreAuthorize("hasRole('OWNER')") public ResponseEntity<AdminInvitationDto> resend(@PathVariable Long id,@AuthenticationPrincipal UserDetails user,HttpServletRequest request){return ResponseEntity.ok(service.resend(user.getUsername(),id,cid(request)));}
 @DeleteMapping("/api/v1/admin/security/invitations/{id}") @PreAuthorize("hasRole('OWNER')") public ResponseEntity<Void> revoke(@PathVariable Long id,@AuthenticationPrincipal UserDetails user,HttpServletRequest request){service.revoke(user.getUsername(),id,cid(request));return ResponseEntity.noContent().build();}
 @GetMapping("/api/v1/auth/admin-invitations/inspect") public ResponseEntity<AdminInvitationDto> inspect(@RequestParam String token){return ResponseEntity.ok(service.inspect(token));}
 @PostMapping("/api/v1/auth/admin-invitations/accept") public ResponseEntity<Void> accept(@Valid @RequestBody AdminInvitationAcceptRequestDto body){service.accept(body);return ResponseEntity.noContent().build();}
 private String cid(HttpServletRequest request){Object value=request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);return value==null?request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER):value.toString();}
}

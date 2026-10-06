package com.grun.calorietracker.controller;

import com.grun.calorietracker.service.SubscriptionPurchaseVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions/me/verification")
@RequiredArgsConstructor
public class SubscriptionVerificationController {
    private final SubscriptionPurchaseVerificationService service;
    public record Request(@NotBlank @Size(max = 255) String productId,
                          @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String attemptId,
                          @Size(max = 255) String transactionId) { }

    @PostMapping("/prepare")
    public ResponseEntity<SubscriptionPurchaseVerificationService.Result> prepare(
            @AuthenticationPrincipal UserDetails principal, @Valid @RequestBody Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.prepare(principal.getUsername(), request.productId(), request.attemptId()));
    }

    @GetMapping("/pending")
    public ResponseEntity<SubscriptionPurchaseVerificationService.PendingAttempt> pending(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.pending(principal.getUsername()));
    }

    public record CancelRequest(@NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String attemptId) { }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelUnconfirmedAttempt(@AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CancelRequest request) {
        service.cancelUnconfirmedAttempt(principal.getUsername(), request.attemptId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping
    public ResponseEntity<SubscriptionPurchaseVerificationService.Result> verify(
            @AuthenticationPrincipal UserDetails principal, @Valid @RequestBody Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(request.transactionId() == null
                        ? service.request(principal.getUsername(), request.productId(), request.attemptId())
                        : service.request(principal.getUsername(), request.productId(), request.attemptId(), request.transactionId()));
    }
}

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
                          @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String attemptId) { }

    @PostMapping
    public ResponseEntity<SubscriptionPurchaseVerificationService.Result> verify(
            @AuthenticationPrincipal UserDetails principal, @Valid @RequestBody Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.request(principal.getUsername(), request.productId(), request.attemptId()));
    }
}

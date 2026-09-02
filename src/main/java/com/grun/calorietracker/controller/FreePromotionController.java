package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.service.FreePromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/free-promotion")
@RequiredArgsConstructor
public class FreePromotionController {
    private final FreePromotionService service;

    @PostMapping("/decision")
    public FreePromotionDecisionDto decision(@RequestBody @Valid FreePromotionDecisionRequestDto request,
                                              @AuthenticationPrincipal UserDetails user) {
        return service.decide(user.getUsername(), request);
    }
    @PostMapping("/impression") public ResponseEntity<Void> impression(@RequestBody @Valid FreePromotionEventRequestDto request,
            @AuthenticationPrincipal UserDetails user) { service.recordImpression(user.getUsername(), request); return ResponseEntity.noContent().build(); }
    @PostMapping("/dismissal") public ResponseEntity<Void> dismissal(@RequestBody @Valid FreePromotionEventRequestDto request,
            @AuthenticationPrincipal UserDetails user) { service.recordDismissal(user.getUsername(), request); return ResponseEntity.noContent().build(); }
    @PostMapping("/cta") public ResponseEntity<Void> cta(@RequestBody @Valid FreePromotionEventRequestDto request,
            @AuthenticationPrincipal UserDetails user) { service.recordCta(user.getUsername(), request); return ResponseEntity.noContent().build(); }
}

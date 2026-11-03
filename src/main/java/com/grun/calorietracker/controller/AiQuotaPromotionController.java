package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiQuotaPromotionDecisionDto;
import com.grun.calorietracker.dto.AiQuotaPromotionDecisionRequestDto;
import com.grun.calorietracker.dto.AiQuotaPromotionEventRequestDto;
import com.grun.calorietracker.service.AiQuotaPromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-quota-promotion")
@RequiredArgsConstructor
public class AiQuotaPromotionController {
    private final AiQuotaPromotionService service;

    @PostMapping("/decision")
    public AiQuotaPromotionDecisionDto decision(@RequestBody @Valid AiQuotaPromotionDecisionRequestDto request,
                                                 @AuthenticationPrincipal UserDetails user) {
        return service.decide(user.getUsername(), request);
    }

    @PostMapping("/impression")
    public ResponseEntity<Void> impression(@RequestBody @Valid AiQuotaPromotionEventRequestDto request,
                                           @AuthenticationPrincipal UserDetails user) {
        service.recordImpression(user.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dismissal")
    public ResponseEntity<Void> dismissal(@RequestBody @Valid AiQuotaPromotionEventRequestDto request,
                                          @AuthenticationPrincipal UserDetails user) {
        service.recordDismissal(user.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cta")
    public ResponseEntity<Void> cta(@RequestBody @Valid AiQuotaPromotionEventRequestDto request,
                                    @AuthenticationPrincipal UserDetails user) {
        service.recordCta(user.getUsername(), request);
        return ResponseEntity.noContent().build();
    }
}

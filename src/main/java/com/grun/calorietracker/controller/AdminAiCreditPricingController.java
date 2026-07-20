package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminAiCreditPricingPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AiCreditPricingPolicyDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AiCreditPricingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ai-credit-pricing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiCreditPricingController {
    private final AiCreditPricingService pricingService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<List<AiCreditPricingPolicyDto>> listPolicies() {
        return ResponseEntity.ok(pricingService.listPolicies());
    }

    @PutMapping("/{feature}")
    public ResponseEntity<AiCreditPricingPolicyDto> updatePolicy(
            @PathVariable SubscriptionFeature feature,
            @RequestBody @Valid AdminAiCreditPricingPolicyUpdateRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        AiCreditPricingPolicyDto before = pricingService.listPolicies().stream()
                .filter(item -> item.getFeature() == feature)
                .findFirst()
                .orElse(null);
        AiCreditPricingPolicyDto response = pricingService.updatePolicy(feature, request);
        adminAuditService.record(
                userDetails == null ? "unknown-admin" : userDetails.getUsername(),
                AdminAuditActionType.AI_CREDIT_PRICING_UPDATE,
                AdminAuditTargetType.AI_CREDIT_PRICING,
                feature.name(),
                before,
                response,
                correlationId(httpRequest));
        return ResponseEntity.ok(response);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null
                ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)
                : value.toString();
    }
}

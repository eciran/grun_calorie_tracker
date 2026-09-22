package com.grun.calorietracker.controller;

import com.grun.calorietracker.service.EcbReferenceRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiExchangeRateController {
    private final EcbReferenceRateService rates;

    @GetMapping("/api/v1/admin/ai/monitoring/exchange-rate")
    public EcbReferenceRateService.Rate latest() {
        return rates.latest();
    }
}

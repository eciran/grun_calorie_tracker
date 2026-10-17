package com.grun.calorietracker.service.support;

import com.grun.calorietracker.service.impl.ProductCatalogAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCatalogAuditScheduler {
    private final ProductCatalogAuditService auditService;

    // The first tick catches up after restart; checkpoints and leases are stored in the DB.
    @Scheduled(fixedDelayString = "${grun.product-catalog.audit.tick-ms:60000}", initialDelayString = "${grun.product-catalog.audit.initial-delay-ms:10000}")
    public void tick() { auditService.scheduledStep(); }
}

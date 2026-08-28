package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.ProductNutritionOcrShadowService;
import com.grun.calorietracker.service.model.ProductNutritionOcrShadowRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductNutritionOcrShadowEventListener {
    private static final Logger log = LoggerFactory.getLogger(ProductNutritionOcrShadowEventListener.class);
    private final ProductNutritionOcrShadowService shadowService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(ProductNutritionOcrShadowRequestedEvent event) {
        try {
            shadowService.compare(event.request());
        } catch (RuntimeException exception) {
            log.warn("product_ocr_shadow_failed caseId={} type={}",
                    event.request().fallbackRequest().reviewCaseId(), exception.getClass().getSimpleName());
        }
    }
}

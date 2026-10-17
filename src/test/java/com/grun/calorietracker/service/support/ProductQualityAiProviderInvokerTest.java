package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AiProductQualityValidationRequestDto;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductQualityAiProviderInvokerTest {

    @Test
    void providerCallSuspendsAnyCatalogTransaction() throws Exception {
        Transactional transactional = ProductQualityAiProviderInvoker.class
                .getMethod("validate", AiMealDraftProviderClient.class, AiProductQualityValidationRequestDto.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation());
    }
}

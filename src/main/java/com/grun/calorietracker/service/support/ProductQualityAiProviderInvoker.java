package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AiProductQualityValidationRequestDto;
import com.grun.calorietracker.dto.AiProductQualityValidationResponseDto;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Keeps remote AI latency outside the caller's database transaction. */
@Component
public class ProductQualityAiProviderInvoker {

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiProductQualityValidationResponseDto validate(
            AiMealDraftProviderClient provider,
            AiProductQualityValidationRequestDto request
    ) {
        return provider.validateProductQuality(request);
    }
}

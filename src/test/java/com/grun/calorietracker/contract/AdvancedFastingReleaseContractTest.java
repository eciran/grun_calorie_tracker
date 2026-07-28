package com.grun.calorietracker.contract;

import com.grun.calorietracker.enums.AdvancedFastingErrorCode;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.AdvancedFastingException;
import com.grun.calorietracker.exception.GlobalExceptionHandler;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.security.SubscriptionFeatureAccessFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancedFastingReleaseContractTest {

    @Test
    void preservesBasicAndAdvancedEntitlementBoundaries() {
        assertThat(SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/fasting/plan"))
                .isEqualTo(SubscriptionFeature.FASTING_BASIC);
        assertThat(SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/fasting/summary/range"))
                .isEqualTo(SubscriptionFeature.FASTING_BASIC);
        assertThat(SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/fasting/advanced/programs"))
                .isEqualTo(SubscriptionFeature.FASTING_ADVANCED);
        assertThat(SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/fasting/advanced/analytics"))
                .isEqualTo(SubscriptionFeature.FASTING_ADVANCED);
    }

    @Test
    void returnsStableLocalizationReadyAdvancedFastingErrorCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new StaticMessageSource(), false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fasting/advanced/occurrences/1/start");
        request.setAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "fasting-contract-1");

        var response = handler.handleAdvancedFastingException(
                new AdvancedFastingException(
                        AdvancedFastingErrorCode.SKIPPED_OCCURRENCE_CANNOT_BE_STARTED,
                        "Skipped occurrences cannot be started."),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SKIPPED_OCCURRENCE_CANNOT_BE_STARTED");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("fasting-contract-1");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/fasting/advanced/occurrences/1/start");
    }
}
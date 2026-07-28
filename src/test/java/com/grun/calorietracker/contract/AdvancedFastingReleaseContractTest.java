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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

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
        assertThat(SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/fasting/advanced/reminder-settings"))
                .isEqualTo(SubscriptionFeature.FASTING_ADVANCED);
        assertThat(SubscriptionFeatureAccessFilter.resolveFeature("PUT", "/api/v1/fasting/advanced/occurrences/2026-08-01/exception"))
                .isEqualTo(SubscriptionFeature.FASTING_ADVANCED);
        assertThat(SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/fasting/advanced/occurrences/2026-08-01/nutrition-summary"))
                .isEqualTo(SubscriptionFeature.FASTING_ADVANCED);
        assertThat(SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/fasting/advanced/diary-conflicts/evaluate"))
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

    @Test
    void resolvesDublinDstGapDeterministically() {
        var resolved = LocalDate.of(2026, 3, 29).atTime(LocalTime.of(1, 30)).atZone(ZoneId.of("Europe/Dublin"));
        assertThat(resolved.toLocalTime()).isEqualTo(LocalTime.of(2, 30));
        assertThat(resolved.getOffset().getTotalSeconds()).isEqualTo(3600);
    }

    @Test
    void advancedFastingMigrationsPreserveCascadeAndIntegrityContracts() throws Exception {
        String history = Files.readString(Path.of("src/main/resources/db/migration/V191__add_fasting_history_corrections.sql"));
        String reminders = Files.readString(Path.of("src/main/resources/db/migration/V193__add_advanced_fasting_reminder_settings.sql"));
        String exceptions = Files.readString(Path.of("src/main/resources/db/migration/V194__add_fasting_schedule_exceptions.sql"));

        assertThat(history).contains("REFERENCES users(id) ON DELETE CASCADE", "chk_fasting_history_correction_action");
        assertThat(reminders).contains("REFERENCES users(id) ON DELETE CASCADE", "chk_advanced_fasting_pre_start_minutes");
        assertThat(exceptions).contains("uk_fasting_exception_user_source_date", "REFERENCES users(id) ON DELETE CASCADE",
                "REFERENCES fasting_programs(id) ON DELETE CASCADE", "chk_fasting_exception_type");
    }
}
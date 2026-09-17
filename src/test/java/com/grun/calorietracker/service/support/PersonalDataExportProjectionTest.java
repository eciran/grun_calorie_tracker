package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.GdprDataExportDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PersonalDataExportProjectionTest {
    @Test
    void keepsOnlyTypedUserCorrectionFacts() throws Exception {
        String projected = PersonalDataExportProjection.correctionSummary(
                "{\"confirmedItemCount\":2,\"portionsChanged\":true,\"model\":\"private\","
                        + "\"totalTokens\":2111,\"nested\":{\"cost\":0.02},\"createdLogCount\":\"private\"}");
        var json = new ObjectMapper().readTree(projected);
        assertEquals(2, json.size());
        assertEquals(2, json.path("confirmedItemCount").asInt());
        assertTrue(json.path("portionsChanged").asBoolean());
    }

    @Test
    void excludesRetryDiagnosticsMalformedAndUnknownPayloads() {
        for (String raw : Arrays.asList(null, "", "Controlled nutrition validation retry succeeded: private",
                "{", "[]", "null", "{\"model\":\"private\"}", "{\"portionsChanged\":\"private\"}")) {
            assertNull(PersonalDataExportProjection.correctionSummary(raw));
        }
    }

    @Test
    void aiExportContractContainsOnlyPersonalHistoryAndCreditFields() {
        Set<String> fields = Arrays.stream(GdprDataExportDto.AiRequestExportDto.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
        assertEquals(Set.of("id", "requestType", "status", "quotaConsumed", "correctionSummary",
                "rejectionReason", "rejectionFeedback", "quotaRefundedAmount", "createdAt",
                "confirmedAt", "rejectedAt", "quotaRefundedAt"), fields);
    }

    @Test
    void relatedExportContractsDoNotExposeInternalConfiguration() {
        for (Class<?> type : Set.of(GdprDataExportDto.MealPlanExportDto.class,
                GdprDataExportDto.MealPlanItemExportDto.class, GdprDataExportDto.SubscriptionEventExportDto.class,
                GdprDataExportDto.ProductAnalyticsEventExportDto.class)) {
            Set<String> fields = Arrays.stream(type.getDeclaredFields())
                    .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
            assertTrue(java.util.Collections.disjoint(fields,
                    Set.of("promptVersion", "schemaVersion", "provider", "entitlementIds", "environment", "metadataJson")));
        }
    }
}

package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.prompt.AiPromptTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiCoachingPresentationTest {
    @Test
    void cleansHistoricalTextWithoutMutatingStoredPayloadOrMachineFields() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var original = mapper.readTree("{\"summary\":\"Rest today.\",\"keyFindings\":[{\"evidence\":\"totalExerciseMinutes=30\"}],"
                + "\"linkedMetric\":\"totalExerciseMinutes\",\"warnings\":[\"diaryDays=5\",\"Keep a rest day.\"]}");
        var cleaned = AiCoachingPresentation.cleanHistory(original);
        assertEquals("", cleaned.path("keyFindings").get(0).path("evidence").asText());
        assertEquals("totalExerciseMinutes=30", original.path("keyFindings").get(0).path("evidence").asText());
        assertEquals("totalExerciseMinutes", cleaned.path("linkedMetric").asText());
        assertEquals(1, cleaned.path("warnings").size());
    }
    @Test
    void removesRawEvidenceWithoutLosingHumanObservationOrMetricLink() {
        var response = new AiInsightResponseDto();
        response.setSummary("Recorded exercise was 30 minutes.");
        var finding = new AiInsightResponseDto.KeyFinding();
        finding.setMessage("Recorded exercise was 30 minutes.");
        finding.setEvidence("totalExerciseMinutes=30, averageBurnedCalories=33");
        finding.setImpact("Recorded exercise was 30 minutes.");
        response.setKeyFindings(List.of(finding, finding));
        var action = new AiInsightResponseDto.PersonalizedAction();
        action.setAction("Record your rest after training.");
        action.setReason("Record your rest after training.");
        action.setLinkedMetric("totalExerciseMinutes");
        response.setPersonalizedActions(List.of(action, action));
        AiCoachingPresentation.clean(response);
        assertEquals(1, response.getKeyFindings().size());
        assertEquals("", finding.getEvidence());
        assertEquals("", finding.getImpact());
        assertEquals("Recorded exercise was 30 minutes.", response.getSummary());
        assertEquals(1, response.getPersonalizedActions().size());
        assertEquals("", action.getReason());
        assertEquals("totalExerciseMinutes", action.getLinkedMetric());
    }

    @Test
    void preservesNaturalLanguageAndRemovesOnlyDiagnosticSentences() {
        var response = new AiInsightResponseDto();
        response.setSummary("Your records cover five days. diaryDays=5. Record tomorrow's meals.");
        response.setWarnings(List.of("Keep a rest day.", "Keep a rest day.", "averageConsumedCalories=840.84"));
        AiCoachingPresentation.clean(response);
        assertEquals("Your records cover five days. Record tomorrow's meals.", response.getSummary());
        assertEquals(List.of("Keep a rest day."), response.getWarnings());
    }

    @Test
    void coachingRulesApplyOnlyToInsights() {
        for (var type : List.of(AiRequestType.AI_DAILY_INSIGHT, AiRequestType.AI_WEEKLY_INSIGHT)) {
            String prompt = AiPromptTemplates.request(type, "{}");
            assertTrue(prompt.contains("request.focus"));
            assertTrue(prompt.contains("RECOVERY:"));
            assertTrue(prompt.contains("Missing records are unknown"));
            assertTrue(prompt.contains("at most 3 distinct"));
            assertTrue(prompt.contains("request.language"));
        }
        assertFalse(AiPromptTemplates.request(AiRequestType.AI_RECIPE_GENERATION, "{}")
                .contains("Produce concise coaching"));
        assertFalse(AiPromptTemplates.request(AiRequestType.PHOTO_MEAL_LOG, "{}")
                .contains("Produce concise coaching"));
    }
}

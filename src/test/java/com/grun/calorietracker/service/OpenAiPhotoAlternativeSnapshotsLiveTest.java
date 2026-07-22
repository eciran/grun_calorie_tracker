package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftAlternativeCandidateDto;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.service.impl.OpenAiAiMealDraftProviderClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenAiPhotoAlternativeSnapshotsLiveTest {

    @Test
    void livePhotoAlternativeSnapshotSmoke() {
        String apiKey = System.getenv("GRUN_AI_OPENAI_API_KEY");
        String imagePathValue = System.getenv("GRUN_AI_SMOKE_IMAGE_PATH");
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("GRUN_RUN_OPENAI_PHOTO_ALTERNATIVE_SMOKE")));
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank());
        Assumptions.assumeTrue(imagePathValue != null && !imagePathValue.isBlank());

        Path imagePath = Path.of(imagePathValue).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(imagePath));

        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.OPENAI);
        properties.setModel(environment("GRUN_AI_MODEL", "gpt-5.4"));
        properties.setPromptVersion(environment("GRUN_AI_PROMPT_VERSION", "ai-prompt-v2-photo-alt-exp1"));
        properties.getPhoto().setAlternativeSnapshotsEnabled(true);
        properties.getPhoto().setMaxAlternativeSnapshots(2);
        properties.getPhoto().setStorageDirectory(imagePath.getParent().toString());
        properties.getPhoto().setPublicBaseUrl("https://api.grun.test");
        properties.getOpenai().setApiKey(apiKey);
        properties.getOpenai().setBaseUrl(environment(
                "GRUN_AI_OPENAI_BASE_URL", "https://api.openai.com/v1/responses"));
        properties.getOpenai().setTimeout(Duration.ofSeconds(120));
        properties.getOpenai().setMaxOutputTokens(12_000);
        properties.getOpenai().setInputTokenCostPer1m(2.50d);
        properties.getOpenai().setOutputTokenCostPer1m(15.00d);
        properties.getOpenai().setCostCurrency("USD");

        OpenAiAiMealDraftProviderClient client = new OpenAiAiMealDraftProviderClient(
                properties, new RestTemplate(), new ObjectMapper());
        AiPhotoMealDraftRequestDto request = new AiPhotoMealDraftRequestDto();
        request.setImageReference("https://api.grun.test/api/v1/ai/meal-drafts/photo-references/"
                + imagePath.getFileName());
        request.setUserNote("Estimate the complete visible meal. Group identical pieces.");

        long startedAt = System.nanoTime();
        AiMealDraftResponseDto response = client.createPhotoMealDraft(request);
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertNotNull(response);
        assertFalse(response.getItems().isEmpty());
        int alternativeCount = response.getItems().stream()
                .map(AiMealDraftItemDto::getAlternativeCandidates)
                .filter(java.util.Objects::nonNull)
                .mapToInt(List::size)
                .sum();
        AiMealDraftItemDto primary = response.getItems().get(0);
        String alternatives = primary.getAlternativeCandidates() == null
                ? "[]"
                : primary.getAlternativeCandidates().stream()
                .map(OpenAiPhotoAlternativeSnapshotsLiveTest::candidateSummary)
                .toList().toString();

        System.out.printf(
                "AI_SMOKE_RESULT model=%s schema=%s latencyMs=%d inputTokens=%s outputTokens=%s totalTokens=%s costUsd=%s items=%d alternatives=%d primary=%s quantity=%s unit=%s grams=%s candidateSummaries=%s%n",
                properties.getModel(), response.getSchemaVersion(), latencyMs, response.getPromptTokens(),
                response.getCompletionTokens(), response.getTotalTokens(), response.getEstimatedCost(),
                response.getItems().size(), alternativeCount, primary.getName(), primary.getQuantity(),
                primary.getUnit(), primary.getEstimatedTotalWeightGrams(), alternatives);
    }

    private static String candidateSummary(AiMealDraftAlternativeCandidateDto candidate) {
        Double calories = candidate.getEstimatedNutrition() == null
                ? null
                : candidate.getEstimatedNutrition().getCalories();
        return candidate.getName() + "(" + calories + " kcal," + candidate.getConfidence() + ")";
    }

    private static String environment(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
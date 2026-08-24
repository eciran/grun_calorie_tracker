package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AiMealDraftAlternativeCandidateDto;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.exception.AiOutputLanguageMismatchException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Conservative EN/TR language gate for AI-generated user-visible meal-draft prose. */
public final class AiMealDraftLanguageValidator {
    private static final Set<String> TURKISH_MARKERS = Set.of(
            "bir", "ve", "ile", "icin", "bu", "olarak", "gorunuyor", "fotografta",
            "urun", "tahmin", "edildi", "net", "olmadigi", "marka", "agirlik", "seker",
            "besin", "gore", "yalnizca", "degisebilir", "goruluyor", "uyumlu");
    private static final Set<String> ENGLISH_MARKERS = Set.of(
            "the", "and", "with", "for", "this", "appears", "photo", "product", "estimated",
            "estimate", "visible", "brand", "weight", "nutrition", "because", "from", "only",
            "review", "portion", "shown", "looks", "uncertain", "confirm");

    private AiMealDraftLanguageValidator() {
    }

    public static void validate(AiMealDraftResponseDto response, String expectedLanguage) {
        if (response == null || !("en".equals(expectedLanguage) || "tr".equals(expectedLanguage))) {
            return;
        }
        String text = String.join(" ", userVisibleText(response));
        if (text.isBlank()) {
            return;
        }
        int turkishScore = markerScore(text, TURKISH_MARKERS) + turkishCharacterScore(text);
        int englishScore = markerScore(text, ENGLISH_MARKERS);
        boolean mismatch = "en".equals(expectedLanguage)
                ? turkishScore >= 3 && turkishScore >= englishScore + 2
                : englishScore >= 4 && englishScore >= turkishScore + 2;
        if (mismatch) {
            throw new AiOutputLanguageMismatchException(expectedLanguage);
        }
    }

    private static List<String> userVisibleText(AiMealDraftResponseDto response) {
        List<String> values = new ArrayList<>();
        add(values, response.getSummary());
        add(values, response.getUserMessage());
        add(values, response.getProfessionalSummary());
        addAll(values, response.getAssumptions());
        addAll(values, response.getNextBestActions());
        addAll(values, response.getReviewReasons());
        if (response.getItems() != null) {
            for (AiMealDraftItemDto item : response.getItems()) {
                if (item == null) continue;
                add(values, item.getNutritionEstimateNote());
                add(values, item.getMatchReason());
                add(values, item.getSafetyWarning());
                add(values, item.getReasoning());
                add(values, item.getPortionNote());
                if (item.getAlternativeCandidates() != null) {
                    for (AiMealDraftAlternativeCandidateDto alternative : item.getAlternativeCandidates()) {
                        if (alternative == null) continue;
                        add(values, alternative.getNutritionEstimateNote());
                        add(values, alternative.getMatchReason());
                    }
                }
            }
        }
        return values;
    }

    private static int markerScore(String text, Set<String> markers) {
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        int score = 0;
        for (String token : normalized.split("[^a-z]+")) {
            if (markers.contains(token)) score++;
        }
        return score;
    }

    private static int turkishCharacterScore(String text) {
        int score = 0;
        for (int i = 0; i < text.length(); i++) {
            if ("çÇğĞıİöÖşŞüÜ".indexOf(text.charAt(i)) >= 0) score++;
        }
        return Math.min(score, 4);
    }

    private static void add(List<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private static void addAll(List<String> values, List<String> source) {
        if (source != null) source.forEach(value -> add(values, value));
    }
}

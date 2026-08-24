package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.exception.AiOutputLanguageMismatchException;
import com.grun.calorietracker.service.support.AiMealDraftLanguageValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiMealDraftLanguageValidatorTest {
    @Test
    void validate_rejectsTurkishProseForEnglishDraft() {
        AiMealDraftResponseDto response = response(
                "Fotoğrafta tek bir sert şeker görünüyor ve ürün ağırlığı yaklaşık olarak tahmin edildi.");
        response.setReviewReasons(List.of("Marka ve gramaj görünmüyor.", "Besin değeri buna göre değişebilir."));

        assertThrows(AiOutputLanguageMismatchException.class,
                () -> AiMealDraftLanguageValidator.validate(response, "en"));
    }

    @Test
    void validate_acceptsEnglishProseForEnglishDraft() {
        AiMealDraftResponseDto response = response(
                "The photo appears to show one hard candy, and its weight was estimated from the visible portion.");
        response.setReviewReasons(List.of("The brand and exact weight are not visible."));

        assertDoesNotThrow(() -> AiMealDraftLanguageValidator.validate(response, "en"));
    }

    @Test
    void validate_rejectsEnglishProseForTurkishDraft() {
        AiMealDraftResponseDto response = response(
                "The product appears in the photo and the portion was estimated from the visible weight.");
        response.setReviewReasons(List.of("Review and confirm the portion because the brand is uncertain."));

        assertThrows(AiOutputLanguageMismatchException.class,
                () -> AiMealDraftLanguageValidator.validate(response, "tr"));
    }

    private AiMealDraftResponseDto response(String summary) {
        AiMealDraftItemDto item = new AiMealDraftItemDto();
        item.setName("Hard candy");
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setSummary(summary);
        response.setItems(List.of(item));
        return response;
    }
}

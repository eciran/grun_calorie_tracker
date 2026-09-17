package com.grun.calorietracker.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class AiPhotoInputExceptionHandlerTest {
    @Test
    void returnsLocalized422WithStableCodes() {
        var handler = new GlobalExceptionHandler(new StaticMessageSource(), false);
        for (String code : new String[]{"NO_FOOD_DETECTED", "IMAGE_UNCLEAR"}) {
            for (String language : new String[]{"tr", "en"}) {
                var request = new MockHttpServletRequest();
                request.addPreferredLocale(Locale.forLanguageTag(language));
                request.setRequestURI("/api/v1/ai/meal-drafts/photo");
                var error = new AiPhotoInputException(code);
                var response = handler.handleAiPhotoInput(error, request);
                assertEquals(422, response.getStatusCode().value());
                assertNotNull(response.getBody());
                assertEquals(code, response.getBody().getCode());
                assertEquals(error.userMessage(language.equals("tr")), response.getBody().getMessage());
            }
        }
    }
}

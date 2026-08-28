package com.grun.calorietracker.exception;

import com.grun.calorietracker.dto.AdvancedGoalRequestDto;
import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.enums.GoalCalculationMode;
import com.grun.calorietracker.enums.GoalControlledStrategy;
import com.grun.calorietracker.service.support.AdvancedMacroTargetPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class GoalValidationContractTest {
    @Test void localizedErrorsExposeStableCodeNotDiagnostics() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        var handler = new GlobalExceptionHandler(messages, false);
        for (String lang : new String[]{"tr", "en"}) {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/goals/advanced/preview");
            request.addPreferredLocale(Locale.forLanguageTag(lang));
            var response = handler.handleIllegalArgumentException(
                    new GoalValidationException("GOAL_PRIORITY_MACRO_COUNT", "internal diagnostic"), request);
            assertEquals(400, response.getStatusCode().value());
            assertEquals("GOAL_PRIORITY_MACRO_COUNT", response.getBody().getCode());
            assertEquals(lang.equals("tr") ? "Yalnızca bir makro doldurun; diğer iki alanı boş bırakın."
                    : "Fill in exactly one macro and leave the other two blank.", response.getBody().getMessage());
        }
    }
    @Test void macroCountAndBudgetFailuresKeepStableCodes() {
        var policy = new AdvancedMacroTargetPolicy();
        var request = new AdvancedGoalRequestDto();
        var reference = new GoalCalculationResponse(2000, 125, 67, 224);
        request.setMode(GoalCalculationMode.MANUAL);
        assertEquals("GOAL_MANUAL_MACROS_REQUIRED", assertThrows(GoalValidationException.class,
                () -> policy.preview(request, reference)).getCode());
        request.setMode(GoalCalculationMode.CONTROLLED);
        request.setStrategy(GoalControlledStrategy.CALORIES_FIXED);
        assertEquals("GOAL_CONTROLLED_MACRO_COUNT", assertThrows(GoalValidationException.class,
                () -> policy.preview(request, reference)).getCode());
        request.setProteinGrams(500.0);
        assertEquals("GOAL_MACRO_BUDGET_EXCEEDED", assertThrows(GoalValidationException.class,
                () -> policy.preview(request, reference)).getCode());
    }
    @Test void manualReferenceIgnoresHiddenPaceButControlledRetainsIt() {
        var request = new AdvancedGoalRequestDto();
        request.setWeeklyWeightChangeTargetKg(0.0);
        request.setMode(GoalCalculationMode.MANUAL);
        assertNull(request.automaticRequest().getWeeklyWeightChangeTargetKg());
        request.setMode(GoalCalculationMode.CONTROLLED);
        assertEquals(0.0, request.automaticRequest().getWeeklyWeightChangeTargetKg());
    }
}

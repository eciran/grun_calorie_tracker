package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.prompt.AiPromptTemplates;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiCoachingPromptScopeTest {
    @Test
    void newDataRulesApplyOnlyToDailyAndWeeklyCoaching() {
        for (AiRequestType type : AiRequestType.values()) {
            String prompt = type == AiRequestType.AI_NUTRITION_PLAN
                    ? AiPromptTemplates.nutrition("test guardrails", "test") : AiPromptTemplates.request(type, "test");
            boolean coaching = type == AiRequestType.AI_DAILY_INSIGHT || type == AiRequestType.AI_WEEKLY_INSIGHT;
            assertEquals(coaching, prompt.contains("waterMl/waterTargetMl"), type.name());
            assertEquals(coaching, prompt.contains("dailyData contains dated observations"), type.name());
        }
    }
}

package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.enums.PreferredLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AchievementServiceImplLocalizationTest {

    @Test
    void returnsTurkishContentForTurkishUsers() {
        assertEquals(
                "İlk Öğün Kaydedildi",
                AchievementServiceImpl.localizedText(
                        PreferredLanguage.TR,
                        "  İlk Öğün Kaydedildi  ",
                        "First Meal Logged"
                )
        );
    }

    @Test
    void fallsBackToEnglishWhenTurkishContentIsMissing() {
        assertEquals(
                "First Meal Logged",
                AchievementServiceImpl.localizedText(PreferredLanguage.TR, " ", "First Meal Logged")
        );
    }

    @Test
    void returnsEnglishContentForEnglishAndUnspecifiedUsers() {
        assertEquals(
                "First Meal Logged",
                AchievementServiceImpl.localizedText(PreferredLanguage.EN, "İlk Öğün Kaydedildi", "First Meal Logged")
        );
        assertEquals(
                "First Meal Logged",
                AchievementServiceImpl.localizedText(null, "İlk Öğün Kaydedildi", "First Meal Logged")
        );
    }
}

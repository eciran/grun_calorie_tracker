package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleConfigTest {

    @Test
    void resolveSupportedLocale_keepsTurkishRegionalVariants() {
        assertEquals(LocaleConfig.TURKISH, LocaleConfig.resolveSupportedLocale(Locale.forLanguageTag("tr-TR")));
    }

    @Test
    void resolveSupportedLocale_fallsBackToEnglishForUnsupportedLanguage() {
        assertEquals(LocaleConfig.ENGLISH, LocaleConfig.resolveSupportedLocale(Locale.GERMAN));
        assertEquals(LocaleConfig.ENGLISH, LocaleConfig.resolveSupportedLocale(null));
    }
}

package com.grun.calorietracker.service.support;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationTextTest {
    @Test void capitalizesOpeningLetterUsingTurkishRules() {
        Locale tr = Locale.forLanguageTag("tr-TR");
        assertEquals("Tarif taslağı hazır!", NotificationText.sentenceStart("tarif taslağı hazır!", tr));
        assertEquals("İnceleme hazır", NotificationText.sentenceStart("inceleme hazır", tr));
        assertEquals("Işık", NotificationText.sentenceStart("ışık", tr));
        assertEquals("✨ Tarif hazır", NotificationText.sentenceStart("✨ tarif hazır", tr));
    }
    @Test void preservesRestOfTextAndHandlesEmptyAndNumericText() {
        assertEquals("Insight with AI", NotificationText.sentenceStart("insight with AI", Locale.ENGLISH));
        assertEquals("GRUN AI", NotificationText.sentenceStart("GRUN AI", Locale.ENGLISH));
        assertEquals("100g", NotificationText.sentenceStart("100g", Locale.ENGLISH));
        assertEquals("", NotificationText.sentenceStart("", Locale.ENGLISH));
        assertNull(NotificationText.sentenceStart(null, Locale.ENGLISH));
    }
}

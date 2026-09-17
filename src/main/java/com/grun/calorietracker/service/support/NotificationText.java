package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NotificationText {
    private static final Pattern OPENING = Pattern.compile("^([^\\p{L}\\p{N}]*)(\\p{L})");
    private NotificationText() {}
    public static String sentenceStart(String text, NotificationEntity notification) {
        boolean turkish = notification != null && notification.getUser() != null
                && notification.getUser().getPreferredLanguage() == PreferredLanguage.TR;
        return sentenceStart(text, turkish ? Locale.forLanguageTag("tr-TR") : Locale.ENGLISH);
    }
    public static String sentenceStart(String text, Locale locale) {
        if (text == null || text.isEmpty()) return text;
        var match = OPENING.matcher(text);
        if (!match.find()) return text;
        return match.group(1) + match.group(2).toUpperCase(locale) + text.substring(match.end());
    }
}

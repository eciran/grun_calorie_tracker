package com.grun.calorietracker.service.support;

import java.text.Normalizer;
import java.util.Locale;

public final class ExerciseNameNormalizer {
    private ExerciseNameNormalizer() {}
    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT)
                        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                        .replace('ç', 'c').replace('ö', 'o').replace('ü', 'u'), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim().replaceAll("\\s+", " ");
    }
}

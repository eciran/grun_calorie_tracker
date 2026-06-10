package com.grun.calorietracker.service.support;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FoodProductNormalizationRules {

    private FoodProductNormalizationRules() {
    }

    public static String normalizeBarcode(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.replaceAll("[\\s-]+", "");
        if (normalized.isBlank()) {
            return null;
        }

        return normalized.toUpperCase(Locale.ROOT);
    }

    public static String normalizeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    public static List<String> expandSearchTerms(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        addTerm(terms, normalized);
        addTerm(terms, stripDiacritics(normalized));
        addSynonyms(terms, normalized);
        addSynonyms(terms, stripDiacritics(normalized));
        return terms.stream().toList();
    }

    public static String stripDiacritics(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        String replaced = normalized
                .replace('ı', 'i')
                .replace('İ', 'I')
                .replace('ğ', 'g')
                .replace('Ğ', 'G')
                .replace('ü', 'u')
                .replace('Ü', 'U')
                .replace('ş', 's')
                .replace('Ş', 'S')
                .replace('ö', 'o')
                .replace('Ö', 'O')
                .replace('ç', 'c')
                .replace('Ç', 'C');
        return Normalizer.normalize(replaced, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static void addSynonyms(Set<String> terms, String value) {
        if (value == null) {
            return;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "yoğurt", "yogurt" -> {
                addTerm(terms, "yoğurt");
                addTerm(terms, "yogurt");
                addTerm(terms, "yoghurt");
            }
            case "süt", "sut", "milk" -> {
                addTerm(terms, "süt");
                addTerm(terms, "sut");
                addTerm(terms, "milk");
            }
            case "tavuk göğsü", "tavuk gogsu", "chicken breast" -> {
                addTerm(terms, "tavuk göğsü");
                addTerm(terms, "tavuk gogsu");
                addTerm(terms, "chicken breast");
            }
            case "yumurta", "egg", "eggs" -> {
                addTerm(terms, "yumurta");
                addTerm(terms, "egg");
                addTerm(terms, "eggs");
            }
            case "peynir", "cheese" -> {
                addTerm(terms, "peynir");
                addTerm(terms, "cheese");
            }
            case "ekmek", "bread" -> {
                addTerm(terms, "ekmek");
                addTerm(terms, "bread");
            }
            case "pirinç", "pirinc", "rice" -> {
                addTerm(terms, "pirinç");
                addTerm(terms, "pirinc");
                addTerm(terms, "rice");
            }
            case "mercimek", "lentil", "lentils" -> {
                addTerm(terms, "mercimek");
                addTerm(terms, "lentil");
                addTerm(terms, "lentils");
            }
            default -> {
                if (lower.contains("tavuk") && lower.contains("gog")) {
                    addTerm(terms, "chicken breast");
                    addTerm(terms, "tavuk göğsü");
                }
            }
        }
    }

    private static void addTerm(Set<String> terms, String value) {
        String normalized = normalizeText(value);
        if (normalized != null) {
            terms.add(normalized);
        }
    }
}

package com.grun.calorietracker.service.support;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FoodProductNormalizationRules {

    private static final Set<String> SMALL_TITLE_WORDS = Set.of(
            "and", "or", "of", "in", "on", "with", "for", "to", "from", "a", "an", "the"
    );

    private static final Map<String, String> ACRONYM_REPLACEMENTS = new HashMap<>();

    static {
        ACRONYM_REPLACEMENTS.put("bbq", "BBQ");
        ACRONYM_REPLACEMENTS.put("uk", "UK");
        ACRONYM_REPLACEMENTS.put("eu", "EU");
        ACRONYM_REPLACEMENTS.put("usa", "USA");
        ACRONYM_REPLACEMENTS.put("m&s", "M&S");
        ACRONYM_REPLACEMENTS.put("m & s", "M&S");
        ACRONYM_REPLACEMENTS.put("b12", "B12");
        ACRONYM_REPLACEMENTS.put("bcaa", "BCAA");
        ACRONYM_REPLACEMENTS.put("dna", "DNA");
    }

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

    public static String normalizeProductDisplayName(String value) {
        return normalizeDisplayName(value, true);
    }

    public static String normalizeBrandDisplayName(String value) {
        return normalizeDisplayName(value, false);
    }

    private static String normalizeDisplayName(String value, boolean allowSmallTitleWords) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+([,.;:!?])", "$1")
                .trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (!shouldApplyTitleCase(normalized)) {
            return normalized;
        }

        return toTitleCase(normalized, allowSmallTitleWords);
    }

    private static boolean shouldApplyTitleCase(String value) {
        boolean hasLetter = false;
        boolean hasLower = false;
        boolean hasUpper = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetter(character)) {
                hasLetter = true;
                hasLower = hasLower || Character.isLowerCase(character);
                hasUpper = hasUpper || Character.isUpperCase(character);
            }
        }

        if (!hasLetter) {
            return false;
        }
        return !hasLower || !hasUpper;
    }

    private static String toTitleCase(String value, boolean allowSmallTitleWords) {
        String[] parts = value.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                result.append(' ');
            }
            String part = parts[index];
            if (part.isBlank()) {
                continue;
            }
            boolean isMiddleSmallWord = allowSmallTitleWords
                    && index > 0
                    && index < parts.length - 1
                    && SMALL_TITLE_WORDS.contains(part);
            result.append(isMiddleSmallWord ? part : capitalizeCompoundToken(part));
        }
        return result.toString();
    }

    private static String capitalizeCompoundToken(String token) {
        String acronym = ACRONYM_REPLACEMENTS.get(token);
        if (acronym != null) {
            return acronym;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (Character.isLetter(character)) {
                result.append(capitalizeNext ? Character.toTitleCase(character) : character);
                capitalizeNext = false;
            } else {
                result.append(character);
                capitalizeNext = character == '-' || character == '/' || character == '(' || character == '[' || character == '&';
            }
        }
        return result.toString();
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

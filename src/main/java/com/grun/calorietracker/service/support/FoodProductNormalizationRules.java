package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.PreferredLanguage;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FoodProductNormalizationRules {

    public static final int MAX_SEARCH_QUERY_CHARACTERS = 256;
    public static final int MAX_SEARCH_QUERY_WORDS = 24;

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
        return normalizeProductDisplayName(value, null);
    }

    public static String normalizeProductDisplayName(String value, PreferredLanguage language) {
        Locale locale = language == PreferredLanguage.TR ? Locale.forLanguageTag("tr") : Locale.ROOT;
        return normalizeDisplayName(value, true, locale);
    }

    public static String cleanDisplayText(String value) {
        if (value == null) return null;
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\s\\p{Zs}]+", " ").strip()
                .replaceAll("\\s+([,.;:!?])", "$1");
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static String normalizeBrandDisplayName(String value) {
        String cleaned = cleanDisplayText(value);
        String canonical = FoodBrandDisplayRules.canonicalName(cleaned);
        if (canonical != null) {
            return canonical;
        }
        // Unknown proper names cannot acquire an invented official spelling.
        return cleaned;
    }

    private static String normalizeDisplayName(String value, boolean allowSmallTitleWords, Locale locale) {
        String normalized = cleanDisplayText(value);
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

        return Normalizer.normalize(toTitleCase(normalized, allowSmallTitleWords, locale), Normalizer.Form.NFC);
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

    private static String toTitleCase(String value, boolean allowSmallTitleWords, Locale locale) {
        String[] parts = value.toLowerCase(locale).split(" ");
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
                    && (locale.getLanguage().equals("tr") ? Set.of("ve", "veya", "ile").contains(part) : SMALL_TITLE_WORDS.contains(part));
            result.append(isMiddleSmallWord ? part : capitalizeCompoundToken(part, locale));
        }
        return result.toString();
    }

    private static String capitalizeCompoundToken(String token, Locale locale) {
        String acronym = ACRONYM_REPLACEMENTS.get(token);
        if (acronym != null) {
            return acronym;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (Character.isLetter(character)) {
                result.append(capitalizeNext ? String.valueOf(character).toUpperCase(locale) : character);
                capitalizeNext = false;
            } else {
                result.append(character);
                capitalizeNext = character == '-' || character == '/' || character == '(' || character == '[' || character == '&';
            }
        }
        return result.toString();
    }


    public static String normalizeSearchAlias(String value) {
        String normalized = stripDiacritics(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
    public static List<String> expandSearchTerms(String value) {
        validateSearchQuery(value);
        String normalized = normalizeText(value);
        if (normalized == null) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        addTerm(terms, normalized);
        FoodBrandDisplayRules.searchAliases(normalized).forEach(alias -> addTerm(terms, alias));
        addTerm(terms, stripDiacritics(normalized));
        addSynonyms(terms, normalized);
        addSynonyms(terms, stripDiacritics(normalized));
        return terms.stream().toList();
    }

    public static List<Set<String>> searchTokenVariants(String value) {
        validateSearchQuery(value);
        String normalized = normalizeSearchAlias(value);
        String source = normalizeText(value);
        if (normalized == null || source == null) {
            return List.of();
        }
        String[] normalizedTokens = normalized.split("\\s+");
        String[] sourceTokens = source.toLowerCase(Locale.ROOT).split("\\s+");
        List<Set<String>> groups = new java.util.ArrayList<>();
        int count = Math.min(normalizedTokens.length, sourceTokens.length);
        for (int index = 0; index < count; index++) {
            // Preserve reviewed multiword brands as one condition, including short M & S tokens.
            int brandEnd = index;
            for (int end = count; end > index + 1; end--) {
                String phrase = String.join(" ", java.util.Arrays.copyOfRange(sourceTokens, index, end));
                if (!FoodBrandDisplayRules.searchAliases(phrase).isEmpty()) {
                    brandEnd = end;
                    break;
                }
            }
            if (brandEnd > index) {
                String phrase = String.join(" ", java.util.Arrays.copyOfRange(sourceTokens, index, brandEnd));
                Set<String> brandVariants = new LinkedHashSet<>();
                brandVariants.add(phrase);
                brandVariants.add(normalizeSearchAlias(phrase));
                FoodBrandDisplayRules.searchAliases(phrase).forEach(alias -> {
                    brandVariants.add(alias.toLowerCase(Locale.ROOT));
                    brandVariants.add(normalizeSearchAlias(alias));
                });
                groups.add(Set.copyOf(brandVariants));
                index = brandEnd - 1;
                continue;
            }
            Set<String> variants = new LinkedHashSet<>();
            if (sourceTokens[index].length() >= 2) {
                variants.add(sourceTokens[index]);
            }
            if (normalizedTokens[index].length() >= 2) {
                variants.add(normalizedTokens[index]);
            }
            if (!variants.isEmpty()) {
                groups.add(Set.copyOf(variants));
            }
        }
        return List.copyOf(groups);
    }

    public static void validateSearchQuery(String value) {
        if (value == null) return;
        if (value.length() > MAX_SEARCH_QUERY_CHARACTERS) {
            throw new IllegalArgumentException("Search text must not exceed 256 characters.");
        }
        String normalized = normalizeText(value);
        if (normalized != null && normalized.split("\\s+").length > MAX_SEARCH_QUERY_WORDS) {
            throw new IllegalArgumentException("Search text must not exceed 24 words.");
        }
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
            // Only whole-phrase synonyms are safe: a broader alias must not drop preparation/composition.
            default -> { }
        }
    }

    private static void addTerm(Set<String> terms, String value) {
        String normalized = normalizeText(value);
        if (normalized != null) {
            terms.add(normalized);
        }
    }
}

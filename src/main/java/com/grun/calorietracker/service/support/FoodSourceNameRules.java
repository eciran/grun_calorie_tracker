package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.PreferredLanguage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Exact source-backed exceptions; never fuzzy translation or product identity. */
public final class FoodSourceNameRules {
    public static final String VERSION = "catalog-naming-r7-v1";
    private static final Map<String, Decision> DECISIONS = load();
    private FoodSourceNameRules() { }

    public record Decision(String sourceKey, String sourceDescription, FoodPreparationState targetState,
                           String displayNameEn, String displayNameTr, String stateRationale, boolean servingReviewRequired) {
        public String displayName(PreferredLanguage language) {
            return language == PreferredLanguage.TR ? displayNameTr : displayNameEn;
        }
    }
    private record Registry(String version, String source, java.util.List<Decision> entries) { }

    public static Optional<Decision> match(String sourceKey, String description, String sourceDataType) {
        if (!"SR Legacy".equals(sourceDataType) || sourceKey == null) return Optional.empty();
        String key = sourceKey.replaceFirst("^USDA:fdc:", "USDA_FOODDATA:fdc:");
        Decision decision = DECISIONS.get(key);
        return decision != null && normalized(decision.sourceDescription()).equals(normalized(description))
                ? Optional.of(decision) : Optional.empty();
    }

    public static Optional<Decision> reviewedSource(String sourceKey) {
        return sourceKey == null ? Optional.empty() : Optional.ofNullable(DECISIONS.get(
                sourceKey.replaceFirst("^USDA:fdc:", "USDA_FOODDATA:fdc:")));
    }

    public static int size() { return DECISIONS.size(); }

    private static String normalized(String value) {
        String cleaned = FoodProductNormalizationRules.cleanDisplayText(value);
        return cleaned == null ? "" : cleaned.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Decision> load() {
        var result = new LinkedHashMap<String, Decision>();
        loadRegistry("catalog-naming-r4-v1", "/catalog/source-name-decisions-r4-v1.json", result);
        loadRegistry("catalog-naming-r6-v1", "/catalog/source-name-decisions-r6-v1.json", result);
        loadRegistry(VERSION, "/catalog/source-name-decisions-r7-v1.json", result);
        return Map.copyOf(result);
    }

    private static void loadRegistry(String version, String resource, Map<String, Decision> result) {
        try (var input = FoodSourceNameRules.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing reviewed source-name decisions");
            Registry registry = new ObjectMapper().readValue(input, Registry.class);
            if (!version.equals(registry.version()) || registry.entries() == null) throw new IllegalStateException("Invalid naming version");
            for (Decision decision : registry.entries()) {
                if (decision.sourceKey() == null || !decision.sourceKey().matches("USDA_FOODDATA:fdc:[1-9][0-9]{0,17}")
                        || normalized(decision.sourceDescription()).isEmpty() || decision.targetState() == null
                        || normalized(decision.displayNameEn()).isEmpty() || normalized(decision.displayNameTr()).isEmpty()
                        || decision.displayNameEn().length() > 255 || decision.displayNameTr().length() > 255
                        || decision.stateRationale() == null || decision.stateRationale().isBlank()
                        || result.putIfAbsent(decision.sourceKey(), decision) != null) {
                    throw new IllegalStateException("Invalid or duplicate source-name decision");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load reviewed source-name decisions", exception);
        }
    }
}

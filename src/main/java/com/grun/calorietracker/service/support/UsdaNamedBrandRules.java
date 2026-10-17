package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Named manufacturers in a non-retail USDA subtype need exact reviewed scope. */
public final class UsdaNamedBrandRules {
    private UsdaNamedBrandRules() { }
    public record Scope(String sourceKey, String sourceDataType, String sourceDescription,
                        List<String> acceptedDisplayNames, String canonicalBrand,
                        String sourceUrl, String manufacturerUrl) { }
    private record Registry(String version, List<Scope> entries) { }
    private static final Map<String, Scope> SCOPES = load();

    public static Optional<Scope> match(String key, String description, String subtype) {
        Scope scope = key == null ? null : SCOPES.get(key.replaceFirst("^USDA:fdc:", "USDA_FOODDATA:fdc:"));
        if (scope == null) return Optional.empty();
        String normalized = description == null ? "" : description.trim().toLowerCase(Locale.ROOT);
        boolean accepted = scope.sourceDescription().toLowerCase(Locale.ROOT).equals(normalized)
                || scope.acceptedDisplayNames().stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(normalized));
        if (!scope.sourceDataType().equals(UsdaCatalogClassificationRules.normalizeDataType(subtype)) || !accepted) {
            throw new IllegalArgumentException("Reviewed USDA named-brand source subtype/description changed; re-review required.");
        }
        return Optional.of(scope);
    }

    private static Map<String, Scope> load() {
        try (var input = UsdaNamedBrandRules.class.getResourceAsStream("/catalog/usda-named-brand-scopes-r6-v1.json")) {
            if (input == null) throw new IllegalStateException("Missing USDA named-brand scope registry");
            Registry registry = new ObjectMapper().readValue(input, Registry.class);
            if (!"usda-named-brand-scopes-r6-v1".equals(registry.version()) || registry.entries() == null) {
                throw new IllegalStateException("Invalid named-brand registry");
            }
            for (Scope scope : registry.entries()) {
                if (scope.sourceKey() == null || !scope.sourceKey().matches("USDA_FOODDATA:fdc:[1-9][0-9]{0,17}")
                        || scope.sourceDescription() == null || scope.sourceDescription().isBlank()
                        || scope.canonicalBrand() == null || scope.canonicalBrand().isBlank()
                        || scope.acceptedDisplayNames() == null || scope.acceptedDisplayNames().stream().anyMatch(String::isBlank)
                        || !"SR Legacy".equals(scope.sourceDataType()) || !scope.sourceUrl().startsWith("https://fdc.nal.usda.gov/")) {
                    throw new IllegalStateException("Invalid reviewed named-brand scope");
                }
            }
            return registry.entries().stream().collect(Collectors.toUnmodifiableMap(Scope::sourceKey, s -> s));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load named-brand registry", exception);
        }
    }
}

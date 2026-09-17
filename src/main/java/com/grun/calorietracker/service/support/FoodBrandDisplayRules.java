package com.grun.calorietracker.service.support;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.stream.Collectors;

/** Reviewed display aliases only. Never use a brand alias as product identity. */
public final class FoodBrandDisplayRules {
    private static final Map<String, String> ALIASES = loadAliases();

    private FoodBrandDisplayRules() { }

    public static String canonicalName(String value) {
        if (value == null) return null;
        String direct = ALIASES.get(key(value));
        if (direct != null || !value.contains(",")) return direct;
        // All components must be reviewed. Unknown business punctuation is not a brand list.
        var names = new LinkedHashSet<String>();
        for (String part : value.split(",", -1)) {
            String canonical = ALIASES.get(key(part));
            if (canonical == null) return null;
            names.add(canonical);
        }
        return String.join(", ", names);
    }

    public static List<String> searchAliases(String value) {
        String canonical = value == null ? null : ALIASES.get(key(value));
        if (canonical == null) return List.of();
        var names = new LinkedHashSet<String>();
        names.add(canonical);
        ALIASES.entrySet().stream().filter(entry -> entry.getValue().equals(canonical))
                .map(Map.Entry::getKey).sorted().forEach(names::add);
        return List.copyOf(names);
    }

    private static String key(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\s\\p{Zs}]+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> loadAliases() {
        Properties properties = new Properties();
        try (InputStream input = FoodBrandDisplayRules.class.getResourceAsStream(
                "/catalog/brand-display-aliases.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing reviewed brand display aliases");
            }
            properties.load(input);
            Map<String, String> aliases = properties.stringPropertyNames().stream()
                    .collect(Collectors.toUnmodifiableMap(FoodBrandDisplayRules::key,
                            properties::getProperty));
            for (String canonical : aliases.values()) {
                if (canonical.isBlank() || !canonical.equals(aliases.get(key(canonical)))) {
                    throw new IllegalStateException("Brand aliases must have stable canonical names");
                }
            }
            return aliases;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load reviewed brand display aliases", exception);
        }
    }
}

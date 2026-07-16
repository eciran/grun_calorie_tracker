package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.RecipeAllergen;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class RecipeAllergenResolver {

    private RecipeAllergenResolver() {
    }

    public static Set<RecipeAllergen> resolve(String rawAllergens) {
        LinkedHashSet<RecipeAllergen> allergens = new LinkedHashSet<>();
        if (rawAllergens == null || rawAllergens.isBlank()) {
            return allergens;
        }
        String[] tokens = rawAllergens.split("[,;|]");
        for (String token : tokens) {
            RecipeAllergen allergen = mapToken(token);
            if (allergen != null) {
                allergens.add(allergen);
            }
        }
        String normalizedText = normalize(rawAllergens);
        if (normalizedText != null) {
            detectMentions(" " + normalizedText + " ", allergens);
        }
        return allergens;
    }

    private static void detectMentions(String text, Set<RecipeAllergen> allergens) {
        mention(text, allergens, RecipeAllergen.MILK,
                "milk", "dairy", "lactose", "casein", "whey", "sut", "yogurt", "yoghurt", "cheese");
        mention(text, allergens, RecipeAllergen.EGGS, "egg", "eggs", "yumurta");
        mention(text, allergens, RecipeAllergen.FISH, "fish", "balik", "salmon", "tuna", "cod");
        mention(text, allergens, RecipeAllergen.CRUSTACEAN_SHELLFISH,
                "shellfish", "shrimp", "prawn", "crab", "lobster");
        mention(text, allergens, RecipeAllergen.TREE_NUTS,
                "tree nuts", "almond", "hazelnut", "walnut", "cashew", "pecan", "pistachio",
                "brazil nut", "macadamia", "findik", "ceviz", "badem");
        mention(text, allergens, RecipeAllergen.PEANUTS, "peanut", "peanuts", "yer fistigi");
        mention(text, allergens, RecipeAllergen.WHEAT, "wheat", "bugday");
        mention(text, allergens, RecipeAllergen.SOYBEANS, "soy", "soya", "soybean", "soybeans");
        mention(text, allergens, RecipeAllergen.SESAME, "sesame", "susam", "tahini");
        mention(text, allergens, RecipeAllergen.GLUTEN, "gluten");
        mention(text, allergens, RecipeAllergen.CELERY, "celery", "kereviz");
        mention(text, allergens, RecipeAllergen.MUSTARD, "mustard", "hardal");
        mention(text, allergens, RecipeAllergen.LUPIN, "lupin");
        mention(text, allergens, RecipeAllergen.MOLLUSCS,
                "mollusc", "molluscs", "clam", "mussel", "oyster");
        mention(text, allergens, RecipeAllergen.SULPHITES,
                "sulphite", "sulphites", "sulfite", "sulfites",
                "sulphur dioxide", "sulfur dioxide");
    }

    private static void mention(String text, Set<RecipeAllergen> allergens,
                                RecipeAllergen allergen, String... terms) {
        for (String term : terms) {
            if (text.contains(" " + term + " ")) {
                allergens.add(allergen);
                return;
            }
        }
    }

    private static RecipeAllergen mapToken(String token) {
        String normalized = normalize(token);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "milk", "dairy", "lactose", "casein", "whey", "sut", "sut urunleri" -> RecipeAllergen.MILK;
            case "egg", "eggs", "yumurta" -> RecipeAllergen.EGGS;
            case "fish", "balik" -> RecipeAllergen.FISH;
            case "crustaceans", "crustacean shellfish", "shellfish", "shrimp", "prawn", "crab", "lobster" -> RecipeAllergen.CRUSTACEAN_SHELLFISH;
            case "nuts", "tree nuts", "almond", "hazelnut", "walnut", "cashew", "pecan", "pistachio", "brazil nut", "macadamia", "findik", "ceviz", "badem" -> RecipeAllergen.TREE_NUTS;
            case "peanut", "peanuts", "yer fistigi" -> RecipeAllergen.PEANUTS;
            case "wheat", "bugday" -> RecipeAllergen.WHEAT;
            case "soy", "soya", "soybeans", "soya beans" -> RecipeAllergen.SOYBEANS;
            case "sesame", "susam" -> RecipeAllergen.SESAME;
            case "gluten" -> RecipeAllergen.GLUTEN;
            case "celery", "kereviz" -> RecipeAllergen.CELERY;
            case "mustard", "hardal" -> RecipeAllergen.MUSTARD;
            case "lupin" -> RecipeAllergen.LUPIN;
            case "molluscs", "mollusk", "mollusks", "clam", "mussel", "oyster" -> RecipeAllergen.MOLLUSCS;
            case "sulphites", "sulfites", "sulphur dioxide", "sulfur dioxide" -> RecipeAllergen.SULPHITES;
            default -> null;
        };
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return null;
        }
        int colon = text.indexOf(':');
        if (colon >= 0 && colon < text.length() - 1) {
            text = text.substring(colon + 1);
        }
        text = text.replace('-', ' ').replace('_', ' ');
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        text = text.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return text.isBlank() ? null : text;
    }
}

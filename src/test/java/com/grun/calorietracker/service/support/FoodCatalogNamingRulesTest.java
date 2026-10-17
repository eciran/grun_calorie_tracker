package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FoodCatalogNamingRulesTest {
    private FoodItemEntity product() {
        var product = new FoodItemEntity();
        product.setName("Milk");
        product.setDisplayName("Milk");
        product.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        return product;
    }
    private FoodItemLocalizationEntity localization(PreferredLanguage language, String name) {
        var localization = new FoodItemLocalizationEntity();
        localization.setLanguage(language);
        localization.setDisplayName(name);
        return localization;
    }

    @Test
    void missingTranslationIsNotSilentlyInvented() {
        var findings = FoodCatalogNamingRules.findings(product(), List.of(localization(PreferredLanguage.EN, "Milk")));
        assertTrue(findings.contains("MISSING_TR_LOCALIZATION"));
    }
    @Test
    void turkishDiacriticsAreNotRepairedByGuessing() {
        var tr = localization(PreferredLanguage.TR, "Cig Tavuk Gogsu");
        assertTrue(FoodCatalogNamingRules.findings(product(), List.of(tr)).contains("TR_DIACRITICS_REVIEW"));
        assertEquals("Cig Tavuk Gogsu", tr.getDisplayName());
    }
    @Test
    void brandedProperNamesAreNotRequiredToBeTranslated() {
        var product = product();
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        assertTrue(FoodCatalogNamingRules.findings(product, List.of()).isEmpty());
    }
    @Test
    void englishFlavorDescriptorInTurkishBrandedLocaleRequiresReviewNotTranslation() {
        var product = product();
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setBrand("Activia");
        var tr = localization(PreferredLanguage.TR, "Probiotic Shot, Banana and Strawberry");
        assertTrue(FoodCatalogNamingRules.findings(product, List.of(tr)).contains("ENGLISH_DESCRIPTOR_IN_TR_NAME"));
        assertEquals("Probiotic Shot, Banana and Strawberry", tr.getDisplayName());
        assertFalse(FoodCatalogNamingRules.findings(product,
                List.of(localization(PreferredLanguage.TR, "Dairy Milk"))).contains("ENGLISH_DESCRIPTOR_IN_TR_NAME"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "170380|Broccoli, frozen, spears, cooked, boiled, drained, without salt|Dallar|Tuzsuz",
            "169969|Broccoli, frozen, chopped, cooked, boiled, drained, without salt|Do\u011franm\u0131\u015f|Tuzsuz",
            "168510|Broccoli, cooked, boiled, drained, with salt|S\u00fcz\u00fclm\u00fc\u015f|Tuzlu"
    })
    void reviewedBroccoliNamesKeepSourceQualifiersAndExactSubtype(
            String id, String description, String part, String salt) {
        var decision = FoodSourceNameRules.match("USDA_FOODDATA:fdc:" + id, description, "SR Legacy").orElseThrow();
        assertEquals(FoodPreparationState.BOILED, decision.targetState());
        assertTrue(decision.displayNameTr().contains(part));
        assertTrue(decision.displayNameTr().contains(salt));
        assertTrue(decision.displayNameTr().contains("S\u00fcz\u00fclm\u00fc\u015f"));
        if (!id.equals("168510")) assertTrue(decision.displayNameTr().contains("Dondurulmu\u015f"));
        assertTrue(FoodSourceNameRules.match("USDA_FOODDATA:fdc:" + id, "Broccoli, raw", "SR Legacy").isEmpty());
        assertTrue(FoodSourceNameRules.match("USDA_FOODDATA:fdc:" + id, description, "Branded").isEmpty());
    }
    @Test
    void reviewedBrandAliasesAreFindingsNotSkuMerges() {
        var product = product();
        product.setBrand("Vit-Hit");
        assertTrue(FoodCatalogNamingRules.findings(product, List.of()).contains("REVIEWED_BRAND_DISPLAY_VARIANT"));
        assertEquals("Vit-Hit", product.getBrand());
    }
    @Test
    void unicodeControlsHtmlAndBrokenParenthesesRequireReview() {
        var product = product();
        product.setDisplayName("<b>Milk</b> (\u202e");
        var findings = FoodCatalogNamingRules.findings(product, List.of());
        assertTrue(findings.contains("DISPLAY_HTML_REVIEW"));
        assertTrue(findings.contains("DISPLAY_CONTROL_OR_ENCODING_REVIEW"));
        assertTrue(findings.contains("DISPLAY_PARENTHESES_REVIEW"));
    }
    @Test
    void sourceKnownDryRiceIsNeverCertifiedAsCooked() {
        var product = product();
        product.setSourceKey("USDA_FOODDATA:fdc:168876");
        product.setSourceDataType("SR Legacy");
        product.setDataSource(FoodDataSource.USDA_FOODDATA);
        product.setPreparationState(FoodPreparationState.BOILED);
        assertTrue(FoodCatalogNamingRules.findings(product, List.of()).contains("SOURCE_NAME_EXCEPTION_PENDING"));
        assertEquals(FoodPreparationState.BOILED, product.getPreparationState());
    }
    @Test
    void registryMatchesExactSourceNotOnlyIdOrSimilarName() {
        assertEquals(20, FoodSourceNameRules.size());
        var decision = FoodSourceNameRules.match("USDA_FOODDATA:fdc:168876", "Rice, brown, parboiled, dry, UNCLE BEN'S", "SR Legacy").orElseThrow();
        assertEquals(FoodPreparationState.UNSPECIFIED, decision.targetState());
        assertTrue(decision.displayNameTr().contains("Kuru"));
        assertTrue(FoodSourceNameRules.match("USDA_FOODDATA:fdc:168876", "Cooked Brown Rice", "SR Legacy").isEmpty());
        assertTrue(FoodSourceNameRules.match("USDA_FOODDATA:fdc:168876", decision.sourceDescription(), "Branded").isEmpty());
        assertTrue(FoodCatalogNamingRules.sourcePreparationConflict("Broccoli, raw", FoodPreparationState.COOKED));
        assertFalse(FoodCatalogNamingRules.sourcePreparationConflict("Raw sugar", FoodPreparationState.PREPARED));
        assertFalse(FoodCatalogNamingRules.sourcePreparationConflict("Broccoli, raw", FoodPreparationState.UNSPECIFIED));
        assertFalse(FoodCatalogNamingRules.sourcePreparationConflict("Broccoli, cooked, boiled", FoodPreparationState.COOKED));
    }

    @Test
    void preparedFrozenEdamameDoesNotInventABoilingMethod() {
        var decision = FoodSourceNameRules.match("USDA_FOODDATA:fdc:168411", "Edamame, frozen, prepared", "SR Legacy").orElseThrow();
        assertEquals(FoodPreparationState.PREPARED, decision.targetState());
        assertEquals("Prepared Edamame (From Frozen)", decision.displayNameEn());
        assertEquals("Dondurulmuştan Hazırlanmış Edamame", decision.displayNameTr());
        assertEquals("GLOBAL:GENERIC_INGREDIENT:PREPARED:edamame_from_frozen",
                FoodProductCanonicalKeyRules.resolve(FoodCatalogType.GENERIC_INGREDIENT,
                        MarketRegion.GLOBAL, decision.targetState(), decision.displayNameEn()));
        assertTrue(FoodSourceNameRules.match("USDA_FOODDATA:fdc:168411", "Edamame, frozen, raw", "SR Legacy").isEmpty());
        assertTrue(FoodSourceNameRules.match("USDA_FOODDATA:fdc:168411", "Edamame, frozen, prepared", "Foundation").isEmpty());
        assertTrue(FoodSourceNameRules.match("USDA_FOODDATA:fdc:168410", "Edamame, frozen, prepared", "SR Legacy").isEmpty());
    }

    @Test
    void unknownBrandCaseIsReviewNotAnInventedCanonicalName() {
        var product = product();
        product.setBrand("acme foods");
        assertTrue(FoodCatalogNamingRules.findings(product, List.of()).contains("UNREVIEWED_BRAND_CASING"));
        assertEquals("acme foods", product.getBrand());
        var canonical = new FoodBrandEntity();
        canonical.setVerified(true);
        canonical.setCanonicalName("acme foods");
        product.setCanonicalBrand(canonical);
        assertFalse(FoodCatalogNamingRules.findings(product, List.of()).contains("UNREVIEWED_BRAND_CASING"));
    }
}

package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.FoodCatalogType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UsdaCatalogClassificationRulesTest {
    @Test
    void namedManufacturerIsReviewedSeparatelyFromSourceSubtypeAndBarcode() {
        String key = "USDA_FOODDATA:fdc:170904";
        String name = "Kefir, lowfat, plain, LIFEWAY";
        assertEquals(FoodCatalogType.BRANDED_PRODUCT,
                UsdaCatalogClassificationRules.resolve("SR Legacy", null, false, key, name));
        assertEquals(FoodCatalogType.BRANDED_PRODUCT,
                UsdaCatalogClassificationRules.resolve("SR Legacy", FoodCatalogType.BRANDED_PRODUCT, false, key, "Lifeway Plain Low-Fat Kefir"));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("SR Legacy", FoodCatalogType.GENERIC_INGREDIENT, false, key, name));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("SR Legacy", null, true, key, name));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("Branded", null, false, key, name));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("SR Legacy", null, false, "USDA:fdc:170904", name));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("SR Legacy", null, false, key, "Kefir, lowfat, strawberry, LIFEWAY"));
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT,
                UsdaCatalogClassificationRules.resolve("SR Legacy", null, false, "USDA_FOODDATA:fdc:123", name));
    }
    @Test
    void providerRecordAndFdcIdMustAgree() {
        assertDoesNotThrow(() -> UsdaCatalogClassificationRules.validateSourceIdentity("123", "USDA_FOODDATA:fdc:123"));
        assertDoesNotThrow(() -> UsdaCatalogClassificationRules.validateSourceIdentity("123", "USDA:fdc:123"));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.validateSourceIdentity("123", "USDA_FOODDATA:fdc:124"));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.validateSourceIdentity("-1", null));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.validateSourceIdentity(null, "USDA_FOODDATA:fdc:abc"));
    }
    @Test
    void subtypeDistinguishesGenericFromRetailProducts() {
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, UsdaCatalogClassificationRules.resolve("Foundation", null, false));
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, UsdaCatalogClassificationRules.resolve("SR Legacy", null, false));
        assertEquals(FoodCatalogType.BRANDED_PRODUCT, UsdaCatalogClassificationRules.resolve("Branded", null, false));
        assertEquals(FoodCatalogType.BRANDED_PRODUCT, UsdaCatalogClassificationRules.resolve("Branded", null, true));
    }

    @Test
    void conflictingOrUnsupportedSourceMetadataFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("Branded", FoodCatalogType.GENERIC_INGREDIENT, false));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("Foundation", null, true));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("SR Legacy", FoodCatalogType.BRANDED_PRODUCT, false));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("Unknown", null, false));
    }

    @Test
    void SurveyAndExperimentalRequireExplicitClassification() {
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("Survey (FNDDS)", null, false));
        assertThrows(IllegalArgumentException.class, () -> UsdaCatalogClassificationRules.resolve("Experimental", null, false));
        assertEquals(FoodCatalogType.STANDARD_PREPARED_ITEM,
                UsdaCatalogClassificationRules.resolve("FNDDS", FoodCatalogType.STANDARD_PREPARED_ITEM, false));
    }
}

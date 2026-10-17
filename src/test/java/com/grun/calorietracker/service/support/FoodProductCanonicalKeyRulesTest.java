package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MarketRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodProductCanonicalKeyRulesTest {

    @Test
    void sourceVerifiedIdentityRepairsRemainCompatibleWithSchema305LegacyKeys() {
        assertEquals("GLOBAL:GENERIC_INGREDIENT:PREPARED:reduced_fat_mayonnaise_with_olive_oil",
                FoodProductCanonicalKeyRules.resolve(FoodCatalogType.GENERIC_INGREDIENT,
                        MarketRegion.GLOBAL, FoodPreparationState.PREPARED,
                        "Reduced-Fat Mayonnaise with Olive Oil"));
        assertEquals("GLOBAL:GENERIC_INGREDIENT:ROASTED:turkey_light_meat_all_classes",
                FoodProductCanonicalKeyRules.resolve(FoodCatalogType.GENERIC_INGREDIENT,
                        MarketRegion.GLOBAL, FoodPreparationState.ROASTED,
                        "Roasted Turkey Light Meat (All Classes)"));
    }

    @Test
    void reviewedConceptSurvivesRenameAndMarketChangeButNotStateOrCompositionChange() {
        FoodItemEntity product = new FoodItemEntity();
        product.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        product.setPreparationState(FoodPreparationState.RAW);
        product.setFoodConceptKey("banana");
        product.setCompositionVariantKey("edible_flesh");
        product.setDisplayName("Raw Banana");
        product.setMarketRegion(MarketRegion.UK_IE);
        String key = FoodProductCanonicalKeyRules.resolve(product);
        assertEquals("C2:GENERIC_INGREDIENT:RAW:banana:edible_flesh", key);
        product.setDisplayName("Muz");
        product.setMarketRegion(MarketRegion.TR);
        assertEquals(key, FoodProductCanonicalKeyRules.resolve(product));
        product.setPreparationState(FoodPreparationState.COOKED);
        assertNotEquals(key, FoodProductCanonicalKeyRules.resolve(product));
        product.setPreparationState(FoodPreparationState.RAW);
        product.setCompositionVariantKey("whole_with_skin");
        assertNotEquals(key, FoodProductCanonicalKeyRules.resolve(product));
    }

    @Test
    void preparedConceptRequiresExplicitStateAndVariantAndCannotBeBranded() {
        FoodItemEntity product = new FoodItemEntity();
        product.setFoodConceptKey("latte");
        product.setCatalogType(FoodCatalogType.STANDARD_PREPARED_ITEM);
        product.setPreparationState(FoodPreparationState.PREPARED);
        assertThrows(IllegalArgumentException.class, () -> FoodProductCanonicalKeyRules.resolve(product));
        product.setCompositionVariantKey("whole_milk_no_sugar");
        assertEquals("C2:STANDARD_PREPARED_ITEM:PREPARED:latte:whole_milk_no_sugar", FoodProductCanonicalKeyRules.resolve(product));
        product.setPreparationState(FoodPreparationState.UNSPECIFIED);
        assertThrows(IllegalArgumentException.class, () -> FoodProductCanonicalKeyRules.resolve(product));
        product.setPreparationState(FoodPreparationState.PREPARED);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        assertThrows(IllegalArgumentException.class, () -> FoodProductCanonicalKeyRules.resolve(product));
    }

    @Test
    void conceptIdsAreValidatedNotLossilySlugified() {
        for (String invalid : java.util.List.of("Muz", "a b", "a:b", "muz/banana", "a".repeat(81))) {
            assertThrows(IllegalArgumentException.class, () -> FoodProductCanonicalKeyRules.validateIdentityToken(invalid));
        }
    }

    @Test
    void genericIdentityRemainsBackwardCompatible() {
        assertEquals(
                "GLOBAL:GENERIC_INGREDIENT:RAW:banana",
                FoodProductCanonicalKeyRules.resolve(
                        FoodCatalogType.GENERIC_INGREDIENT,
                        MarketRegion.GLOBAL,
                        FoodPreparationState.RAW,
                        "Bananas raw"
                )
        );
    }

    @Test
    void localDishFamilyAndVariantCreateStableIndependentIdentity() {
        assertEquals(
                "TR:LOCAL_DISH:PREPARED:kuru_fasulye:pastirmali",
                FoodProductCanonicalKeyRules.resolve(
                        FoodCatalogType.LOCAL_DISH,
                        MarketRegion.TR,
                        FoodPreparationState.PREPARED,
                        "Pastırmalı Kuru Fasulye",
                        "kuru-fasulye",
                        "pastırmalı"
                )
        );
    }

    @Test
    void localDishWithoutFamilyFallsBackToNormalizedDisplayIdentity() {
        assertEquals(
                "TR:LOCAL_DISH:PREPARED:mercimek_corbasi",
                FoodProductCanonicalKeyRules.resolve(
                        FoodCatalogType.LOCAL_DISH,
                        MarketRegion.TR,
                        FoodPreparationState.PREPARED,
                        "Mercimek Çorbası"
                )
        );
    }

    @Test
    void entityResolverUsesDishFamilyAndVariantButNeverKeysBrandedProducts() {
        FoodItemEntity dish = new FoodItemEntity();
        dish.setCatalogType(FoodCatalogType.LOCAL_DISH);
        dish.setMarketRegion(MarketRegion.TR);
        dish.setPreparationState(FoodPreparationState.PREPARED);
        dish.setDisplayName("Etli Kuru Fasulye");
        dish.setDishFamilyKey("kuru_fasulye");
        dish.setDishVariantKey("etli");
        assertEquals("TR:LOCAL_DISH:PREPARED:kuru_fasulye:etli", FoodProductCanonicalKeyRules.resolve(dish));

        dish.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        assertNull(FoodProductCanonicalKeyRules.resolve(dish));
    }
}

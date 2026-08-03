package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MarketRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FoodProductCanonicalKeyRulesTest {

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

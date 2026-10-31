package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.FoodPortionUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodPackageMeasureRulesTest {
    @Test
    void parsesSingleAndMultipackMassAndVolume() {
        assertEquals(measure("400.000", FoodPortionUnit.GRAM), FoodPackageMeasureRules.resolve(null, null, "400 gr"));
        assertEquals(measure("1500.000", FoodPortionUnit.MILLILITER), FoodPackageMeasureRules.resolve(null, null, "6 x 250 ml"));
        assertEquals(measure("1500.000", FoodPortionUnit.MILLILITER), FoodPackageMeasureRules.resolve("1500", "ml", "6 x 250 ml"));
    }

    @Test
    void preservesMissingMetadataAndRejectsPartialOrConflictingEvidence() {
        assertNull(FoodPackageMeasureRules.resolve(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FoodPackageMeasureRules.resolve("500", null, null));
        assertThrows(IllegalArgumentException.class, () -> FoodPackageMeasureRules.resolve("500", "g", "750 g"));
        assertThrows(IllegalArgumentException.class, () -> FoodPackageMeasureRules.resolve("500", "piece", null));
    }

    @Test
    void ignoresAmbiguousOptionalSourceFieldsButUsesSourceQuantityText() {
        assertNull(FoodPackageMeasureRules.resolveSource("500", null, null));
        assertNull(FoodPackageMeasureRules.resolveSource(null, "g", null));
        assertEquals(
                measure("500.000", FoodPortionUnit.GRAM),
                FoodPackageMeasureRules.resolveSource("500", null, "500 g")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodPackageMeasureRules.resolveSource("750", "g", "500 g")
        );
    }

    private FoodPackageMeasureRules.PackageMeasure measure(String quantity, FoodPortionUnit unit) {
        return new FoodPackageMeasureRules.PackageMeasure(new BigDecimal(quantity), unit);
    }
}

package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class TypedSourceObservationContractTest {
    final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    final TypedSourceObservationContract contract = new TypedSourceObservationContract(json);
    public static final String USDA = """
            {"fdcId":172878,"dataType":"SR Legacy","description":"Complete test food",
             "foodNutrients":[{"nutrient":{"id":1109,"number":"323","name":"Vitamin E (alpha-tocopherol)","unitName":"MG"},"amount":0.12}]}
            """;
    public static final String OFF = """
            {"code":"00032056","nutrition_data_per":"100g",
             "nutriments":{"sodium_100g":0.005,"sodium_unit":"g","sodium":0.005,"sodium_value":0.005}}
            """;
    TypedSourceObservationContract.Fact read(String provider, String field, String body) throws Exception {
        return contract.read(TypedSourceObservationContract.VERSION, provider, field, json.readTree(body));
    }
    @Test void exactUsdaFieldsKeepSeparateSemanticsAndCanonicalUnits() throws Exception {
        var e = read("USDA_FOODDATA", "vitamin_e", USDA);
        assertEquals(new BigDecimal("0.12"), e.canonicalAmount()); assertEquals("MG", e.canonicalUnit());
        var k = read("USDA_FOODDATA", "potassium", USDA.replace("1109", "1092").replace("323", "306")
                .replace("Vitamin E (alpha-tocopherol)", "Potassium, K"));
        assertEquals(1092, k.nutrientId()); assertEquals("POTASSIUM", k.semantic());
        for (String unit : List.of("UG", "\u00b5g", "\u03bcg")) {
            var b = read("USDA_FOODDATA", "vitamin_b12", USDA.replace("1109", "1178").replace("323", "418")
                    .replace("Vitamin E (alpha-tocopherol)", "Vitamin B-12").replace("MG", unit));
            assertEquals("ug", b.sourceUnit()); assertEquals("MCG", b.canonicalUnit());
            assertEquals(new BigDecimal("0.12"), b.canonicalAmount());
        }
    }
    @Test void invalidUsdaFormsIdsUnitsTypesAndAmountsNeverFallback() {
        for (String body : List.of(USDA.replace("1109", "1104"), USDA.replace("1109", "1109.0"),
                USDA.replace("323", "324"), USDA.replace("MG", "IU"), USDA.replace("alpha-tocopherol", "total"),
                USDA.replace("SR Legacy", "Branded"), USDA.replace("172878", "172878.0"),
                USDA.replace("0.12", "true"), USDA.replace("0.12", "null"), USDA.replace("0.12", "-1"),
                USDA.replace("0.12", "1e101"), USDA.replace("0.12", "1e-101"),
                USDA.replace("\"foodNutrients\":[", "\"foodNutrients\":[{\"nutrient\":{\"id\":1109}},"))) {
            assertThrows(IllegalArgumentException.class, () -> read("USDA_FOODDATA", "vitamin_e", body), body);
        }
    }
    @Test void sodiumConvertsOnlySourceGramsAndNeverChangesNativeBasis() throws Exception {
        for (String per : List.of("100g", "100ml")) {
            var fact = read("OPEN_FOOD_FACTS", "sodium", OFF.replace("\"100g\"", "\"" + per + "\""));
            assertNull(fact.nutrientId()); assertEquals("sodium_100g", fact.sourceField());
            assertEquals(new BigDecimal("0.005"), fact.declaredAmount());
            assertEquals(0, new BigDecimal("5").compareTo(fact.canonicalAmount()));
            assertEquals(per.equals("100g") ? "PER_100G" : "PER_100ML", fact.basis());
        }
    }
    @Test void missingFiberIsNotZeroButAnExplicitReportedZeroIsAllowed() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> read("OPEN_FOOD_FACTS", "fiber", OFF));
        var fiber = read("OPEN_FOOD_FACTS", "fiber", OFF.replace("sodium", "fiber").replace("0.005", "0"));
        assertEquals(BigDecimal.ZERO, fiber.declaredAmount()); assertEquals("G", fiber.canonicalUnit());
    }
    @Test void offUnitsModifiersConflictsAndNonNativeReferencesAreRefused() {
        for (String body : List.of(OFF.replace("\"100g\"", "\"serving\""), OFF.replace("\"g\"", "\"mg\""),
                OFF.replace("00032056", "00032057"), OFF.replace("\"00032056\"", "32056"),
                OFF.replace("\"sodium_unit\"", "\"sodium_modifier\":\"<\",\"sodium_unit\""),
                OFF.replace("\"sodium_value\":0.005", "\"sodium_value\":0.006"),
                OFF.replace("\"sodium\":0.005", "\"sodium\":null"),
                OFF.replace("\"sodium_100g\":0.005", "\"sodium_100g\":true"),
                OFF.replace("\"sodium_100g\":0.005", "\"sodium_100g\":null"))) {
            assertThrows(IllegalArgumentException.class, () -> read("OPEN_FOOD_FACTS", "sodium", body));
        }
    }
    @Test void providerAndVersionAreExplicitAllowLists() throws Exception {
        for (String field : List.of("sodium", "fiber", "vitamin_a", "calcium"))
            assertThrows(IllegalArgumentException.class, () -> read("USDA_FOODDATA", field, USDA));
        assertThrows(IllegalArgumentException.class, () -> contract.read("v1", "USDA_FOODDATA", "vitamin_e", json.readTree(USDA)));
        assertFalse(TypedSourceObservationContract.validGtin("00000000" + "bad"));
    }
}

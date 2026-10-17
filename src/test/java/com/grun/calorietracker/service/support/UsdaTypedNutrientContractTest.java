package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.MicronutrientTotalsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Future-source export semantics, not attestation of existing database scalars. */
class UsdaTypedNutrientContractTest {
    private JsonNode contract() throws IOException {
        try (var input = getClass().getResourceAsStream("/catalog/usda-typed-nutrient-contract-r5-v1.json")) {
            assertNotNull(input);
            return new ObjectMapper().readTree(input);
        }
    }

    @Test
    void vitaminAUsesDirectRaeSourceAndExistingApiContract() throws Exception {
        var field = contract().path("fields").path("vitamin_a");
        assertEquals(1106, field.path("nutrientId").asInt());
        assertEquals("320", field.path("nutrientNumber").asText());
        assertEquals("ug", field.path("sourceUnit").asText());
        assertEquals("MCG_RAE", field.path("canonicalUnit").asText());
        assertEquals("VITAMIN_A_RAE", field.path("semantic").asText());
        assertTrue(MicronutrientTotalsDto.class.getDeclaredField("vitaminA")
                .getAnnotation(Schema.class).description().contains("micrograms RAE"));
    }

    @Test
    void vitaminDAndSugarDoNotAliasOtherSourceForms() throws Exception {
        var fields = contract().path("fields");
        assertEquals(1114, fields.path("vitamin_d").path("nutrientId").asInt());
        assertEquals("ug", fields.path("vitamin_d").path("sourceUnit").asText());
        assertEquals("MCG", fields.path("vitamin_d").path("canonicalUnit").asText());
        assertTrue(MicronutrientTotalsDto.class.getDeclaredField("vitaminD")
                .getAnnotation(Schema.class).description().contains("micrograms"));
        assertEquals(2000, fields.path("sugar").path("nutrientId").asInt());
        assertEquals("g", fields.path("sugar").path("sourceUnit").asText());
        assertEquals("USDA_TOTAL_SUGARS_2000", fields.path("sugar").path("semantic").asText());
    }

    @Test
    void contractForbidsInferenceConversionAndLiveApproval() throws Exception {
        var value = contract();
        assertEquals("PER_100G", value.path("basis").asText());
        var policy = value.path("policy");
        assertEquals(6, policy.size());
        policy.forEach(permission -> {
            assertTrue(permission.isBoolean());
            assertFalse(permission.asBoolean());
        });
    }
}

package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.TypedSourceObservationRequest;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.service.support.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TypedSourceObservationServiceTest {
    JdbcTemplate jdbc;
    TypedNutrientEvidenceService service;
    Map<String, Object> parent;
    final Instant observed = Instant.now().minusSeconds(60);
    String body = TypedSourceObservationContractTest.USDA;
    String provider = "USDA_FOODDATA", field = "vitamin_e";
    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class); service = new TypedNutrientEvidenceService(jdbc, new ObjectMapper(), true);
        parent = TypedNutrientEvidenceServiceTest.parent(); parent.put("vitamin_e", 0.0);
        when(jdbc.queryForList(FoodProductDependencySnapshot.DISCOVER_SQL)).thenReturn(TypedNutrientEvidenceServiceTest.dependencyLayout());
        when(jdbc.queryForList(startsWith("SELECT * FROM food_items"), eq(1L))).thenAnswer(i -> List.of(parent));
        when(jdbc.queryForList(startsWith("SELECT * FROM food_product_source_evidence"), eq(1L))).thenReturn(List.of());
        when(jdbc.update(startsWith("INSERT INTO food_typed_nutrient_evidence"), any(Object[].class))).thenReturn(1, 0);
        when(jdbc.queryForObject(startsWith("SELECT id FROM food_typed"), eq(Long.class), anyString())).thenReturn(90L);
    }
    TypedSourceObservationRequest request(String fp, boolean approval, Instant time) {
        return new TypedSourceObservationRequest(TypedSourceObservationContract.VERSION, provider, field, body,
                TypedNutrientEvidenceServiceTest.hash(body), time, fp, approval, "Explicit source-only review, not a nutrition patch");
    }
    TypedSourceObservationRequest approved() {
        return request(service.previewObservation(1, request(null, false, observed)).currentFingerprint(), true, observed);
    }
    @Test void differingScalarCanBeRecordedButNeverApprovedOrUpdated() {
        var c = service.previewObservation(1, request(null, false, observed));
        assertEquals("DIFFERS", c.scalarComparison()); assertTrue(c.holds().contains("CURRENT_NUMERIC_REVIEW_REQUIRED"));
        assertFalse(c.legacyUnitAttested()); assertFalse(c.nutritionWriteAllowed()); assertTrue(c.observationOnly());
        assertEquals("UNATTESTED_LEGACY_UNIT", c.currentScalarUnit());
        assertEquals("NOT_COMPARABLE_WITHOUT_LEGACY_UNIT_ATTESTATION", c.canonicalCurrentComparison());
        assertEquals(3, c.releaseHolds().size());
        var request = approved(); assertTrue(service.appendObservation(1, request, "admin").created());
        assertFalse(service.appendObservation(1, request, "admin").created());
        assertEquals(0.0, parent.get("vitamin_e"));
        verify(jdbc, times(2)).update(startsWith("INSERT INTO food_typed_nutrient_evidence"), any(Object[].class));
        verify(jdbc, never()).update(startsWith("UPDATE"), any(Object[].class));
    }
    @Test void enabledApprovalReviewerAndCurrentFingerprintRemainRequired() {
        var request = approved();
        assertThrows(IllegalArgumentException.class, () -> new TypedNutrientEvidenceService(jdbc, new ObjectMapper(), false).appendObservation(1, request, "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.appendObservation(1, request, null));
        assertThrows(IllegalArgumentException.class, () -> service.appendObservation(1, request(request.expectedCurrentFingerprint(), false, observed), "admin"));
        parent.put("protein", 21);
        assertThrows(RequestConflictException.class, () -> service.appendObservation(1, request, "admin"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void staleFutureIdentityAndPriorContradictionsStillBlockSourceOnlyAppend() {
        for (Instant time : List.of(Instant.now().minusSeconds(90000), Instant.now().plusSeconds(3600))) {
            var preview = service.previewObservation(1, request(null, false, time));
            assertThrows(IllegalArgumentException.class, () -> service.appendObservation(1, request(preview.currentFingerprint(), true, time), "admin"));
        }
        parent.put("source_key", "USDA_FOODDATA:fdc:999");
        assertThrows(IllegalArgumentException.class, () -> service.appendObservation(1, approved(), "admin"));
        parent.put("source_key", "USDA_FOODDATA:fdc:172878");
        when(jdbc.queryForList(startsWith("SELECT semantic"), eq(1L), eq("vitamin_e")))
                .thenReturn(List.of(Map.of("semantic", "VITAMIN_E_ALPHA_TOCOPHEROL", "canonical_unit", "MG", "source_amount", 1)));
        assertThrows(IllegalArgumentException.class, () -> service.appendObservation(1, approved(), "admin"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void offCanonicalComparisonNeverConvertsCurrentScalarOrInfersMilkVolume() {
        provider = "OPEN_FOOD_FACTS"; field = "sodium"; body = TypedSourceObservationContractTest.OFF;
        parent.put("source_key", "barcode:00032056"); parent.put("data_source", provider);
        parent.put("catalog_type", "BRANDED_PRODUCT"); parent.put("normalized_barcode", "00032056");
        parent.put("barcode", "00032056"); parent.put("sodium", 0.0);
        var c = service.previewObservation(1, request(null, false, observed));
        assertEquals("MG", c.canonicalUnit()); assertEquals("PER_100G", c.sourceBasis());
        assertEquals("0.005", c.declaredSourceAmount().toPlainString()); assertEquals("5.000", c.sourceAmount().toPlainString());
        assertEquals(List.of("CURRENT_NUMERIC_REVIEW_REQUIRED"), c.holds());
        service.appendObservation(1, approved(), "admin"); assertEquals(0.0, parent.get("sodium"));
        body = body.replace("\"100g\"", "\"100ml\"");
        assertTrue(service.previewObservation(1, request(null, false, observed)).holds().contains("NUTRITION_BASIS_REVIEW_REQUIRED"));
    }
    @Test void unknownOptionalValueRemainsUnknownAndMalformedBodyNeverReadsDatabase() {
        parent.remove("vitamin_e");
        assertEquals("CURRENT_UNKNOWN", service.previewObservation(1, request(null, false, observed)).scalarComparison());
        clearInvocations(jdbc);
        for (String bad : List.of(body + "{}", body.replace("\"fdcId\":172878", "\"fdcId\":172878,\"fdcId\":999"))) {
            body = bad;
            assertThrows(IllegalArgumentException.class, () -> service.previewObservation(1, request(null, false, observed)));
        }
        verifyNoInteractions(jdbc);
    }
    @Test void equalNumberFromAnotherPriorSourceOrBasisIsStillAConflict() {
        for (String changed : List.of("provider", "external_id", "basis")) {
            var old = new HashMap<String, Object>(Map.of("semantic", "VITAMIN_E_ALPHA_TOCOPHEROL", "canonical_unit", "MG",
                    "source_amount", new java.math.BigDecimal("0.12"), "provider", provider,
                    "external_id", "USDA_FOODDATA:fdc:172878", "basis", "PER_100_G"));
            old.put(changed, "other");
            when(jdbc.queryForList(startsWith("SELECT semantic"), eq(1L), eq("vitamin_e"))).thenReturn(List.of(old));
            assertTrue(service.previewObservation(1, request(null, false, observed)).holds()
                    .contains("PRIOR_TYPED_OBSERVATION_CONFLICT_REQUIRES_REVIEW"));
            assertThrows(IllegalArgumentException.class, () -> service.appendObservation(1, approved(), "admin"));
        }
    }
}

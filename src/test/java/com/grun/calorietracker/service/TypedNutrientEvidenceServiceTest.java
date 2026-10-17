package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.TypedNutrientEvidenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.service.support.FoodProductDependencySnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class TypedNutrientEvidenceServiceTest {
    JdbcTemplate jdbc;
    TypedNutrientEvidenceService service;
    Map<String, Object> parent;
    static final String BODY = """
            {"fdcId":172878,"dataType":"SR Legacy","description":"Turkey, raw",
             "foodNutrients":[{"nutrient":{"id":1106,"number":"320","name":"Vitamin A, RAE","unitName":"UG"},"amount":6},
             {"nutrient":{"id":1104,"number":"318","unitName":"IU"},"amount":20}]}
            """;

    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        service = new TypedNutrientEvidenceService(jdbc, new ObjectMapper(), true);
        when(jdbc.queryForList(FoodProductDependencySnapshot.DISCOVER_SQL)).thenReturn(dependencyLayout());
        parent = parent();
        when(jdbc.queryForList(startsWith("SELECT * FROM food_items"), eq(1L))).thenAnswer(i -> List.of(parent));
        when(jdbc.queryForList(startsWith("SELECT * FROM food_product_source_evidence"), eq(1L))).thenReturn(List.of());
        when(jdbc.queryForList(startsWith("SELECT semantic"), eq(1L), eq("vitamin_a"))).thenReturn(List.of());
    }

    static Map<String, Object> parent() {
        var row = new HashMap<String, Object>();
        row.put("id", 1L); row.put("source_key", "USDA_FOODDATA:fdc:172878");
        row.put("data_source", "USDA_FOODDATA"); row.put("source_data_type", "SR Legacy");
        row.put("catalog_type", "GENERIC_INGREDIENT"); row.put("publication_status", "PUBLISHED");
        row.put("is_custom", false); row.put("nutrition_reference_unit", "PER_100G");
        row.put("nutrition_basis", "SOURCE_REPORTED"); row.put("vitamin_a", 6.0); row.put("protein", 20.0);
        return row;
    }
    public static List<Map<String, Object>> dependencyLayout() {
        var result = new ArrayList<Map<String, Object>>();
        for (String path : FoodProductDependencySnapshot.requiredPaths().stream().sorted().toList()) {
            String[] sides = path.split("->"), from = sides[0].split("\\."), to = sides[1].split("\\.");
            String table = from[1];
            var row = new TreeMap<String, Object>();
            row.put("constraint_name", "fk_" + table + "_" + from[2]); row.put("table_schema", "public"); row.put("table_name", table);
            row.put("column_name", from[2]);
            row.put("target_table", to[0]); row.put("target_column", to[1]);
            row.put("key_columns", 1); row.put("target_columns", 1); row.put("validated", true);
            row.put("relation_kind", "r"); row.put("column_type", "bigint"); result.add(row);
        }
        return result;
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    static TypedNutrientEvidenceRequest request(String body, String before) {
        return new TypedNutrientEvidenceRequest("vitamin_a", body, hash(body), Instant.now().minusSeconds(60), before, true, "Reviewed exact USDA source observation");
    }
    TypedNutrientEvidenceRequest approved() {
        return request(BODY, service.preview(1, request(BODY, null)).currentFingerprint());
    }

    @Test void exactRaeIsNotLegacyCertificationOrWholeProfileApproval() {
        var result = service.preview(1, request(BODY, null));
        assertTrue(result.holds().isEmpty()); assertEquals("ug", result.sourceUnit());
        assertEquals(1106, result.nutrientId()); assertEquals("MATCH_SCALAR_ONLY", result.scalarComparison());
        assertFalse(result.legacyUnitAttested()); assertFalse(result.nutritionWriteAllowed());
        assertEquals(3, result.releaseHolds().size());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void iuOnlyWrongUnitWrongNumberWrongFormDuplicateAndBooleanAreRefused() {
        List<String> invalid = List.of(BODY.replace("1106", "1105"), BODY.replace("\"UG\"", "\"IU\""),
                BODY.replace("\"320\"", "\"318\""), BODY.replace("Vitamin A, RAE", "Vitamin A"),
                BODY.replace("\"amount\":6", "\"amount\":true"), BODY.replace("\"amount\":6", "\"amount\":-1"),
                BODY.replace("\"foodNutrients\":[", "\"foodNutrients\":[{\"nutrient\":{\"id\":1106}},"),
                BODY.replace("\"fdcId\":172878", "\"fdcId\":172878,\"fdcId\":172879"), BODY + "{}");
        invalid.forEach(body -> assertThrows(IllegalArgumentException.class, () -> service.preview(1, request(body, null))));
    }
    @Test void sourceHashMismatchIsRefusedBeforeDatabaseRead() {
        clearInvocations(jdbc);
        var request = new TypedNutrientEvidenceRequest("vitamin_a", BODY, "0".repeat(64), Instant.now(), null, false, null);
        assertThrows(IllegalArgumentException.class, () -> service.preview(1, request)); verifyNoInteractions(jdbc);
    }
    @Test void microSymbolsAreNormalizedNotConverted() {
        for (String unit : List.of("ug", "\u00b5g", "\u03bcg")) {
            var result = service.preview(1, request(BODY.replace("UG", unit), null));
            assertEquals("ug", result.sourceUnit()); assertEquals(0, result.sourceAmount().compareTo(new java.math.BigDecimal("6")));
        }
    }
    @Test void staleFutureAndMissingObservationCannotAppend() {
        for (Instant time : List.of(Instant.now().minusSeconds(86460), Instant.now().plusSeconds(3600))) {
            var request = new TypedNutrientEvidenceRequest("vitamin_a", BODY, hash(BODY), time, null, false, null);
            assertTrue(service.preview(1, request).holds().contains("SOURCE_RECEIPT_EXPIRED_OR_FUTURE"));
        }
    }
    @Test void everyParentNutrientAndLegacyChangeRequiresNewPreview() {
        var request = approved(); parent.put("protein", 21.0);
        assertThrows(RequestConflictException.class, () -> service.append(1, request, "reviewer"));
        parent.put("protein", 20.0);
        when(jdbc.queryForList(startsWith("SELECT * FROM food_product_source_evidence"), eq(1L)))
                .thenReturn(List.of(Map.of("id", 9, "fingerprint", "changed")));
        assertThrows(RequestConflictException.class, () -> service.append(1, request, "reviewer"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void unknownAndCloseButDifferentAreNeverFilledOrRounded() {
        for (Double value : Arrays.asList(null, 6.04, Double.NaN)) {
            parent.put("vitamin_a", value);
            assertTrue(service.preview(1, request(BODY, null)).holds().contains("CURRENT_NUMERIC_REVIEW_REQUIRED"));
        }
        parent.put("vitamin_a", 6.0);
        assertTrue(service.preview(1, request(BODY.replace("\"amount\":6", "\"amount\":6.000000000000000000001"), null))
                .holds().contains("CURRENT_NUMERIC_REVIEW_REQUIRED"));
    }
    @Test void wrongSourceMassVolumeAndCustomCatalogRemainHeld() {
        parent.put("source_key", "USDA_FOODDATA:fdc:999"); parent.put("nutrition_reference_unit", "PER_100ML");
        parent.put("is_custom", true);
        assertEquals(3, service.preview(1, request(BODY, null)).holds().size());
    }
    @Test void contradictoryPriorTypedObservationRequiresReview() {
        when(jdbc.queryForList(startsWith("SELECT semantic"), eq(1L), eq("vitamin_a")))
                .thenReturn(List.of(Map.of("semantic", "VITAMIN_A_RAE", "canonical_unit", "MCG_RAE", "source_amount", 7)));
        assertTrue(service.preview(1, request(BODY, null)).holds().contains("PRIOR_TYPED_OBSERVATION_CONFLICT_REQUIRES_REVIEW"));
    }
    @Test void appendNeedsSeparateEnableApprovalReasonAndAuthenticatedReviewer() {
        var request = approved();
        assertThrows(IllegalArgumentException.class, () -> new TypedNutrientEvidenceService(jdbc, new ObjectMapper(), false).append(1, request, "reviewer"));
        assertThrows(IllegalArgumentException.class, () -> service.append(1, request, null));
        var unapproved = new TypedNutrientEvidenceRequest(request.field(), request.sourceJson(), request.sourceSha256(), request.observedAt(), request.expectedCurrentFingerprint(), false, request.approvalReason());
        assertThrows(IllegalArgumentException.class, () -> service.append(1, unapproved, "reviewer"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void onlyAppendTableCanBeWrittenAndIdenticalRepeatReturnsOriginalId() {
        var request = approved();
        when(jdbc.update(startsWith("INSERT INTO food_typed_nutrient_evidence"), any(Object[].class))).thenReturn(1, 0);
        when(jdbc.queryForObject(startsWith("SELECT id FROM food_typed"), eq(Long.class), anyString())).thenReturn(88L);
        assertTrue(service.append(1, request, "reviewer").created());
        assertFalse(service.append(1, request, "reviewer").created());
        verify(jdbc, times(2)).update(startsWith("INSERT INTO food_typed_nutrient_evidence"), any(Object[].class));
        assertEquals(6.0, parent.get("vitamin_a"));
    }
    @Test void popularityCountersDoNotInvalidateNutritionReview() {
        var first = service.preview(1, request(BODY, null)).currentFingerprint();
        parent.put("usage_count", 99); parent.put("search_selection_count", 10);
        assertEquals(first, service.preview(1, request(BODY, null)).currentFingerprint());
    }
    @Test void vitaminDAndSugarRequireTheirOwnExactIdsAndUnits() {
        for (String field : List.of("vitamin_d", "sugar")) {
            boolean vitamin = field.equals("vitamin_d");
            String id = vitamin ? "1114" : "2000", number = vitamin ? "328" : "269";
            double amount = vitamin ? 0.3 : 5;
            parent.put(field, amount);
            String body = BODY.replace("1106", id).replace("320", number)
                    .replace("Vitamin A, RAE", vitamin ? "Vitamin D (D2 + D3)" : "Sugars, total including NLEA")
                    .replace("\"amount\":6", "\"amount\":" + amount);
            if (!vitamin) body = body.replace("\"UG\"", "\"G\"");
            var request = new TypedNutrientEvidenceRequest(field, body, hash(body), Instant.now().minusSeconds(60), null, false, null);
            var result = service.preview(1, request);
            assertTrue(result.holds().isEmpty()); assertEquals(vitamin ? "MCG" : "G", result.canonicalUnit());
            String alternate = body.replace(id, vitamin ? "1110" : "1063");
            var unsupported = new TypedNutrientEvidenceRequest(field, alternate, hash(alternate), request.observedAt(), null, false, null);
            assertThrows(IllegalArgumentException.class, () -> service.preview(1, unsupported));
        }
    }
    @Test void newOfflineDifferenceFactsCannotBypassExistingTypedLedgerFieldContract() {
        clearInvocations(jdbc);
        for (String field : List.of("potassium", "vitamin_e", "vitamin_b12", "sodium", "fiber")) {
            var request = new TypedNutrientEvidenceRequest(field, BODY, hash(BODY), Instant.now().minusSeconds(60), null, true, "Offline facts are not automatic approval");
            assertThrows(IllegalArgumentException.class, () -> service.preview(1, request));
        }
        verifyNoInteractions(jdbc);
    }
}

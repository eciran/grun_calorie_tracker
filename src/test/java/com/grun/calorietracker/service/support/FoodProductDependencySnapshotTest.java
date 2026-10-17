package com.grun.calorietracker.service.support;

import com.grun.calorietracker.service.TypedNutrientEvidenceServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FoodProductDependencySnapshotTest {
    JdbcTemplate jdbc;
    FoodProductDependencySnapshot snapshots;
    List<Map<String, Object>> layout;
    @BeforeEach void prepare() {
        jdbc = mock(JdbcTemplate.class); snapshots = new FoodProductDependencySnapshot(jdbc);
        layout = TypedNutrientEvidenceServiceTest.dependencyLayout();
        when(jdbc.queryForList(FoodProductDependencySnapshot.DISCOVER_SQL)).thenAnswer(i -> layout);
    }
    @Test void emptyButMeasuredDependenciesAreCompleteAndDoNotExposeRows() {
        var snapshot = snapshots.capture(1, false);
        assertTrue(snapshot.complete()); assertEquals(29, snapshot.relations().size()); assertEquals(0, snapshot.rowsExamined());
        assertTrue(snapshot.relations().stream().allMatch(r -> r.fingerprint().matches("[a-f0-9]{64}")));
    }
    @Test void missingRequiredCompositeUnvalidatedAndOutsideSchemaRemainIncomplete() {
        var original = TypedNutrientEvidenceServiceTest.dependencyLayout();
        for (String defect : List.of("missing", "composite", "unvalidated", "schema", "type")) {
            layout = new ArrayList<>(); original.forEach(e -> layout.add(new TreeMap<>(e)));
            if (defect.equals("missing")) layout.remove(0);
            else layout.get(0).put(switch (defect) {
                case "composite" -> "key_columns"; case "unvalidated" -> "validated"; case "schema" -> "table_schema";
                default -> "column_type";
            }, switch (defect) { case "composite" -> 2; case "unvalidated" -> false; case "schema" -> "unknown"; default -> "integer"; });
            assertFalse(snapshots.capture(1, false).complete(), defect);
        }
    }
    @Test void tooManyRelationsOrRowsNeverBecomeFullCoverage() {
        layout = new ArrayList<>(Collections.nCopies(65, layout.get(0)));
        assertFalse(snapshots.capture(1, false).complete());
        layout = TypedNutrientEvidenceServiceTest.dependencyLayout();
        when(jdbc.queryForList(startsWith("SELECT encode(sha256"), eq(1L), anyInt()))
                .thenReturn(Collections.nCopies(4097, Map.of("row_hash", "a".repeat(64))));
        assertFalse(snapshots.capture(1, false).complete());
    }
    @Test void optionLockAndHashQueriesRemainBoundAndHistoryHashesAreStable() {
        when(jdbc.queryForList(startsWith("SELECT encode(sha256"), eq(1L), anyInt()))
                .thenReturn(List.of(Map.of("row_hash", "b".repeat(64)), Map.of("row_hash", "a".repeat(64))))
                .thenReturn(List.of(Map.of("row_hash", "a".repeat(64)), Map.of("row_hash", "b".repeat(64))));
        var snapshot = snapshots.capture(1, true);
        assertEquals(snapshot.relations().get(0).fingerprint(), snapshot.relations().get(1).fingerprint());
        verify(jdbc).queryForList(contains("ORDER BY id LIMIT 4097 FOR UPDATE"), eq(1L));
        verify(jdbc, times(29)).queryForList(contains("FOR SHARE OF d"), eq(1L), anyInt());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void metadataIdentifiersCannotInjectSql() {
        layout.get(0).put("table_name", "food_items;drop table food_items");
        assertFalse(snapshots.capture(1, false).complete());
        verify(jdbc, never()).queryForList(contains("drop table"), any(Object[].class));
    }
}

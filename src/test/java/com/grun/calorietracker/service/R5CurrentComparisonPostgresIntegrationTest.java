package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GRUN_RUN_R5_COMPARISON_POSTGRES", matches = "true")
class R5CurrentComparisonPostgresIntegrationTest {
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static final ObjectMapper json = new ObjectMapper();
    static String sql;
    static final long FOOD = 190000001;

    @BeforeAll static void migrateOwnedDatabaseOnly() throws Exception {
        String url = System.getenv("GRUN_R5_COMPARISON_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_r5_comparison_test"))
            throw new IllegalStateException("Only the owned localhost comparison DB is allowed");
        source = new DriverManagerDataSource(url, "r5_test", ""); jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        jdbc.update("INSERT INTO food_items(id,name,is_custom,publication_status,data_source,source_key,calories,protein,fat,carbs) VALUES (?,'Independent snapshot fixture',false,'PUBLISHED','USDA_FOODDATA','USDA_FOODDATA:fdc:50000001',100,1,2,3)", FOOD);
        jdbc.update("INSERT INTO food_items(id,name,is_custom) VALUES (?,'Second snapshot fixture',false)", FOOD + 1);
        sql = Files.readString(Path.of("outputs/r5-comparison-cycle-20261015/owned-query-bounded.sql"));
    }

    List<JsonNode> read() throws Exception {
        var frames = new ArrayList<JsonNode>();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            boolean rows = statement.execute(sql);
            while (true) {
                if (rows) {
                    try (var result = statement.getResultSet()) {
                        while (result.next()) frames.add(json.readTree(result.getString(1)));
                    }
                } else if (statement.getUpdateCount() == -1) break;
                rows = statement.getMoreResults();
            }
        }
        return frames;
    }
    JsonNode product(List<JsonNode> frames) {
        return frames.stream().filter(f -> f.path("kind").asText().equals("row") && f.path("row").path("id").asLong() == FOOD)
                .findFirst().orElseThrow().path("row");
    }

    @Test void exactReadOnlyCountFullFieldCoverageAndDeclaredLayout() throws Exception {
        var frames = read();
        assertEquals(4, frames.size()); assertEquals("on", frames.get(0).path("readonly").asText());
        assertEquals(2, frames.get(0).path("count").asInt()); assertEquals(2, frames.get(3).path("count").asInt());
        var row = product(frames);
        assertTrue(row.path("food").has("vitamin_b12")); assertTrue(row.path("food").has("sugar_alcohol"));
        assertEquals(31, row.path("dependencies").path("layout").size());
        assertEquals(29, row.path("dependencies").path("relations").size());
        assertTrue(row.path("dependencies").path("complete").asBoolean());
        assertTrue(row.path("dependencies").path("runtimeApprovalFingerprint").isNull());
    }

    @Test void parentAndAliasesAreCapturedAndChangeIndependentFingerprint() throws Exception {
        String before = product(read()).toString();
        jdbc.update("UPDATE food_items SET display_name='Changed source fixture' WHERE id=?", FOOD);
        assertNotEquals(before, product(read()).toString());
        var first = product(read());
        jdbc.update("INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type) VALUES (?,'Fixture alias','fixture alias','EN','SYNONYM')", FOOD);
        var second = product(read());
        assertNotEquals(first.path("dependencies"), second.path("dependencies"));
        assertTrue(second.path("aliases").size() > first.path("aliases").size());
    }

    @Test void unknownForeignKeyCannotBeSilentlyExcluded() throws Exception {
        jdbc.execute("CREATE TABLE owned_extra_reference(id BIGINT PRIMARY KEY,food_id BIGINT REFERENCES food_items(id))");
        try {
            assertFalse(product(read()).path("dependencies").path("complete").asBoolean());
        } finally { jdbc.execute("DROP TABLE owned_extra_reference"); }
    }

    @Test void boundedDependencyOverflowStaysIncomplete() throws Exception {
        jdbc.update("INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type) SELECT ?, 'Overflow '||i,'overflow '||i,'EN','SYNONYM' FROM generate_series(1,4097) i", FOOD);
        try {
            var deps = product(read()).path("dependencies");
            assertFalse(deps.path("complete").asBoolean()); assertTrue(deps.path("rowsExamined").asInt() >= 4097);
            assertTrue(product(read()).path("aliases").size() <= 4097);
        } finally { jdbc.update("DELETE FROM food_item_search_aliases WHERE food_item_id=? AND normalized_alias LIKE 'overflow %'", FOOD); }
    }

    @Test void readOnlySqlCannotRewriteParentAndLeavesNoOpenTransaction() throws Exception {
        String before = jdbc.queryForObject("SELECT to_jsonb(f)::text FROM food_items f WHERE id=?", String.class, FOOD);
        read();
        assertEquals(before, jdbc.queryForObject("SELECT to_jsonb(f)::text FROM food_items f WHERE id=?", String.class, FOOD));
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.execute("BEGIN TRANSACTION READ ONLY");
            assertThrows(java.sql.SQLException.class, () -> s.execute("UPDATE food_items SET calories=0 WHERE id=" + FOOD));
            s.execute("ROLLBACK");
        }
    }
}

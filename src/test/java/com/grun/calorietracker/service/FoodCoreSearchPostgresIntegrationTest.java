package com.grun.calorietracker.service;

import com.grun.calorietracker.service.support.PostgresFoodSearchCandidateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_CORE_SEARCH_POSTGRES", matches = "true")
class FoodCoreSearchPostgresIntegrationTest extends FoodItemServiceSearchIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @org.junit.jupiter.api.Test
    void reviewedAwsSourceProjectionRetainsAllSixtyTokenRecallAnchors() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath().resolve("outputs").normalize();
        String receiptValue = System.getenv("GRUN_R7_TOKEN_RECALL_RECEIPT");
        String reportValue = System.getenv("GRUN_R7_TOKEN_RECALL_REPORT");
        org.junit.jupiter.api.Assumptions.assumeTrue(receiptValue != null && reportValue != null,
                "Explicit owned source-projection inputs required");
        var receiptPath = java.nio.file.Path.of(receiptValue).toRealPath();
        var reportPath = java.nio.file.Path.of(reportValue).toRealPath();
        org.junit.jupiter.api.Assertions.assertTrue(receiptPath.startsWith(root.toRealPath()) && reportPath.startsWith(root.toRealPath()));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var receipt = mapper.readTree(receiptPath.toFile());
        var report = mapper.readTree(reportPath.toFile());
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(receiptPath)));
        org.junit.jupiter.api.Assertions.assertEquals(digest, report.path("receiptSha256").asText());
        org.junit.jupiter.api.Assertions.assertEquals(60, report.path("judgments").size());
        var ids = new java.util.HashSet<String>();
        for (var row : receipt.path("rows")) {
            ids.add(row.path("id").asText());
            org.junit.jupiter.api.Assertions.assertTrue(row.path("food").path("brand_id").isNull()
                    && row.path("food").path("created_by_user_id").isNull());
            jdbc.update("insert into food_items select * from jsonb_populate_record(null::food_items,?::jsonb)", row.path("food").toString());
        }
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("49340", "181013", "181138", "181140", "181155"), ids);
        for (var category : receipt.path("dependencies").path("categories")) {
            jdbc.update("insert into food_categories select * from jsonb_populate_record(null::food_categories,?::jsonb)", category.toString());
        }
        for (var row : receipt.path("rows")) {
            for (var category : row.path("assignments")) {
                jdbc.update("insert into food_item_categories select * from jsonb_populate_record(null::food_item_categories,?::jsonb)", category.toString());
            }
            for (String key : java.util.List.of("aliases", "localizations", "options", "option_localizations")) {
                String table = switch (key) {
                    case "aliases" -> "food_item_search_aliases";
                    case "localizations" -> "food_item_localizations";
                    case "options" -> "food_item_serving_options";
                    default -> "food_item_serving_option_localizations";
                };
                for (var child : row.path("name_review").path(key)) {
                    jdbc.update("insert into " + table + " select * from jsonb_populate_record(null::" + table + ",?::jsonb)", child.toString());
                }
            }
        }
        var service = (com.grun.calorietracker.service.impl.FoodItemServiceImpl)
                ReflectionTestUtils.getField(this, "foodItemService");
        int evaluated = 0;
        for (var judgment : report.path("judgments")) {
            var params = judgment.path("params");
            var criteria = new com.grun.calorietracker.dto.FoodSearchCriteriaDto();
            criteria.setQuery(params.path("q").asText());
            criteria.setCategoryId(params.path("categoryId").asLong());
            criteria.setCatalogType(com.grun.calorietracker.enums.FoodCatalogType.valueOf(params.path("catalogType").asText()));
            criteria.setMarketRegion(com.grun.calorietracker.enums.MarketRegion.valueOf(params.path("region").asText()));
            criteria.setPreferredLanguage(com.grun.calorietracker.enums.PreferredLanguage.valueOf(params.path("language").asText()));
            if (params.has("preparationState")) {
                criteria.setPreparationState(com.grun.calorietracker.enums.FoodPreparationState.valueOf(params.path("preparationState").asText()));
            }
            var content = service.searchFoodItems(criteria, 0, 100).getContent();
            var found = content.stream().filter(p -> p.getId() == judgment.path("expectedId").asLong()).findFirst();
            org.junit.jupiter.api.Assertions.assertTrue(found.isPresent(), judgment.path("id").asText());
            org.junit.jupiter.api.Assertions.assertEquals(judgment.path("expectedSourceKey").asText(), found.orElseThrow().getSourceKey());
            org.junit.jupiter.api.Assertions.assertEquals(judgment.path("expectedLabel").asText(), found.orElseThrow().getDisplayName());
            org.junit.jupiter.api.Assertions.assertEquals(content.size(), content.stream().map(p -> p.getId()).distinct().count());
            evaluated++;
        }
        System.out.println("R7_SOURCE_TOKEN_PROJECTION cases=" + evaluated + " retained=60 postgres=18.3 projectionOnly=true");
    }

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("GRUN_CORE_SEARCH_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_r7_search_test")) {
            throw new IllegalStateException("Only the disposable localhost core-search DB is allowed");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "r7_test");
        registry.add("spring.datasource.password", () -> System.getenv("GRUN_CORE_SEARCH_TEST_PASSWORD"));
    }

    @org.junit.jupiter.api.Test
    void frozenReviewedSourceCohortHasIndependentIdentityAndRankingJudgments() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode fixture;
        try (var input = new org.springframework.core.io.ClassPathResource("r7-reviewed-source-relevance-v1.json").getInputStream()) {
            fixture = mapper.readTree(input);
        }
        String value = System.getenv("GRUN_R7_SOURCE_ORACLE_RECEIPT");
        org.junit.jupiter.api.Assertions.assertNotNull(value, "Explicit frozen source receipt required for this gate");
        var root = java.nio.file.Path.of("outputs").toRealPath();
        var source = java.nio.file.Path.of(value).toRealPath();
        org.junit.jupiter.api.Assertions.assertTrue(source.startsWith(root));
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(source)));
        org.junit.jupiter.api.Assertions.assertEquals(fixture.path("receiptSha256").asText(), digest);
        var receipt = mapper.readTree(source.toFile());
        org.junit.jupiter.api.Assertions.assertEquals(37, receipt.path("rows").size());
        var brand = receipt.path("commonReceipt").path("brand");
        jdbc.update("insert into food_brands select * from jsonb_populate_record(null::food_brands,?::jsonb)", brand.toString());
        var brandAlias = receipt.path("commonReceipt").path("brandAlias");
        jdbc.update("insert into food_brand_aliases select * from jsonb_populate_record(null::food_brand_aliases,?::jsonb)", brandAlias.toString());
        var rows = new java.util.HashMap<Long, com.fasterxml.jackson.databind.JsonNode>();
        for (var row : receipt.path("rows")) {
            long id = row.path("id").asLong();
            org.junit.jupiter.api.Assertions.assertNull(rows.put(id, row), "Duplicate source product id");
            jdbc.update("insert into food_items select * from jsonb_populate_record(null::food_items,?::jsonb)", row.path("food").toString());
            for (String field : java.util.List.of("aliases", "localizations")) {
                String table = field.equals("aliases") ? "food_item_search_aliases" : "food_item_localizations";
                for (var child : row.path("name_review").path(field)) {
                    jdbc.update("insert into " + table + " select * from jsonb_populate_record(null::" + table + ",?::jsonb)", child.toString());
                }
            }
        }
        for (var resolution : receipt.path("dependencies").path("commonCanonicalResolutions")) {
            jdbc.update("insert into food_canonical_resolutions select * from jsonb_populate_record(null::food_canonical_resolutions,?::jsonb)", resolution.toString());
        }
        String before = sourceReadProjectionFingerprint();
        var service = (com.grun.calorietracker.service.impl.FoodItemServiceImpl)
                ReflectionTestUtils.getField(this, "foodItemService");
        org.junit.jupiter.api.Assertions.assertEquals(32, fixture.path("judgments").size());
        int evaluated = 0;
        int topOneHits = 0;
        int hiddenChecks = 0;
        for (boolean indexed : java.util.List.of(false, true)) {
            ReflectionTestUtils.setField(service, "postgresFoodSearchCandidateProvider",
                    indexed ? new PostgresFoodSearchCandidateProvider(jdbc) : null);
            for (var judgment : fixture.path("judgments")) {
                var criteria = new com.grun.calorietracker.dto.FoodSearchCriteriaDto();
                criteria.setQuery(judgment.path("q").asText());
                criteria.setPreferredLanguage(com.grun.calorietracker.enums.PreferredLanguage.valueOf(judgment.path("language").asText()));
                criteria.setMarketRegion(com.grun.calorietracker.enums.MarketRegion.valueOf(judgment.path("region").asText()));
                criteria.setPreparationState(com.grun.calorietracker.enums.FoodPreparationState.valueOf(judgment.path("preparationState").asText()));
                criteria.setCatalogType(com.grun.calorietracker.enums.FoodCatalogType.valueOf(judgment.path("catalogType").asText()));
                if (judgment.has("brand")) criteria.setBrand(judgment.path("brand").asText());
                var page = service.searchFoodItems(criteria, 0, 25);
                String caseId = judgment.path("id").asText() + " indexed=" + indexed;
                var expectedRow = rows.get(judgment.path("expectedId").asLong());
                org.junit.jupiter.api.Assertions.assertNotNull(expectedRow, caseId);
                org.junit.jupiter.api.Assertions.assertEquals(judgment.path("expectedSourceKey").asText(), expectedRow.path("food").path("source_key").asText(), caseId);
                evaluated++;
                if (judgment.has("expectedVisibility")) {
                    org.junit.jupiter.api.Assertions.assertEquals(judgment.path("expectedVisibility").asText(), expectedRow.path("food").path("publication_status").asText(), caseId);
                    org.junit.jupiter.api.Assertions.assertTrue(page.getContent().isEmpty(), caseId);
                    org.junit.jupiter.api.Assertions.assertEquals(0L, page.getTotalElements(), caseId);
                    hiddenChecks++;
                    continue;
                }
                org.junit.jupiter.api.Assertions.assertFalse(page.getContent().isEmpty(), caseId);
                var top = page.getContent().get(0);
                org.junit.jupiter.api.Assertions.assertEquals(judgment.path("expectedId").asLong(), top.getId(), caseId);
                org.junit.jupiter.api.Assertions.assertEquals(judgment.path("expectedSourceKey").asText(), top.getSourceKey(), caseId);
                var row = rows.get(top.getId());
                var expectedLabel = java.util.stream.StreamSupport.stream(row.path("name_review").path("localizations").spliterator(), false)
                        .filter(label -> label.path("active").asBoolean() && label.path("language").asText().equals(judgment.path("language").asText()))
                        .findFirst().orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals(expectedLabel.path("display_name").asText(), top.getDisplayName(), caseId);
                org.junit.jupiter.api.Assertions.assertEquals(row.path("food").path("calories").asDouble(), top.getCalories(), caseId);
                org.junit.jupiter.api.Assertions.assertEquals(row.path("food").path("nutrition_reference_unit").asText(), top.getNutritionReferenceUnit().name(), caseId);
                org.junit.jupiter.api.Assertions.assertEquals(page.getContent().size(), page.getContent().stream().map(p -> p.getId()).distinct().count(), caseId);
                topOneHits++;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(before, sourceReadProjectionFingerprint(), "Search must not mutate the frozen read projection");
        org.junit.jupiter.api.Assertions.assertEquals(60, topOneHits);
        org.junit.jupiter.api.Assertions.assertEquals(4, hiddenChecks);
        System.out.println("R7_SOURCE_RELEVANCE_ORACLE rows=37 judgments=32 evaluations=" + evaluated
                + " top1=" + topOneHits + " hiddenChecks=" + hiddenChecks + " projectionOnly=true");
    }

    private String sourceReadProjectionFingerprint() {
        return jdbc.queryForObject("""
                select md5(jsonb_build_object(
                    'foods',(select jsonb_agg(to_jsonb(f) order by id) from food_items f),
                    'aliases',(select jsonb_agg(to_jsonb(a) order by id) from food_item_search_aliases a),
                    'labels',(select jsonb_agg(to_jsonb(l) order by id) from food_item_localizations l),
                    'resolutions',(select jsonb_agg(to_jsonb(r) order by canonical_food_key) from food_canonical_resolutions r),
                    'brands',(select jsonb_agg(to_jsonb(b) order by id) from food_brands b),
                    'brandAliases',(select jsonb_agg(to_jsonb(a) order by id) from food_brand_aliases a)
                )::text)
                """, String.class);
    }

    @BeforeEach
    void useActualPostgresCandidatePipeline() {
        Object service = ReflectionTestUtils.getField(this, "foodItemService");
        ReflectionTestUtils.setField(service, "postgresFoodSearchCandidateProvider",
                new PostgresFoodSearchCandidateProvider(jdbc));
    }
}

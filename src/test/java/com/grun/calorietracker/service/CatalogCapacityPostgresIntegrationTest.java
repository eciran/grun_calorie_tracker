package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.service.impl.CatalogPublicationServiceImpl;
import com.grun.calorietracker.service.support.PostgresFoodSearchCandidateProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_CATALOG_CAPACITY_POSTGRES", matches = "true")
class CatalogCapacityPostgresIntegrationTest extends BroadSourceFoodSearchIntegrationTest {
    @Autowired EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource static void ownedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("GRUN_CATALOG_CAPACITY_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_catalog_capacity_test"))
            throw new IllegalStateException("Only the owned disposable localhost capacity DB is allowed");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "r7_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Test void twoHundredThousandBackgroundRowsPreserveIndependentSourceJudgmentsAndOverflowPaging() throws Exception {
        jdbc.execute("set local statement_timeout='10s'");
        jdbc.update("""
            insert into food_items(name,brand,source_key,catalog_type,data_source,market_region,
              preparation_state,nutrition_reference_unit,nutrition_basis,publication_status,verification_status,
              is_custom,calories,protein,fat,carbs,quality_score,usage_count,search_selection_count)
            select case when n<=5001 then 'Overflowbenchmark' else 'Capacity Background Entry '||n end,
              'Capacity Fixture','CAPACITY:BACKGROUND:'||n,'BRANDED_PRODUCT','OPEN_FOOD_FACTS',
              case when n<=5001 then 'UK_IE' when n%3=0 then 'TR' when n%3=1 then 'UK_IE' else 'EU' end,
              'UNSPECIFIED','PER_100G','SOURCE_REPORTED','PUBLISHED','RAW_IMPORTED',false,100,5,2,18,60,0,0
            from generate_series(1,200000) n
            """);
        assertEquals(200059, foods.count());
        var populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V62__add_food_item_trigram_search_index.sql"),
                new ClassPathResource("db/migration/V127__optimize_food_search_candidates.sql"),
                new ClassPathResource("db/migration/V259__index_food_brand_separator_search.sql"));
        populator.execute(Objects.requireNonNull(jdbc.getDataSource()));
        jdbc.execute("analyze food_items"); jdbc.execute("analyze food_item_search_aliases");
        jdbc.execute("analyze food_item_localizations");
        String plan = jdbc.queryForObject("explain (format json) select id from food_items where lower(name) like '%ham%'", String.class);
        assertNotNull(plan); assertTrue(plan.contains("Index Scan"), plan);

        var provider = new PostgresFoodSearchCandidateProvider(jdbc);
        var publisher = new CatalogPublicationServiceImpl(foods, mock(AdminAuditService.class));
        var judgments = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        fixture.path("judgments").forEach(judgments::add);
        try (var input = new ClassPathResource("r7-capacity-source-judgments-v1.json").getInputStream()) {
            var extra = mapper.readTree(input);
            assertEquals(fixture.path("cohortSha256"), extra.path("cohortSha256"));
            assertEquals(6, extra.path("judgments").size()); extra.path("judgments").forEach(judgments::add);
        }
        var timings = new LinkedHashMap<String, List<Long>>();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        for (boolean indexed : List.of(false, true)) {
            ReflectionTestUtils.setField(search, "postgresFoodSearchCandidateProvider", indexed ? provider : null);
            List<Long> elapsed = new ArrayList<>();
            for (var judgment : judgments) {
                for (var item : seeded.values()) {
                    if (item.getDataSource() == FoodDataSource.COFID) {
                        if (judgment.path("fixtureApprovalSimulation").asBoolean())
                            publisher.publish(item.getId(), "test-only@example.invalid", "Controlled capacity fixture, not source approval", null);
                        else item.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
                    }
                }
                foods.flush();
                var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(judgment.path("q").asText());
                criteria.setMarketRegion(MarketRegion.valueOf(judgment.path("region").asText()));
                criteria.setPreferredLanguage(PreferredLanguage.valueOf(judgment.path("language").asText()));
                if (judgment.has("catalogType")) criteria.setCatalogType(FoodCatalogType.valueOf(judgment.path("catalogType").asText()));
                if (judgment.has("preparationState")) criteria.setPreparationState(FoodPreparationState.valueOf(judgment.path("preparationState").asText()));
                long started = System.nanoTime();
                var result = search.searchFoodItems(criteria, 0, 25);
                elapsed.add((System.nanoTime() - started) / 1_000_000);
                var expected = new HashSet<String>(); judgment.path("expectedKeys").forEach(v -> expected.add(v.asText()));
                assertEquals(expected, new HashSet<>(result.getContent().stream().map(p -> p.getSourceKey()).toList()), judgment.path("id").asText());
                assertEquals(expected.size(), result.getTotalElements());
                if (!expected.isEmpty()) {
                    var acceptable = new HashSet<String>(); judgment.path("acceptableTopKeys").forEach(v -> acceptable.add(v.asText()));
                    assertTrue(acceptable.contains(result.getContent().get(0).getSourceKey()), judgment.path("id").asText());
                }
                assertEquals(result.getContent().size(), result.getContent().stream().map(p -> p.getId()).distinct().count());
                result.getContent().forEach(p -> {
                    var original = seeded.get(p.getSourceKey());
                    assertEquals(original.getCalories(), p.getCalories()); assertEquals(original.getBarcode(), p.getBarcode());
                    assertEquals(original.getNutritionReferenceUnit(), p.getNutritionReferenceUnit());
                });
            }
            timings.put(indexed ? "candidateIndex" : "ormFallback", elapsed);
        }

        ReflectionTestUtils.setField(search, "postgresFoodSearchCandidateProvider", provider);
        var denseCandidates = provider.findCandidateIds("overflowbenchmark").orElseThrow();
        assertEquals(5001, denseCandidates.size(),
                "Dense candidate path must retain every matching id instead of falling back or truncating");
        assertEquals(5001, new HashSet<>(denseCandidates).size(),
                "Dense candidate path must not introduce duplicate ids");
        var overflow = new FoodSearchCriteriaDto(); overflow.setQuery("overflowbenchmark"); overflow.setMarketRegion(MarketRegion.UK_IE);
        long overflowStarted = System.nanoTime();
        var first = search.searchFoodItems(overflow, 0, 25);
        long overflowFirstMs = (System.nanoTime() - overflowStarted) / 1_000_000;
        overflowStarted = System.nanoTime(); var next = search.searchFoodItems(overflow, 1, 25);
        long overflowNextMs = (System.nanoTime() - overflowStarted) / 1_000_000;
        assertEquals(5001, first.getTotalElements()); assertEquals(5001, next.getTotalElements());
        assertEquals(25, first.getContent().size()); assertEquals(25, next.getContent().size());
        assertTrue(overflowFirstMs < 1000 && overflowNextMs < 1000,
                "Local dense-query regression tripwire, not an AWS SLO: "
                        + List.of(overflowFirstMs, overflowNextMs));
        var pageIds = new HashSet<Long>(); first.getContent().forEach(p -> assertTrue(pageIds.add(p.getId())));
        next.getContent().forEach(p -> assertTrue(pageIds.add(p.getId())));
        assertEquals(200059, foods.count());
        long statements = statistics.getPrepareStatementCount();
        var metrics = new LinkedHashMap<String, Object>(); metrics.put("rows", 200059); metrics.put("pinnedSourceRows", 59);
        metrics.put("judgments", judgments.size() * 2); metrics.put("newIndependentJudgments", 6);
        metrics.put("overflowRows", 5001); metrics.put("pageOverlap", 0);
        metrics.put("denseCandidateRows", denseCandidates.size());
        metrics.put("denseCandidateMode", "INDEXED_LOSSLESS");
        metrics.put("overflowPageTimesMs", List.of(overflowFirstMs, overflowNextMs));
        metrics.put("sqlStatements", statements); metrics.put("rawTimingsMs", timings);
        metrics.put("cacheAdvice", false); metrics.put("awsAcceptance", false); metrics.put("sourceApproval", false);
        for (var entry : timings.entrySet()) {
            var sorted = entry.getValue().stream().sorted().toList();
            long p95 = sorted.get((int)Math.ceil(sorted.size() * .95) - 1);
            metrics.put(entry.getKey() + "P95Ms", p95);
        }
        System.out.println("CATALOG_CAPACITY_METRICS " + mapper.writeValueAsString(metrics));
        assertTrue(statements <= 2000, "Bounded result hydration exceeded SQL statement budget: " + statements);
        assertTrue(timings.values().stream().flatMap(Collection::stream).allMatch(ms -> ms < 3000),
                "Local3-second regression tripwire, not an AWS SLO: " + metrics);
    }
}

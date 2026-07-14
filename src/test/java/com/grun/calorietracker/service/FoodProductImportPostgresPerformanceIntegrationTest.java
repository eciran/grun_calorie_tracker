package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.impl.FoodProductImportServiceImpl;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_POSTGRES_BENCHMARK", matches = "true")
class FoodProductImportPostgresPerformanceIntegrationTest {

    private static final int PRODUCT_COUNT = 500;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private FoodItemLocalizationRepository foodItemLocalizationRepository;

    @Autowired
    private FoodItemSearchAliasRepository foodItemSearchAliasRepository;

    @Autowired
    private FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Autowired
    private FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;

    @Autowired
    private FoodProductQualityIssueRepository foodProductQualityIssueRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void importsFiveHundredLocalizedGenericProductsWithinPostgresBudget() {
        FoodProductImportServiceImpl importService = new FoodProductImportServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemSearchAliasRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                new FoodProductQualityIssueTracker(foodProductQualityIssueRepository),
                org.mockito.Mockito.mock(com.grun.calorietracker.service.FoodProductEvidenceService.class),
                new ObjectMapper()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "postgres-benchmark-generic-products.csv",
                "text/csv",
                buildCsv().getBytes(StandardCharsets.UTF_8)
        );
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        Instant startedAt = Instant.now();

        FoodProductImportResultDto result = importService.importCsv(file, "postgres-performance-test");
        Duration elapsed = Duration.between(startedAt, Instant.now());
        long statementCount = statistics.getPrepareStatementCount();

        System.out.printf(
                "PRODUCT_POSTGRES_IMPORT_METRICS products=%d elapsedMs=%d statements=%d%n",
                PRODUCT_COUNT,
                elapsed.toMillis(),
                statementCount
        );

        assertEquals(PRODUCT_COUNT, result.getInsertedRows());
        assertEquals(0, result.getUpdatedRows());
        assertEquals(PRODUCT_COUNT, result.getSavedRows());
        assertEquals(0, result.getSkippedRows());
        assertTrue(
                statementCount <= 3125,
                "500-row PostgreSQL import executed " + statementCount + " SQL statements."
        );
        assertTrue(
                elapsed.compareTo(Duration.ofSeconds(8)) < 0,
                "500-row PostgreSQL import took " + elapsed.toMillis() + " ms."
        );
    }

    private String buildCsv() {
        StringBuilder csv = new StringBuilder("catalog_type,source_key,name,display_name_en,display_name_tr,")
                .append("alias_en,alias_tr,calories,protein,fat,carbs,market_region,preparation_state,")
                .append("serving_size_grams,serving_unit\n");
        for (int index = 1; index <= PRODUCT_COUNT; index++) {
            String suffix = String.format("%04d", index);
            csv.append("GENERIC_INGREDIENT,GLOBAL:POSTGRES_PERF:RAW:")
                    .append(suffix)
                    .append(",Postgres Staple ").append(suffix)
                    .append(",Postgres Staple ").append(suffix)
                    .append(",Postgres Temel Gida ").append(suffix)
                    .append(",postgres staple ").append(suffix)
                    .append(",postgres gida ").append(suffix)
                    .append(",100,5,2,18,GLOBAL,RAW,100,g\n");
        }
        return csv.toString();
    }
}
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class FoodProductImportPerformanceIntegrationTest {

    private static final int PRODUCT_COUNT = 100;

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

    @Autowired
    private EntityManager entityManager;

    @Test
    void importsOneHundredLocalizedGenericProductsWithinSqlBudget() {
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
                "synthetic-generic-products.csv",
                "text/csv",
                buildCsv().getBytes(StandardCharsets.UTF_8)
        );
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        FoodProductImportResultDto result = importService.importCsv(file, "performance-test");
        long statementCount = statistics.getPrepareStatementCount();

        assertEquals(PRODUCT_COUNT, result.getInsertedRows());
        assertEquals(0, result.getUpdatedRows());
        assertEquals(PRODUCT_COUNT, result.getSavedRows());
        assertEquals(0, result.getSkippedRows());
        long marketAvailabilityCount = ((Number) entityManager
                .createNativeQuery("select count(*) from food_item_market_regions")
                .getSingleResult()).longValue();
        long persistedRowCount = foodItemRepository.count()
                + foodItemLocalizationRepository.count()
                + foodItemSearchAliasRepository.count()
                + foodItemServingOptionRepository.count()
                + foodItemServingOptionLocalizationRepository.count()
                + foodProductQualityIssueRepository.count()
                + marketAvailabilityCount;
        assertTrue(
                statementCount <= persistedRowCount + 10,
                "100-row localized generic import executed " + statementCount
                        + " SQL statements for " + persistedRowCount + " persisted rows."
        );

        statistics.clear();
        FoodProductImportResultDto repeatedResult = importService.importCsv(file, "performance-test");
        long repeatedStatementCount = statistics.getPrepareStatementCount();

        assertEquals(0, repeatedResult.getInsertedRows());
        assertEquals(PRODUCT_COUNT, repeatedResult.getUpdatedRows());
        assertEquals(PRODUCT_COUNT, repeatedResult.getSavedRows());
        assertEquals(0, repeatedResult.getSkippedRows());
        assertTrue(
                repeatedStatementCount <= 115,
                "100-row idempotent re-import executed " + repeatedStatementCount + " SQL statements."
        );
    }

    private String buildCsv() {
        StringBuilder csv = new StringBuilder("catalog_type,source_key,name,display_name_en,display_name_tr,")
                .append("alias_en,alias_tr,calories,protein,fat,carbs,market_region,preparation_state,")
                .append("serving_size_grams,serving_unit\n");
        for (int index = 1; index <= PRODUCT_COUNT; index++) {
            String suffix = String.format("%03d", index);
            csv.append("GENERIC_INGREDIENT,GLOBAL:PERF:RAW:")
                    .append(suffix)
                    .append(",Synthetic Staple ").append(suffix)
                    .append(",Synthetic Staple ").append(suffix)
                    .append(",Sentetik Temel Gida ").append(suffix)
                    .append(",synthetic staple ").append(suffix)
                    .append(",sentetik gida ").append(suffix)
                    .append(",100,5,2,18,GLOBAL,RAW,100,g\n");
        }
        return csv.toString();
    }
}
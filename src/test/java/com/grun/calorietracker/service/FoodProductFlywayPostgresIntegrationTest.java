package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodEvidenceField;
import com.grun.calorietracker.service.support.FoodProductEvidenceBulkWriter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=true"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_PRODUCT_FLYWAY_POSTGRES", matches = "true")
class FoodProductFlywayPostgresIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FoodProductEvidenceBulkWriter evidenceBulkWriter;

    @Test
    void appliesCompleteMigrationChainWithoutPendingMigrations() {
        var info = flyway.info();

        assertNotNull(info.current());
        assertEquals(0, info.pending().length);
    }

    @Test
    void bulkEvidenceWriter_insertsLargeBatchAndRemainsIdempotent() {
        Long foodItemId = jdbcTemplate.queryForObject(
                "insert into food_items (name) values ('S10 bulk evidence fixture') returning id",
                Long.class
        );
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setId(foodItemId);
        List<FoodProductSourceEvidenceEntity> evidence = new ArrayList<>();
        for (int index = 0; index < 1001; index++) {
            FoodProductSourceEvidenceEntity value = new FoodProductSourceEvidenceEntity();
            value.setFoodItem(foodItem);
            value.setProvider(FoodDataSource.OPEN_FOOD_FACTS);
            value.setExternalId("S10:" + index);
            value.setFieldName(FoodEvidenceField.CALORIES);
            value.setNumericValue(100.0 + index);
            value.setBasis(FoodEvidenceBasis.PER_100_G);
            value.setConfidenceScore(70);
            value.setObservedAt(LocalDateTime.of(2026, 7, 15, 12, 0));
            value.setSourceVersion("S10_TEST");
            value.setFingerprint(String.format("%064x", index));
            evidence.add(value);
        }

        assertTrue(evidenceBulkWriter.supportsConflictSafeBulkInsert());
        assertEquals(1001, evidenceBulkWriter.insertIgnoringFingerprintConflicts(evidence));
        assertEquals(0, evidenceBulkWriter.insertIgnoringFingerprintConflicts(evidence));
        assertEquals(1001L, jdbcTemplate.queryForObject(
                "select count(*) from food_product_source_evidence where source_version = 'S10_TEST'",
                Long.class
        ));
    }
}
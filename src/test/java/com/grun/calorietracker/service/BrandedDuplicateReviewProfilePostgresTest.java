package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseRequestDto;
import com.grun.calorietracker.entity.FoodBrandedDuplicateDecisionEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import com.grun.calorietracker.repository.FoodBrandedDuplicateDecisionRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseMemberRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseAuditRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "GRUN_R6_POSTGRES_URL", matches = ".+")
class BrandedDuplicateReviewProfilePostgresTest {
    private Connection connection;
    private JdbcTemplate jdbc;
    private BrandedProductDuplicateAnalysisService service;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("GRUN_R6_POSTGRES_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r6_profile_test")) {
            throw new IllegalArgumentException("Owned local r6_profile_test database required");
        }
        connection = DriverManager.getConnection(url, "postgres", "");
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        assertThat(jdbc.queryForObject("SELECT current_setting('server_version_num')", String.class)).isEqualTo("180003");
        String schema = "r6_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        jdbc.execute("SET search_path TO " + schema);
        jdbc.execute("""
                CREATE TABLE food_brands(id bigint PRIMARY KEY,canonical_name text);
                CREATE TABLE food_items(id bigint PRIMARY KEY,brand_id bigint,brand text,name text,display_name text,
                  normalized_barcode text,source_key text,market_region text,preparation_state text,
                  serving_size_grams double precision,serving_unit text,nutrition_reference_unit text,
                  calories double precision,protein double precision,carbs double precision,fat double precision,
                  fiber double precision,sugar double precision,sodium double precision,cholesterol double precision,
                  potassium double precision,calcium double precision,iron double precision,magnesium double precision,zinc double precision,
                  vitamin_a double precision,vitamin_c double precision,vitamin_d double precision,vitamin_e double precision,
                  vitamin_b12 double precision,saturated_fat double precision,trans_fat double precision,sugar_alcohol double precision,
                  quality_score int,verification_status text,data_source text,catalog_type text,is_custom boolean,publication_status text,
                  usage_count bigint,search_selection_count bigint);
                INSERT INTO food_items(id,brand,name,normalized_barcode,source_key,market_region,preparation_state,
                  serving_size_grams,serving_unit,nutrition_reference_unit,calories,protein,carbs,fat,vitamin_c,
                  quality_score,verification_status,data_source,catalog_type,is_custom,publication_status,usage_count,search_selection_count)
                  VALUES(1,'Fixture Brand','Fixture Food','5449000000996','barcode:5449000000996','UK_IE','RAW',100,'g','PER_100G',100,1,2,3,5,
                    80,'VERIFIED','OPEN_FOOD_FACTS','BRANDED_PRODUCT',false,'PUBLISHED',0,0),
                    (2,'Fixture Brand','Fixture Food','5449000000996','barcode:5449000000996','UK_IE','RAW',100,'g','PER_100G',100,1,2,3,5,
                    80,'VERIFIED','OPEN_FOOD_FACTS','BRANDED_PRODUCT',false,'PUBLISHED',0,0);
                CREATE TABLE food_item_market_regions(food_item_id bigint,market_region text);
                CREATE TABLE food_item_source_categories(food_item_id bigint,category_tag text);
                CREATE TABLE food_item_serving_options(id bigint PRIMARY KEY,food_item_id bigint,unit_type text,quantity double precision,
                  gram_weight double precision,ml_volume double precision,is_default boolean,quality_status text,user_visible boolean,label text);
                CREATE TABLE food_item_serving_option_localizations(id bigint PRIMARY KEY,serving_option_id bigint,language text,label text);
                CREATE TABLE food_product_source_evidence(id bigint PRIMARY KEY,food_item_id bigint,field_name text,numeric_value double precision,
                  basis text,provider text,external_id text,source_version text,observed_at timestamp,fingerprint text);
                CREATE TABLE food_branded_duplicate_decisions(id bigint PRIMARY KEY,brand_key text,name_key text,decision text,
                  survivor_food_item_id bigint,candidate_fingerprint text,reason text,reviewed_by text,reviewed_at timestamp,version bigint);
                CREATE TABLE food_branded_duplicate_search_collapses(id bigint PRIMARY KEY,decision_id bigint,survivor_food_item_id bigint,
                  active boolean,candidate_fingerprint text,applied_by text,applied_at timestamp,reverted_by text,reverted_at timestamp);
                """);
        service = new BrandedProductDuplicateAnalysisService(jdbc);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    private String fingerprint() {
        return service.getExactGroup("legacy:fixturebrand", "fixturefood").candidateFingerprint();
    }

    @Test
    void allTwentyOneFieldsInvalidateReviewWithoutRequiringIdentityChange() {
        String before = fingerprint();
        for (String field : new String[]{"calories", "protein", "carbs", "fat", "fiber", "sugar", "sodium", "cholesterol", "potassium",
                "calcium", "iron", "magnesium", "zinc", "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_e", "vitamin_b12",
                "saturated_fat", "trans_fat", "sugar_alcohol"}) {
            Double value = jdbc.queryForObject("SELECT " + field + " FROM food_items WHERE id=1", Double.class);
            jdbc.update("UPDATE food_items SET " + field + "=? WHERE id=1", value == null ? 0.0 : value + .04);
            assertThat(fingerprint()).as(field + " review drift").isNotEqualTo(before);
            jdbc.update("UPDATE food_items SET " + field + "=? WHERE id=1", value);
            assertThat(fingerprint()).isEqualTo(before);
        }
    }

    @Test
    void massVolumePreparationAndAvailabilityInvalidateButTelemetryDoesNot() {
        String before = fingerprint();
        jdbc.update("UPDATE food_items SET usage_count=23,search_selection_count=45 WHERE id=1");
        assertThat(fingerprint()).isEqualTo(before);
        jdbc.update("UPDATE food_items SET nutrition_reference_unit='PER_100ML' WHERE id=1");
        assertThat(fingerprint()).isNotEqualTo(before);
        jdbc.update("UPDATE food_items SET nutrition_reference_unit='PER_100G',preparation_state='COOKED' WHERE id=1");
        assertThat(fingerprint()).isNotEqualTo(before);
        jdbc.update("UPDATE food_items SET preparation_state='RAW' WHERE id=1");
        jdbc.update("INSERT INTO food_item_market_regions VALUES(1,'TR')");
        assertThat(fingerprint()).isNotEqualTo(before);
    }

    @Test
    void portionsLabelsAndSourceVersionAreBoundWithNullDistinctFromZero() {
        String before = fingerprint();
        jdbc.update("INSERT INTO food_item_serving_options VALUES(10,1,'CUP',1,200,NULL,true,'VERIFIED',true,'1 cup')");
        String withOption = fingerprint();
        assertThat(withOption).isNotEqualTo(before);
        jdbc.update("UPDATE food_item_serving_options SET ml_volume=0 WHERE id=10");
        assertThat(fingerprint()).isNotEqualTo(withOption);
        jdbc.update("UPDATE food_item_serving_options SET ml_volume=NULL WHERE id=10");
        jdbc.update("INSERT INTO food_item_serving_option_localizations VALUES(11,10,'TR','1 bardak')");
        assertThat(fingerprint()).isNotEqualTo(withOption);
        jdbc.update("INSERT INTO food_product_source_evidence VALUES(20,1,'calories',100,'PER_100G','OFF','gtin:1','v1',now(),'body1')");
        String source = fingerprint();
        jdbc.update("UPDATE food_product_source_evidence SET source_version='v2' WHERE id=20");
        assertThat(fingerprint()).isNotEqualTo(source);
    }

    @Test
    void sourceTagsAndCanonicalBrandChangesInvalidateApproval() {
        String before = fingerprint();
        jdbc.update("INSERT INTO food_item_source_categories VALUES(1,'en:beverages')");
        assertThat(fingerprint()).isNotEqualTo(before);
        jdbc.update("INSERT INTO food_brands VALUES(10,'Fixture Brand')");
        jdbc.update("UPDATE food_items SET brand_id=10");
        String branded = service.getExactGroup("brand:10", "fixturefood").candidateFingerprint();
        jdbc.update("UPDATE food_brands SET canonical_name='Changed Canonical Brand' WHERE id=10");
        assertThat(service.getExactGroup("brand:10", "fixturefood").candidateFingerprint()).isNotEqualTo(branded);
    }

    @Test
    void staleMicroProfileIsVisibleInPagingAndCannotApplyPriorApproval() {
        String before = fingerprint();
        jdbc.update("INSERT INTO food_branded_duplicate_decisions VALUES(1,'legacy:fixturebrand','fixturefood','SURVIVOR_SELECTED',1,?,'Fixture approval','fixture',now(),0)", before);
        assertThat(service.getCandidates(0, 10, null, null).content().get(0).decisionStale()).isFalse();
        jdbc.update("UPDATE food_items SET vitamin_c=6 WHERE id=1");
        assertThat(service.getCandidates(0, 10, null, null).content().get(0).decisionStale()).isTrue();
        FoodBrandedDuplicateDecisionRepository decisions = mock(FoodBrandedDuplicateDecisionRepository.class);
        FoodBrandedDuplicateSearchCollapseRepository collapses = mock(FoodBrandedDuplicateSearchCollapseRepository.class);
        FoodBrandedDuplicateDecisionEntity decision = new FoodBrandedDuplicateDecisionEntity();
        FoodItemEntity survivor = new FoodItemEntity();
        survivor.setId(1L);
        decision.setDecision(BrandedDuplicateDecision.SURVIVOR_SELECTED);
        decision.setCandidateFingerprint(before);
        decision.setSurvivorFoodItem(survivor);
        when(decisions.findByBrandKeyAndNameKey("legacy:fixturebrand", "fixturefood")).thenReturn(Optional.of(decision));
        BrandedDuplicateSearchCollapseService collapse = new BrandedDuplicateSearchCollapseService(service, decisions, collapses,
                mock(FoodBrandedDuplicateSearchCollapseMemberRepository.class), mock(FoodBrandedDuplicateSearchCollapseAuditRepository.class),
                mock(FoodItemRepository.class));
        assertThatThrownBy(() -> collapse.apply(new BrandedDuplicateSearchCollapseRequestDto("legacy:fixturebrand", "fixturefood", before,
                "Fixture stale-profile attempt"), "fixture")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stale");
        verify(collapses, never()).saveAndFlush(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_items", Integer.class)).isEqualTo(2);
    }
}

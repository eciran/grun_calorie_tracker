package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.TypedNutrientEvidenceRequest;
import com.grun.calorietracker.dto.TypedSourceObservationRequest;
import com.grun.calorietracker.service.support.TypedSourceObservationContract;
import com.grun.calorietracker.service.support.TypedSourceObservationContractTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.exception.RequestConflictException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GRUN_RUN_TYPED_EVIDENCE_POSTGRES", matches = "true")
class TypedNutrientEvidencePostgresIntegrationTest {
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static Flyway flyway;
    static TypedNutrientEvidenceService service;
    static TransactionTemplate tx;
    static ObjectMapper json = new ObjectMapper();
    static long preMigrationEvidence;
    static String preMigrationRow;
    long id;
    String body;
    Instant observed;

    @BeforeAll static void migrateOwnedDatabaseOnly() {
        String url = System.getenv("GRUN_TYPED_EVIDENCE_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_typed_evidence_test"))
            throw new IllegalStateException("Only the owned disposable localhost typed-evidence DB is allowed");
        source = new DriverManagerDataSource(url, "r5_test", "");
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(true).target("310").load().migrate();
        long seedId = jdbc.queryForObject("INSERT INTO food_items(name,is_custom) VALUES ('V310 evidence preservation fixture',false) RETURNING id", Long.class);
        preMigrationEvidence = jdbc.queryForObject("""
                INSERT INTO food_typed_nutrient_evidence(food_item_id,provider,external_id,field_name,nutrient_id,
                    nutrient_number,source_unit,semantic,canonical_unit,basis,source_amount,source_json,source_sha256,
                    observed_at,before_json,before_sha256,fingerprint,reviewer_identity,approval_reason)
                VALUES (?,'USDA_FOODDATA','USDA_FOODDATA:fdc:172878','vitamin_a',1106,'320','ug','VITAMIN_A_RAE',
                    'MCG_RAE','PER_100_G',6,?,?,now(),'{}',?,?,'migration-test','Original V310 source review') RETURNING id
                """, Long.class, seedId, TypedNutrientEvidenceServiceTest.BODY,
                TypedNutrientEvidenceServiceTest.hash(TypedNutrientEvidenceServiceTest.BODY),
                TypedNutrientEvidenceServiceTest.hash("before310"), TypedNutrientEvidenceServiceTest.hash("seed310"));
        preMigrationRow = jdbc.queryForObject("SELECT to_jsonb(e)::text FROM food_typed_nutrient_evidence e WHERE id=?", String.class, preMigrationEvidence);
        flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(true).load();
        flyway.migrate();
        service = new TypedNutrientEvidenceService(jdbc, json, true);
        service.setExternalFoodImportFence(new com.grun.calorietracker.service.support.ExternalFoodImportFence(jdbc));
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @BeforeEach void fixture() {
        id = jdbc.queryForObject("""
                INSERT INTO food_items(name, data_source,catalog_type,publication_status,is_custom,
                    nutrition_basis,nutrition_reference_unit,vitamin_a,protein)
                VALUES ('Typed evidence disposable fixture','USDA_FOODDATA','GENERIC_INGREDIENT','PUBLISHED',false,
                    'SOURCE_REPORTED','PER_100G',6,20) RETURNING id
                """, Long.class);
        long fdc = 50000000 + id;
        jdbc.update("UPDATE food_items SET source_key = ? WHERE id = ?", "USDA_FOODDATA:fdc:" + fdc, id);
        body = TypedNutrientEvidenceServiceTest.BODY.replace("172878", Long.toString(fdc));
        observed = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        jdbc.update("""
                INSERT INTO food_product_source_evidence(food_item_id,provider,external_id,field_name,numeric_value,
                    basis,confidence_score,observed_at,created_at,fingerprint,source_version)
                VALUES (?,'USDA_FOODDATA',?,'VITAMIN_A',6,'PER_100_G',90,now(),now(),?,'GRUN_STANDARD')
                """, id, "USDA_FOODDATA:fdc:" + fdc, TypedNutrientEvidenceServiceTest.hash("legacy-" + id));
    }

    TypedNutrientEvidenceRequest request(String fingerprint) {
        return new TypedNutrientEvidenceRequest("vitamin_a", body, TypedNutrientEvidenceServiceTest.hash(body),
                observed, fingerprint, true, "Explicit disposable test-source review only");
    }
    TypedNutrientEvidenceRequest approved() {
        return request(service.preview(id, request(null)).currentFingerprint());
    }
    TypedNutrientEvidenceService.AppendResult append(TypedNutrientEvidenceRequest request) {
        return tx.execute(status -> service.append(id, request, "r5-test-reviewer"));
    }
    String parent() { return jdbc.queryForObject("SELECT to_jsonb(f)::text FROM food_items f WHERE id=?", String.class, id); }
    String legacy() { return jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(e) ORDER BY id)::text FROM food_product_source_evidence e WHERE food_item_id=?", String.class, id); }

    TypedSourceObservationRequest observation(String provider, String field, String sourceBody, String fp) {
        return new TypedSourceObservationRequest(TypedSourceObservationContract.VERSION, provider, field, sourceBody,
                TypedNutrientEvidenceServiceTest.hash(sourceBody), observed, fp, true, "Explicit source-only review, product values remain held");
    }
    TypedSourceObservationRequest approvedObservation(String provider, String field, String sourceBody) {
        return observation(provider, field, sourceBody, service.previewObservation(id, observation(provider, field, sourceBody, null)).currentFingerprint());
    }
    TypedNutrientEvidenceService.AppendResult appendObservation(TypedSourceObservationRequest request) {
        return tx.execute(status -> service.appendObservation(id, request, "r5-test-reviewer"));
    }

    @Test void additiveMigrationRetainsOriginalV310EvidenceExactly() {
        assertEquals(preMigrationRow, jdbc.queryForObject("""
                SELECT (to_jsonb(e)-'contract_version'-'observation_only'-'declared_source_amount'-'source_field')::text
                FROM food_typed_nutrient_evidence e WHERE id=?
                """, String.class, preMigrationEvidence));
        assertNull(jdbc.queryForObject("SELECT contract_version FROM food_typed_nutrient_evidence WHERE id=?", String.class, preMigrationEvidence));
        assertFalse(jdbc.queryForObject("SELECT observation_only FROM food_typed_nutrient_evidence WHERE id=?", Boolean.class, preMigrationEvidence));
    }

    @Test void sourceOnlyDifferenceIsImmutableIdempotentAndLeavesParentAndLegacyUntouched() {
        jdbc.update("UPDATE food_items SET vitamin_e=0 WHERE id=?", id);
        String sourceBody = TypedSourceObservationContractTest.USDA.replace("172878", Long.toString(50000000 + id));
        String before = parent(), old = legacy();
        var request = approvedObservation("USDA_FOODDATA", "vitamin_e", sourceBody);
        var a = appendObservation(request); var b = appendObservation(request);
        assertTrue(a.created()); assertFalse(b.created()); assertEquals(a.evidenceId(), b.evidenceId());
        assertEquals(List.of("CURRENT_NUMERIC_REVIEW_REQUIRED"), a.comparison().holds());
        assertEquals(before, parent()); assertEquals(old, legacy());
        assertTrue(service.compareStored(id, a.evidenceId()).currentBeforeImageMatches());
        assertTrue(service.compareStored(id, a.evidenceId()).comparison().observationOnly());
        assertThrows(Exception.class, () -> jdbc.update("UPDATE food_typed_nutrient_evidence SET declared_source_amount=1 WHERE id=?", a.evidenceId()));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM food_typed_nutrient_evidence WHERE id=?", a.evidenceId()));
    }

    @Test void offGramsAndMilligramsAreStoredSeparatelyWithoutMassVolumeOrParentConversion() {
        jdbc.update("""
                UPDATE food_items SET data_source='OPEN_FOOD_FACTS',source_key='barcode:4006381333931',
                catalog_type='BRANDED_PRODUCT',barcode='4006381333931',normalized_barcode='4006381333931',sodium=0 WHERE id=?
                """, id);
        String before = parent(), old = legacy();
        var result = appendObservation(approvedObservation("OPEN_FOOD_FACTS", "sodium",
                TypedSourceObservationContractTest.OFF.replace("00032056", "4006381333931")));
        var stored = jdbc.queryForMap("SELECT * FROM food_typed_nutrient_evidence WHERE id=?", result.evidenceId());
        assertEquals(0, new java.math.BigDecimal("5").compareTo((java.math.BigDecimal) stored.get("source_amount")));
        assertEquals(0, new java.math.BigDecimal("0.005").compareTo((java.math.BigDecimal) stored.get("declared_source_amount")));
        assertEquals("g", stored.get("source_unit")); assertEquals("MG", stored.get("canonical_unit"));
        assertEquals("PER_100_G", stored.get("basis")); assertNull(stored.get("nutrient_id"));
        assertEquals(before, parent()); assertEquals(old, legacy());
        assertTrue(service.compareStored(id, result.evidenceId()).currentBeforeImageMatches());
        assertFalse(result.comparison().nutritionWriteAllowed());
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO food_typed_nutrient_evidence(food_item_id,provider,external_id,field_name,nutrient_id,
                nutrient_number,source_unit,semantic,canonical_unit,basis,source_amount,source_json,source_sha256,
                observed_at,before_json,before_sha256,fingerprint,reviewer_identity,approval_reason,
                contract_version,observation_only,declared_source_amount,source_field)
                SELECT food_item_id,provider,external_id,field_name,nutrient_id,nutrient_number,source_unit,semantic,
                canonical_unit,basis,0.005,source_json,source_sha256,observed_at,before_json,before_sha256,?,reviewer_identity,
                approval_reason,contract_version,observation_only,declared_source_amount,source_field
                FROM food_typed_nutrient_evidence WHERE id=?
                """, TypedNutrientEvidenceServiceTest.hash("unconverted-" + id), result.evidenceId()));
    }

    @Test void sourceOnlyApprovalStillRebasesAliasesAndSerializesConcurrentAppenders() throws Exception {
        jdbc.update("UPDATE food_items SET vitamin_e=0 WHERE id=?", id);
        String sourceBody = TypedSourceObservationContractTest.USDA.replace("172878", Long.toString(50000000 + id));
        var old = approvedObservation("USDA_FOODDATA", "vitamin_e", sourceBody);
        jdbc.update("INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type) VALUES (?,'Fixture alias','fixture alias','EN','SYNONYM')", id);
        assertThrows(RequestConflictException.class, () -> appendObservation(old));
        var request = approvedObservation("USDA_FOODDATA", "vitamin_e", sourceBody);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> appendObservation(request)); var second = pool.submit(() -> appendObservation(request));
            var a = first.get(10, TimeUnit.SECONDS); var b = second.get(10, TimeUnit.SECONDS);
            assertEquals(a.evidenceId(), b.evidenceId()); assertNotEquals(a.created(), b.created());
        } finally { pool.shutdownNow(); }
    }

    @Test void databaseRejectsNullContractBypassAndWrongV2CanonicalForm() {
        jdbc.update("UPDATE food_items SET vitamin_e=0 WHERE id=?", id);
        String sourceBody = TypedSourceObservationContractTest.USDA.replace("172878", Long.toString(50000000 + id));
        long evidence = appendObservation(approvedObservation("USDA_FOODDATA", "vitamin_e", sourceBody)).evidenceId();
        for (String replacement : List.of("NULL", "'IU'")) {
            assertThrows(Exception.class, () -> jdbc.update("""
                    INSERT INTO food_typed_nutrient_evidence(food_item_id,provider,external_id,field_name,nutrient_id,
                    nutrient_number,source_unit,semantic,canonical_unit,basis,source_amount,source_json,source_sha256,
                    observed_at,before_json,before_sha256,fingerprint,reviewer_identity,approval_reason,
                    contract_version,observation_only,declared_source_amount,source_field)
                    SELECT food_item_id,provider,external_id,field_name,nutrient_id,nutrient_number,
                    """ + replacement + """
                    ,semantic,canonical_unit,basis,source_amount,source_json,source_sha256,observed_at,before_json,
                    before_sha256,?,reviewer_identity,approval_reason,contract_version,observation_only,
                    declared_source_amount,source_field FROM food_typed_nutrient_evidence WHERE id=?
                    """, TypedNutrientEvidenceServiceTest.hash("forged-" + replacement + id), evidence));
        }
    }

    @Test void sourceOnlyLateFailureRollsBackAndForeignProductCannotReadObservation() {
        jdbc.update("UPDATE food_items SET vitamin_e=0 WHERE id=?", id);
        String sourceBody = TypedSourceObservationContractTest.USDA.replace("172878", Long.toString(50000000 + id));
        var request = approvedObservation("USDA_FOODDATA", "vitamin_e", sourceBody);
        String before = parent(), old = legacy();
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            service.appendObservation(id, request, "reviewer"); throw new IllegalStateException("rollback fixture");
        }));
        assertTrue(service.history(id).isEmpty()); assertEquals(before, parent()); assertEquals(old, legacy());
        long evidence = appendObservation(request).evidenceId();
        assertThrows(ProductNotFoundException.class, () -> service.compareStored(id + 1, evidence));
    }

    @Test void archived20FactsAndFourMissingValuesRehearseWithoutCertificationOrPersistedCatalogChanges() throws Exception {
        Path previews = Path.of("outputs/r5-new-fields-20261015/previews-accepted/unapproved-source-previews.json");
        assertEquals("d0d36108b3449900e6d737df6a8bd4a6b0addbed6a3ccc60249604ae45d50d9d",
                TypedNutrientEvidenceServiceTest.hash(Files.readString(previews)));
        var prepared = json.readTree(Files.readString(previews));
        assertEquals(20, prepared.path("rows").size()); assertEquals(4, prepared.path("missingSourceHolds").size());
        var review = json.readTree(Files.readString(Path.of("outputs/r5-nutrient-differences-20261015/review-accepted/typed-difference-review.json")));
        var baselines = new HashMap<Long, JsonNode>();
        review.path("rows").forEach(row -> baselines.put(row.path("id").asLong(), row.path("before").path("food")));
        int count = 0;
        for (JsonNode proposal : prepared.path("rows")) {
            tx.execute(status -> {
                fixture();
                var sourceRequest = proposal.path("previewRequest");
                var food = baselines.get(proposal.path("productId").asLong());
                String field = sourceRequest.path("field").asText(), provider = sourceRequest.path("provider").asText();
                String sourceBody = sourceRequest.path("sourceJson").asText();
                var sourceFact = proposal.path("sourceFact");
                // Explicit disposable projection of separately reviewed classification/native basis.
                // Archived source time is not changed in the proposal; only this TEST request uses a synthetic fresh time.
                jdbc.update("UPDATE food_items SET data_source=?,source_key=?,catalog_type=?,source_data_type=NULL,brand=NULL,barcode=?,normalized_barcode=?,nutrition_reference_unit=? WHERE id=?",
                        provider, proposal.path("sourceKey").asText(), provider.equals("USDA_FOODDATA") ? "GENERIC_INGREDIENT" : "BRANDED_PRODUCT",
                        provider.equals("USDA_FOODDATA") ? null : proposal.path("sourceKey").asText().substring(8),
                        provider.equals("USDA_FOODDATA") ? null : proposal.path("sourceKey").asText().substring(8),
                        sourceFact.path("sourceBasis").asText(), id);
                assertTrue(Set.of("potassium", "vitamin_e", "vitamin_b12", "sodium").contains(field));
                jdbc.update("UPDATE food_items SET " + field + "=? WHERE id=?", food.path(field).decimalValue(), id);
                String before = parent(), old = legacy();
                var request = approvedObservation(provider, field, sourceBody);
                var appended = appendObservation(request);
                assertTrue(appended.created()); assertFalse(appendObservation(request).created());
                assertFalse(appended.comparison().legacyUnitAttested()); assertFalse(appended.comparison().nutritionWriteAllowed());
                assertTrue(appended.comparison().holds().contains("CURRENT_NUMERIC_REVIEW_REQUIRED"));
                assertEquals(0, new java.math.BigDecimal(sourceFact.path("canonicalSourceAmount").asText()).compareTo(appended.comparison().sourceAmount()));
                assertEquals(before, parent()); assertEquals(old, legacy());
                assertTrue(service.compareStored(id, appended.evidenceId()).currentBeforeImageMatches());
                assertFalse(sourceRequest.path("approved").asBoolean());
                status.setRollbackOnly(); return null;
            });
            count++;
        }
        // Missing source fiber never creates an approved observation or a synthetic zero.
        for (JsonNode missing : prepared.path("missingSourceHolds")) assertTrue(missing.path("previewRequest").isNull());
        assertEquals(20, count);
    }

    @Test void fullFlywayChainValidatesAndLegacyTableIsUntouched() {
        assertTrue(Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> "311".equals(migration.getVersion().getVersion())),
                "Required product migration must be applied; later unrelated migrations are allowed");
        assertEquals(0, flyway.info().pending().length); assertTrue(flyway.validateWithResult().validationSuccessful);
        assertFalse(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_name='food_product_source_evidence' AND column_name='source_unit')", Boolean.class));
    }
    @Test void appendAndRepeatRetainAllOldEvidenceAndParentBits() {
        String before = parent(), legacyBefore = legacy();
        var request = approved(); var first = append(request); var second = append(request);
        assertTrue(first.created()); assertFalse(second.created()); assertEquals(first.evidenceId(), second.evidenceId());
        assertEquals(before, parent()); assertEquals(legacyBefore, legacy());
        assertEquals(1, service.history(id).size());
        var comparison = service.compareStored(id, first.evidenceId());
        assertTrue(comparison.currentBeforeImageMatches()); assertTrue(comparison.comparison().holds().isEmpty());
        assertFalse(comparison.comparison().legacyUnitAttested());
    }
    @Test void noUpdateDeleteTruncateOrParentCascadeAllowed() {
        long evidence = append(approved()).evidenceId();
        assertThrows(Exception.class, () -> jdbc.update("UPDATE food_typed_nutrient_evidence SET source_amount=7 WHERE id=?", evidence));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM food_typed_nutrient_evidence WHERE id=?", evidence));
        assertThrows(Exception.class, () -> jdbc.execute("TRUNCATE food_typed_nutrient_evidence"));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM food_items WHERE id=?", id));
        assertEquals(1, service.history(id).size());
    }
    @Test void lateFailureRollsBackOnlyNewObservation() {
        var request = approved(); String old = legacy();
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            service.append(id, request, "r5-test-reviewer"); throw new IllegalStateException("late test failure");
        }));
        assertTrue(service.history(id).isEmpty()); assertEquals(old, legacy());
    }
    @Test void parentAndLegacyDriftBlockOldApproval() {
        var request = approved(); jdbc.update("UPDATE food_items SET protein=21 WHERE id=?", id);
        assertThrows(RequestConflictException.class, () -> append(request));
        jdbc.update("UPDATE food_items SET protein=20 WHERE id=?", id);
        assertThrows(Exception.class, () -> jdbc.update("UPDATE food_product_source_evidence SET confidence_score=91 WHERE food_item_id=?", id));
        jdbc.update("""
                INSERT INTO food_product_source_evidence(food_item_id,provider,external_id,field_name,numeric_value,
                    basis,confidence_score,observed_at,created_at,fingerprint,source_version)
                VALUES (?,'USDA_FOODDATA','new-source-observation','PROTEIN',20,'PER_100_G',90,now(),now(),?,'TEST')
                """, id, TypedNutrientEvidenceServiceTest.hash("legacy-added-" + id));
        assertThrows(RequestConflictException.class, () -> append(request));
        assertTrue(service.history(id).isEmpty());
    }
    @Test void aliasDriftMustInvalidatePreviousApproval() {
        jdbc.update("""
                INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type)
                VALUES (?,'Source name','source name','EN','SYNONYM')
                """, id);
        var request = approved();
        jdbc.update("UPDATE food_item_search_aliases SET active=false WHERE food_item_id=?", id);
        assertThrows(RequestConflictException.class, () -> append(request));
        assertTrue(service.history(id).isEmpty());
    }
    long option() {
        return jdbc.queryForObject("""
                INSERT INTO food_item_serving_options(food_item_id,label,unit_type,quantity,gram_weight,source,quality_status)
                VALUES (?,'Test portion','PIECE',1,20,'ADMIN','VERIFIED') RETURNING id
                """, Long.class, id);
    }
    void localization() {
        jdbc.update("INSERT INTO food_item_localizations(food_item_id,language,display_name) VALUES (?,'TR','Test food')", id);
    }
    @Test void localizationPortionDefaultAndNewPortionInvalidateApproval() {
        localization(); long option = option(); var first = approved();
        jdbc.update("UPDATE food_item_localizations SET display_name='Changed' WHERE food_item_id=?", id);
        assertThrows(RequestConflictException.class, () -> append(first));
        var second = approved(); jdbc.update("UPDATE food_item_serving_options SET is_default=true WHERE id=?", option);
        assertThrows(RequestConflictException.class, () -> append(second));
        var third = approved(); option();
        assertThrows(RequestConflictException.class, () -> append(third));
        assertTrue(service.history(id).isEmpty());
    }
    @Test void indirectPortionLabelsAreIncludedAndNoUserTextIsCopied() {
        long option = option();
        jdbc.update("INSERT INTO food_item_serving_option_localizations(serving_option_id,language,label) VALUES (?,'TR','Private test label')", option);
        var request = approved();
        jdbc.update("UPDATE food_item_serving_option_localizations SET label='Changed label' WHERE serving_option_id=?", option);
        assertThrows(RequestConflictException.class, () -> append(request));
        var result = append(approved());
        String stored = jdbc.queryForObject("SELECT before_json FROM food_typed_nutrient_evidence WHERE id=?", String.class, result.evidenceId());
        assertFalse(stored.contains("Changed label")); assertFalse(stored.contains("Private test label"));
        assertTrue(service.compareStored(id, result.evidenceId()).comparison().dependencies().complete());
    }
    @Test void directAndOptionOnlyHistoryReferencesInvalidateOldApproval() {
        long option = option(); var request = approved();
        long log = jdbc.queryForObject("INSERT INTO food_logs(food_id,serving_option_id,portion_size) VALUES (?,?,1) RETURNING id", Long.class, id, option);
        assertThrows(RequestConflictException.class, () -> append(request));
        var fresh = approved(); jdbc.update("UPDATE food_logs SET portion_size=2 WHERE id=?", log);
        assertThrows(RequestConflictException.class, () -> append(fresh));
        jdbc.update("UPDATE food_logs SET food_id=NULL WHERE id=?", log);
        var indirect = approved(); jdbc.update("UPDATE food_logs SET portion_size=3 WHERE id=?", log);
        assertThrows(RequestConflictException.class, () -> append(indirect));
        var result = append(approved());
        var dependencies = result.comparison().dependencies().relations();
        assertTrue(dependencies.stream().anyMatch(r -> r.path().contains("fk_food_logs_serving_option") && r.rowsExamined() == 1));
        assertEquals(3, jdbc.queryForObject("SELECT portion_size FROM food_logs WHERE id=?", Integer.class, log));
    }
    @Test void recipeAndTemplateIngredientLinksRemainImmutableAndAreFenced() {
        long user = jdbc.queryForObject("INSERT INTO users(name) VALUES ('Disposable recipe owner') RETURNING id", Long.class);
        long recipe = jdbc.queryForObject("INSERT INTO recipes(name) VALUES ('Private recipe test') RETURNING id", Long.class);
        long template = jdbc.queryForObject("INSERT INTO meal_templates(user_id,name,meal_type,created_at) VALUES (?,'Private template','DINNER',now()) RETURNING id", Long.class, user);
        long option = option(); var request = approved();
        long ingredient = jdbc.queryForObject("INSERT INTO recipe_ingredients(recipe_id,food_item_id,serving_option_id,portion_size) VALUES (?,?,?,1) RETURNING id", Long.class, recipe, id, option);
        assertThrows(RequestConflictException.class, () -> append(request));
        var second = approved();
        long item = jdbc.queryForObject("INSERT INTO meal_template_items(template_id,food_item_id,serving_option_id,portion_size) VALUES (?,?,?,2) RETURNING id", Long.class, template, id, option);
        assertThrows(RequestConflictException.class, () -> append(second));
        String before = jdbc.queryForObject("SELECT to_jsonb(r)::text FROM recipe_ingredients r WHERE id=?", String.class, ingredient);
        var result = append(approved());
        assertEquals(before, jdbc.queryForObject("SELECT to_jsonb(r)::text FROM recipe_ingredients r WHERE id=?", String.class, ingredient));
        assertEquals(2, jdbc.queryForObject("SELECT portion_size FROM meal_template_items WHERE id=?", Integer.class, item));
        String stored = jdbc.queryForObject("SELECT before_json FROM food_typed_nutrient_evidence WHERE id=?", String.class, result.evidenceId());
        assertFalse(stored.contains("Private recipe test")); assertFalse(stored.contains("Private template"));
    }
    @Test void rowBudgetOverflowCannotBeApprovedAsComplete() {
        jdbc.update("INSERT INTO food_logs(food_id,portion_size) SELECT ?,1 FROM generate_series(1,4097)", id);
        var comparison = service.preview(id, request(null));
        assertFalse(comparison.dependencies().complete());
        assertTrue(comparison.holds().contains("DEPENDENCY_REBASE_INCOMPLETE"));
        assertThrows(IllegalArgumentException.class, () -> append(request(comparison.currentFingerprint())));
        assertTrue(service.history(id).isEmpty());
    }
    @Test void newDeclaredFkPathIsDiscoveredAndSchemaDriftChangesFingerprint() {
        var request = approved();
        tx.execute(status -> {
            jdbc.execute("CREATE TABLE r5_f7_extra_link(id BIGSERIAL PRIMARY KEY,product_id BIGINT REFERENCES food_items(id),label text)");
            var current = service.preview(id, request(null));
            assertTrue(current.dependencies().layout().stream().anyMatch(r -> r.get("table_name").equals("r5_f7_extra_link")));
            assertThrows(RequestConflictException.class, () -> service.append(id, request, "r5-test-reviewer"));
            status.setRollbackOnly(); return null;
        });
    }
    @Test void missingOrUnvalidatedFkNeverBecomesCompleteCoverage() {
        for (String ddl : List.of("ALTER TABLE food_logs DROP CONSTRAINT fk_food_logs_food_item",
                "CREATE TABLE r5_f7_unvalidated(id BIGSERIAL PRIMARY KEY,product_id BIGINT); ALTER TABLE r5_f7_unvalidated ADD CONSTRAINT fk_unvalidated_product FOREIGN KEY(product_id) REFERENCES food_items(id) NOT VALID")) {
            tx.execute(status -> {
                jdbc.execute(ddl);
                var current = service.preview(id, request(null));
                assertFalse(current.dependencies().complete());
                assertTrue(current.holds().contains("DEPENDENCY_REBASE_INCOMPLETE"));
                assertThrows(IllegalArgumentException.class, () -> service.append(id, request(current.currentFingerprint()), "r5-test-reviewer"));
                status.setRollbackOnly(); return null;
            });
        }
        assertTrue(service.preview(id, request(null)).dependencies().complete());
    }
    @Test void canonicalResolutionChangesInvalidateApprovalWithoutChangingProductIdentity() {
        String before = parent(); var request = approved();
        jdbc.update("INSERT INTO food_canonical_resolutions(canonical_food_key,primary_food_item_id) VALUES (?,?)", "r5-f7-fixture-" + id, id);
        assertThrows(RequestConflictException.class, () -> append(request));
        var current = approved(); jdbc.update("DELETE FROM food_canonical_resolutions WHERE primary_food_item_id=?", id);
        assertThrows(RequestConflictException.class, () -> append(current)); assertEquals(before, parent());
    }
    @Test void historicalV1EvidenceIsComparedOnlyAtItsOriginalScopeAndNeverUpgraded() throws Exception {
        long existing = append(approved()).evidenceId();
        var row = jdbc.queryForMap("SELECT before_json FROM food_typed_nutrient_evidence WHERE id=?", existing);
        var historical = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(row.get("before_json").toString());
        historical.remove("dependencies"); historical.put("scope", "PARENT_AND_LEGACY_EVIDENCE_V1");
        String legacyJson = json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(json.convertValue(historical, Map.class));
        long copied = jdbc.queryForObject("""
                INSERT INTO food_typed_nutrient_evidence(food_item_id,provider,external_id,field_name,nutrient_id,
                    nutrient_number,source_unit,semantic,canonical_unit,basis,source_amount,source_json,source_sha256,
                    observed_at,before_json,before_sha256,fingerprint,reviewer_identity,approval_reason)
                SELECT food_item_id,provider,external_id,field_name,nutrient_id,nutrient_number,source_unit,semantic,
                    canonical_unit,basis,source_amount,source_json,source_sha256,observed_at,?,?,?,reviewer_identity,approval_reason
                FROM food_typed_nutrient_evidence WHERE id=? RETURNING id
                """, Long.class, legacyJson, TypedNutrientEvidenceServiceTest.hash(legacyJson), TypedNutrientEvidenceServiceTest.hash("historical-v1-" + id), existing);
        var comparison = service.compareStored(id, copied);
        assertTrue(comparison.currentBeforeImageMatches()); assertTrue(comparison.dependencyRebaseRequired());
        assertEquals("PARENT_AND_LEGACY_EVIDENCE_V1", comparison.storedSnapshotScope());
        assertEquals(legacyJson, jdbc.queryForObject("SELECT before_json FROM food_typed_nutrient_evidence WHERE id=?", String.class, copied));
    }
    @Test void dependencyEditsAndNewOptionConsumersWaitUntilEvidenceCommit() throws Exception {
        localization(); long option = option();
        jdbc.update("INSERT INTO food_item_serving_option_localizations(serving_option_id,language,label) VALUES (?,'TR','Test label')", option);
        var request = approved(); var pool = Executors.newFixedThreadPool(3);
        var waiting = new ArrayList<Future<?>>();
        try {
            tx.execute(status -> {
                service.append(id, request, "r5-test-reviewer");
                waiting.add(pool.submit(() -> jdbc.update("UPDATE food_item_localizations SET display_name='After commit' WHERE food_item_id=?", id)));
                waiting.add(pool.submit(() -> jdbc.update("INSERT INTO food_logs(serving_option_id,portion_size) VALUES (?,1)", option)));
                waiting.add(pool.submit(() -> jdbc.update("UPDATE food_item_serving_option_localizations SET label='After commit' WHERE serving_option_id=?", option)));
                for (var future : waiting) assertThrows(TimeoutException.class, () -> future.get(150, TimeUnit.MILLISECONDS));
                return null;
            });
            for (var future : waiting) future.get(10, TimeUnit.SECONDS);
            var history = service.history(id); long evidence = ((Number) history.get(0).get("id")).longValue();
            assertFalse(service.compareStored(id, evidence).currentBeforeImageMatches());
        } finally { pool.shutdownNow(); }
    }
    @Test void storedObservationReportsCurrentDriftWithoutOverwritingHistory() {
        long evidence = append(approved()).evidenceId();
        jdbc.update("UPDATE food_items SET vitamin_a=7 WHERE id=?", id);
        var comparison = service.compareStored(id, evidence);
        assertFalse(comparison.currentBeforeImageMatches());
        assertEquals("DIFFERS", comparison.comparison().scalarComparison());
        assertFalse(comparison.comparison().holds().contains("PRIOR_TYPED_OBSERVATION_CONFLICT_REQUIRES_REVIEW"));
        assertEquals(6, jdbc.queryForObject("SELECT source_amount FROM food_typed_nutrient_evidence WHERE id=?", Integer.class, evidence));
    }
    @Test void concurrentIdenticalAppendersProduceOneObservation() throws Exception {
        var request = approved(); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> append(request)); var second = pool.submit(() -> append(request));
            var a = first.get(10, TimeUnit.SECONDS); var b = second.get(10, TimeUnit.SECONDS);
            assertEquals(a.evidenceId(), b.evidenceId()); assertNotEquals(a.created(), b.created());
            assertEquals(1, service.history(id).size());
        } finally { pool.shutdownNow(); }
    }
    @Test void parentLockPreventsConcurrentMutationBeforeCommit() throws Exception {
        var request = approved(); var pool = Executors.newSingleThreadExecutor();
        try {
            tx.execute(status -> {
                service.append(id, request, "r5-test-reviewer");
                var waiting = pool.submit(() -> jdbc.update("UPDATE food_items SET protein=22 WHERE id=?", id));
                assertThrows(TimeoutException.class, () -> waiting.get(200, TimeUnit.MILLISECONDS));
                return null;
            });
            pool.shutdown(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(22, jdbc.queryForObject("SELECT protein FROM food_items WHERE id=?", Integer.class, id));
        } finally { pool.shutdownNow(); }
    }
    @Test void databaseContractRejectsUnitForgeryEvenOutsideService() {
        long evidence = append(approved()).evidenceId();
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO food_typed_nutrient_evidence(food_item_id,provider,external_id,field_name,nutrient_id,
                    nutrient_number,source_unit,semantic,canonical_unit,basis,source_amount,source_json,source_sha256,
                    observed_at,before_json,before_sha256,fingerprint,reviewer_identity,approval_reason)
                SELECT food_item_id,provider,external_id,field_name,nutrient_id,nutrient_number,'IU',semantic,
                    canonical_unit,basis,source_amount,source_json,source_sha256,observed_at,before_json,before_sha256,
                    ?,reviewer_identity,approval_reason FROM food_typed_nutrient_evidence WHERE id=?
                """, TypedNutrientEvidenceServiceTest.hash("invalid-" + evidence), evidence));
    }
    @Test void storedEvidenceCannotBeReadThroughAnotherProduct() {
        long evidence = append(approved()).evidenceId();
        assertThrows(ProductNotFoundException.class, () -> service.compareStored(id + 1, evidence));
    }
    @Test void archived87RehearsalIsNotCurrentAwsMeasurement() throws Exception {
        Path reviewPath = Path.of("outputs/r5-typed-reattestation-20261015/review-final/typed-reattestation-review.json");
        assertEquals("c0de32170ce2ab3bcf4c3674c787da3a0b5b284d75136c822bb9ab8b30881d95",
                TypedNutrientEvidenceServiceTest.hash(Files.readString(reviewPath)));
        JsonNode review = json.readTree(Files.readString(reviewPath));
        var foods = new HashMap<Long, JsonNode>();
        for (String folder : List.of("usda-fresh", "usda-large-fresh")) {
            try (var files = Files.list(Path.of("outputs/r5-source-portion-followup-20261015", folder))) {
                for (Path path : files.filter(p -> p.getFileName().toString().matches("batch-[0-9]+\\.json")).toList()) {
                    JsonNode batch = json.readTree(Files.readString(path));
                    for (JsonNode food : batch) foods.put(food.path("fdcId").asLong(), food);
                }
            }
        }
        JsonNode rows = review.path("rows");
        assertEquals(87, rows.size());
        Set<String> numericFields = Set.of("calories","protein","fat","carbs","fiber","sugar","sodium","potassium",
                "cholesterol","calcium","iron","magnesium","zinc","vitamin_a","vitamin_c","vitamin_d","vitamin_e",
                "vitamin_b12","saturated_fat","trans_fat","sugar_alcohol");
        int appended = tx.execute(status -> {
          int count = 0;
          for (JsonNode row : rows) {
            var food = row.path("before").path("food");
            // Deliberate disposable fixture projection of the separately reviewed R3 classification.
            // It is never represented as a fresh AWS before-image or an approval of source age.
            fixture();
            jdbc.update("UPDATE food_items SET source_key=? WHERE id=?", food.path("source_key").asText(), id);
            for (String field : numericFields) jdbc.update("UPDATE food_items SET " + field + "=? WHERE id=?",
                    food.path(field).isNumber() ? food.path(field).doubleValue() : null, id);
            long fdc = Long.parseLong(food.path("source_key").asText().replace("USDA_FOODDATA:fdc:", ""));
            body = Objects.requireNonNull(foods.get(fdc)).toString();
            // Synthetic fresh time tests the implementation, not the archived receipt's freshness.
            observed = Instant.now().minusSeconds(60);
            String before = parent(), old = legacy();
            var request = approved(); var result = append(request);
            assertTrue(result.created()); assertFalse(append(request).created());
            assertEquals(before, parent()); assertEquals(old, legacy());
            assertTrue(service.compareStored(id, result.evidenceId()).currentBeforeImageMatches());
            count++;
          }
          // Re-running the rehearsal cannot leave real source keys or new attestations behind.
          status.setRollbackOnly();
          return count;
        });
        assertEquals(87, appended);
    }
}

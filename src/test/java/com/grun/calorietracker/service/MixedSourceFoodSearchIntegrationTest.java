package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.CatalogPublicationServiceImpl;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import com.grun.calorietracker.service.impl.FoodProductImportServiceImpl;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.PostgresFoodSearchCandidateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false"})
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_MIXED_SOURCE_SEARCH", matches = "true")
class MixedSourceFoodSearchIntegrationTest {
    @Autowired FoodItemRepository foods;
    @Autowired FoodItemLocalizationRepository labels;
    @Autowired FoodItemSearchAliasRepository aliases;
    @Autowired FoodItemServingOptionRepository portions;
    @Autowired FoodItemServingOptionLocalizationRepository portionLabels;
    @Autowired JdbcTemplate jdbc;
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    JsonNode cohort;
    JsonNode fixture;
    Map<String, FoodItemEntity> seeded;
    FoodItemServiceImpl search;
    FoodProductQualityIssueTracker tracker;

    @BeforeEach void projectPinnedSearchOnlySources() throws Exception {
        try (var in = new ClassPathResource(judgmentResource()).getInputStream()) {
            fixture = mapper.readTree(in);
        }
        String value = System.getenv(corpusEnvironment());
        assertNotNull(value, "Explicit private source projection required");
        Path path = Path.of(value).toRealPath();
        assertTrue(path.startsWith(Path.of("outputs").toRealPath()));
        assertEquals(fixture.path("cohortSha256").asText(), hash(path));
        cohort = mapper.readTree(path.toFile());
        assertEquals(expectedRecords(), cohort.path("foods").size());
        for (var input : cohort.path("inputs")) {
            Path original = Path.of(input.path("path").asText()).toRealPath();
            assertTrue(original.startsWith(Path.of("outputs").toRealPath()));
            assertEquals(input.path("sha256").asText(), hash(original));
        }
        seeded = new LinkedHashMap<>();
        for (var source : cohort.path("foods")) {
            var item = mapper.treeToValue(source.path("food"), FoodItemEntity.class);
            assertNull(item.getId());
            if (source.path("origin").asText().equals("COFID_PENDING_CONTROL")) {
                assertEquals("PENDING_REVIEW", source.path("sourceApproval").asText());
                assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, item.getPublicationStatus());
            }
            foods.saveAndFlush(item);
            assertNull(seeded.put(item.getSourceKey(), item));
            source.path("labels").fields().forEachRemaining(entry -> {
                var label = new FoodItemLocalizationEntity(); label.setFoodItem(item);
                label.setLanguage(PreferredLanguage.valueOf(entry.getKey()));
                label.setDisplayName(entry.getValue().asText()); label.setShortDisplayName(entry.getValue().asText());
                labels.saveAndFlush(label);
            });
            for (var text : source.path("aliases")) {
                var alias = new FoodItemSearchAliasEntity(); alias.setFoodItem(item);
                alias.setAlias(text.path("text").asText());
                alias.setNormalizedAlias(FoodProductNormalizationRules.normalizeSearchAlias(alias.getAlias()));
                alias.setLanguage(PreferredLanguage.valueOf(text.path("language").asText()));
                alias.setAliasType(FoodSearchAliasType.ADMIN_MANUAL);
                alias.setSource("controlled-archived-label-projection-not-live-certification");
                aliases.saveAndFlush(alias);
            }
        }
        tracker = mock(FoodProductQualityIssueTracker.class);
        search = new FoodItemServiceImpl(foods, labels, portions, portionLabels,
                mock(OpenFoodFactsService.class), tracker, mock(FoodProductEvidenceService.class), mock(CatalogPublicationService.class));
    }

    @Test void mixedSourceJudgmentsHoldWithoutMergingDistinctBarcodes() throws Exception {
        // Only this disposable fixture simulates approval. Pending source artifacts stay unchanged.
        var publisher = new CatalogPublicationServiceImpl(foods, mock(AdminAuditService.class));
        int evaluations = 0;
        for (boolean indexed : List.of(false, true)) {
            var provider = new PostgresFoodSearchCandidateProvider(jdbc);
            ReflectionTestUtils.setField(provider, "postgres", true);
            ReflectionTestUtils.setField(search, "postgresFoodSearchCandidateProvider", indexed ? provider : null);
            for (var judgment : fixture.path("judgments")) {
                boolean simulateApproval = judgment.path("fixtureApprovalSimulation").asBoolean();
                for (var item : seeded.values()) {
                    if (item.getDataSource() == FoodDataSource.COFID) {
                        if (simulateApproval) publisher.publish(item.getId(), "test-only-approval@example.invalid", "Controlled search contract, NOT source/release approval", null);
                        else item.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
                    }
                }
                foods.flush();
                String before = factFingerprint();
                var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(judgment.path("q").asText());
                criteria.setMarketRegion(MarketRegion.valueOf(judgment.path("region").asText()));
                criteria.setPreferredLanguage(PreferredLanguage.valueOf(judgment.path("language").asText()));
                if (judgment.has("catalogType")) criteria.setCatalogType(FoodCatalogType.valueOf(judgment.path("catalogType").asText()));
                if (judgment.has("preparationState")) criteria.setPreparationState(FoodPreparationState.valueOf(judgment.path("preparationState").asText()));
                var result = search.searchFoodItems(criteria, 0, 25);
                Set<String> expected = strings(judgment.path("expectedKeys"));
                String id = judgment.path("id").asText() + " indexed=" + indexed;
                assertEquals(expected, new HashSet<>(result.getContent().stream().map(p -> p.getSourceKey()).toList()), id);
                assertEquals(expected.size(), result.getTotalElements(), id);
                if (!expected.isEmpty()) assertTrue(strings(judgment.path("acceptableTopKeys")).contains(result.getContent().get(0).getSourceKey()), id);
                assertEquals(result.getContent().size(), result.getContent().stream().map(p -> p.getId()).distinct().count(), id);
                for (var product : result.getContent()) {
                    var original = seeded.get(product.getSourceKey());
                    assertEquals(original.getBarcode(), product.getBarcode(), id);
                    assertEquals(original.getNutritionReferenceUnit(), product.getNutritionReferenceUnit(), id);
                    assertEquals(original.getCalories(), product.getCalories(), id);
                }
                assertEquals(before, factFingerprint(), id);
                evaluations++;
            }
        }
        assertEquals(expectedRecords(), foods.count());
        System.out.println("R7_MIXED_SOURCE_ORACLE records=" + expectedRecords() + " judgments=" + fixture.path("judgments").size()
                + " evaluations=" + evaluations + " cofidApproval=testOnly sourceCertification=false");
    }

    @Test void cofidPendingArtifactsCannotOverwriteOrApproveStoredCandidates() throws Exception {
        String before = factFingerprint();
        var importer = new FoodProductImportServiceImpl(foods, labels, aliases, portions, portionLabels,
                tracker, mock(FoodProductEvidenceService.class), mapper);
        var file = new MockMultipartFile("file", "cofid-pending.csv", "text/csv",
                cohort.path("cofidPendingCsv").asText().getBytes(StandardCharsets.UTF_8));
        var result = importer.importCsv(file, "controlled-fixture@example.invalid");
        assertEquals(0, result.getSavedRows()); assertEquals(6, result.getSkippedRows());
        assertEquals(6, result.getErrors().size());
        assertTrue(result.getErrors().stream().allMatch(error -> error.getReason().contains("Review-only source artifacts")));
        foods.flush();
        assertEquals(before, factFingerprint()); assertEquals(expectedRecords(), foods.count());
        assertTrue(seeded.values().stream().filter(p -> p.getDataSource() == FoodDataSource.COFID)
                .allMatch(p -> p.getPublicationStatus() == CatalogPublicationStatus.INTERNAL_REVIEW));
    }

    String judgmentResource() { return "r7-mixed-source-judgments-v1.json"; }
    String corpusEnvironment() { return "GRUN_R7_MIXED_SOURCE_CORPUS"; }
    int expectedRecords() { return 35; }

    private String factFingerprint() throws Exception {
        List<JsonNode> facts = new ArrayList<>();
        for (var item : foods.findAll().stream().sorted(Comparator.comparing(FoodItemEntity::getSourceKey)).toList()) {
            var value = mapper.valueToTree(item);
            facts.add(value);
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(facts)));
    }
    private static Set<String> strings(JsonNode array) {
        Set<String> values = new LinkedHashSet<>(); array.forEach(value -> assertTrue(values.add(value.asText()))); return values;
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}

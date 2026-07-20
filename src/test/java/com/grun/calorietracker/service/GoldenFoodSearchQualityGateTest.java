package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodCanonicalResolutionEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodCanonicalResolutionRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import com.grun.calorietracker.service.impl.FoodProductImportServiceImpl;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
class GoldenFoodSearchQualityGateTest {

    private static final double MIN_TOP_ONE_RATE = 0.95;
    private static final double MIN_RECALL_AT_THREE = 0.99;
    private static final long MAX_P95_MILLISECONDS = 300;
    private static final int MINIMUM_GOLDEN_CASES = 200;

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
    private FoodCanonicalResolutionRepository foodCanonicalResolutionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void goldenSearchDataset_meetsReadinessThresholds() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GoldenFixture fixture = readFixture(objectMapper);
        assertFixtureContract(fixture);

        FoodProductQualityIssueTracker issueTracker = new FoodProductQualityIssueTracker(foodProductQualityIssueRepository);
        FoodProductImportServiceImpl importService = new FoodProductImportServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemSearchAliasRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                issueTracker,
                Mockito.mock(FoodProductEvidenceService.class),
                objectMapper
        );
        importGoldenCatalog(importService);
        seedMarketProducts();
        seedGuardrailProducts();
        resolveCanonicalBananaDuplicate();
        entityManager.flush();
        entityManager.clear();

        FoodItemServiceImpl foodItemService = new FoodItemServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                Mockito.mock(OpenFoodFactsService.class),
                issueTracker,
                Mockito.mock(FoodProductEvidenceService.class)
        );

        GoldenMetrics metrics = evaluate(fixture, foodItemService);
        writeReport(objectMapper, fixture, metrics);

        assertEquals(0, metrics.criticalZeroResults(), failureMessage("critical zero results", metrics));
        assertEquals(0, metrics.forbiddenResultCount(), failureMessage("forbidden results", metrics));
        assertEquals(0, metrics.duplicateGenericResultCount(), failureMessage("duplicate generic results", metrics));
        assertEquals(0, metrics.displayNameFailureCount(), failureMessage("localized display names", metrics));
        assertEquals(0, metrics.servingFailureCount(), failureMessage("localized serving options", metrics));
        assertTrue(metrics.topOneRate() >= MIN_TOP_ONE_RATE, failureMessage("Top-1 rate", metrics));
        assertTrue(metrics.recallAtThree() >= MIN_RECALL_AT_THREE, failureMessage("Recall@3", metrics));
        assertTrue(metrics.p95Milliseconds() <= MAX_P95_MILLISECONDS, failureMessage("search p95", metrics));
    }

    private GoldenFixture readFixture(ObjectMapper objectMapper) throws Exception {
        try (InputStream input = new ClassPathResource("golden-food-search-v1.json").getInputStream()) {
            return objectMapper.readValue(input, GoldenFixture.class);
        }
    }

    private void assertFixtureContract(GoldenFixture fixture) {
        assertEquals("golden-food-search-v1", fixture.version());
        assertTrue(fixture.cases().size() >= MINIMUM_GOLDEN_CASES);
        assertEquals(MIN_TOP_ONE_RATE, fixture.thresholds().top1Rate());
        assertEquals(MIN_RECALL_AT_THREE, fixture.thresholds().recallAt3());
        assertEquals(MAX_P95_MILLISECONDS, fixture.thresholds().p95Milliseconds());
        assertTrue(fixture.cases().stream().anyMatch(item -> item.tags().contains("SPELLING")));
        assertTrue(fixture.cases().stream().anyMatch(item -> item.tags().contains("ASCII_TR")));
        assertTrue(fixture.cases().stream().anyMatch(item -> item.tags().contains("NATIVE_TR")));
        assertTrue(fixture.cases().stream().map(GoldenCase::marketRegion).collect(java.util.stream.Collectors.toSet())
                .containsAll(Set.of(MarketRegion.GLOBAL, MarketRegion.UK_IE, MarketRegion.EU, MarketRegion.TR)));
    }

    private void importGoldenCatalog(FoodProductImportServiceImpl importService) throws Exception {
        byte[] csv = new ClassPathResource("food-generic-staples-curated-seed.csv").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile("file", "golden-catalog.csv", "text/csv", csv);
        var result = importService.importCsv(file, "golden-search-gate");
        assertEquals(14, result.getInsertedRows());
        assertEquals(0, result.getSkippedRows());
    }

    private GoldenMetrics evaluate(GoldenFixture fixture, FoodItemServiceImpl foodItemService) {
        int topOneHits = 0;
        int recallAtThreeHits = 0;
        int criticalZeroResults = 0;
        int forbiddenResultCount = 0;
        int duplicateGenericResultCount = 0;
        int displayNameFailureCount = 0;
        int servingFailureCount = 0;
        int distinctSourceKeyTotal = 0;
        int distinctCatalogTypeTotal = 0;
        double reciprocalRankTotal = 0.0;
        List<Long> durations = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (GoldenCase goldenCase : fixture.cases()) {
            FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(goldenCase.query());
            criteria.setPreferredLanguage(goldenCase.language());
            criteria.setMarketRegion(goldenCase.marketRegion());
            criteria.setPreparationState(goldenCase.preparationState());

            long startedAt = System.nanoTime();
            FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 10);
            durations.add((System.nanoTime() - startedAt) / 1_000_000L);
            List<FoodProductDto> products = result.getContent();
            distinctSourceKeyTotal += Math.toIntExact(products.stream()
                    .map(FoodProductDto::getSourceKey)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count());
            distinctCatalogTypeTotal += Math.toIntExact(products.stream()
                    .map(FoodProductDto::getCatalogType)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count());

            if (goldenCase.critical() && products.isEmpty()) {
                criticalZeroResults++;
                recordFailure(failures, goldenCase, "zero results");
                continue;
            }

            List<String> sourceKeys = products.stream().map(FoodProductDto::getSourceKey).toList();
            int rank = findRank(sourceKeys, goldenCase.acceptableTop3SourceKeys());
            if (!sourceKeys.isEmpty() && goldenCase.expectedTopSourceKey().equals(sourceKeys.get(0))) {
                topOneHits++;
            } else {
                recordFailure(failures, goldenCase, "top-1=" + firstOrEmpty(sourceKeys));
            }
            if (rank >= 1 && rank <= 3) {
                recallAtThreeHits++;
                reciprocalRankTotal += 1.0 / rank;
            } else {
                recordFailure(failures, goldenCase, "expected result missing from top 3");
            }

            Set<String> forbiddenKeys = new HashSet<>(fixture.defaultForbiddenSourceKeys());
            long forbidden = sourceKeys.stream().filter(forbiddenKeys::contains).count();
            forbiddenResultCount += Math.toIntExact(forbidden);
            if (forbidden > 0) {
                recordFailure(failures, goldenCase, "forbidden product visible");
            }

            int duplicateCount = countDuplicateGenericResults(products);
            duplicateGenericResultCount += duplicateCount;
            if (duplicateCount > 0) {
                recordFailure(failures, goldenCase, "duplicate canonical generic result");
            }

            if (!products.isEmpty() && goldenCase.expectedDisplayName() != null
                    && !goldenCase.expectedDisplayName().equals(products.get(0).getProductName())) {
                displayNameFailureCount++;
                recordFailure(failures, goldenCase, "display=" + products.get(0).getProductName());
            }
            if (!products.isEmpty() && goldenCase.expectedServingLabel() != null
                    && products.get(0).getServingOptions().stream()
                    .noneMatch(option -> goldenCase.expectedServingLabel().equals(option.getLabel()))) {
                servingFailureCount++;
                recordFailure(failures, goldenCase, "localized serving missing");
            }
        }

        int total = fixture.cases().size();
        durations.sort(Comparator.naturalOrder());
        return new GoldenMetrics(
                total,
                topOneHits,
                recallAtThreeHits,
                topOneHits / (double) total,
                recallAtThreeHits / (double) total,
                reciprocalRankTotal / total,
                criticalZeroResults,
                forbiddenResultCount,
                duplicateGenericResultCount,
                displayNameFailureCount,
                servingFailureCount,
                distinctSourceKeyTotal / (double) total,
                distinctCatalogTypeTotal / (double) total,
                percentile95(durations),
                List.copyOf(failures)
        );
    }

    private int findRank(List<String> actualSourceKeys, List<String> acceptableSourceKeys) {
        for (int index = 0; index < Math.min(3, actualSourceKeys.size()); index++) {
            if (acceptableSourceKeys.contains(actualSourceKeys.get(index))) {
                return index + 1;
            }
        }
        return -1;
    }

    private int countDuplicateGenericResults(List<FoodProductDto> products) {
        Map<String, Integer> canonicalCounts = new HashMap<>();
        for (FoodProductDto product : products) {
            if (product.getCatalogType() == FoodCatalogType.GENERIC_INGREDIENT && product.getCanonicalFoodKey() != null) {
                canonicalCounts.merge(product.getCanonicalFoodKey(), 1, Integer::sum);
            }
        }
        return canonicalCounts.values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
    }

    private long percentile95(List<Long> sortedDurations) {
        int index = Math.max(0, (int) Math.ceil(sortedDurations.size() * 0.95) - 1);
        return sortedDurations.get(index);
    }

    private void recordFailure(List<String> failures, GoldenCase goldenCase, String reason) {
        if (failures.size() < 30) {
            failures.add(goldenCase.id() + " [" + goldenCase.query() + "]: " + reason);
        }
    }

    private String firstOrEmpty(List<String> values) {
        return values.isEmpty() ? "<empty>" : values.get(0);
    }

    private String failureMessage(String metric, GoldenMetrics metrics) {
        return metric + " gate failed. Metrics=" + metrics.summary() + ", samples=" + metrics.failures();
    }

    private void writeReport(ObjectMapper objectMapper, GoldenFixture fixture, GoldenMetrics metrics) throws Exception {
        Path reportPath = Path.of("target", "reports", "golden-food-search-v1-report.json");
        Files.createDirectories(reportPath.getParent());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("fixtureVersion", fixture.version());
        report.put("generatedAt", Instant.now().toString());
        report.put("thresholds", fixture.thresholds());
        report.put("metrics", metrics);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        System.out.println("GOLDEN_SEARCH_REPORT " + metrics.summary());
    }

    private void seedMarketProducts() {
        createMarketProduct("UK_IE:GOLDEN:market_milk", MarketRegion.UK_IE, "UK Market Milk", "Birleşik Krallık Pazar Sütü");
        createMarketProduct("EU:GOLDEN:market_milk", MarketRegion.EU, "EU Market Milk", "AB Pazar Sütü");
        createMarketProduct("TR:GOLDEN:market_milk", MarketRegion.TR, "TR Market Milk", "Pazar Sütü");
        createMarketProduct("GLOBAL:GOLDEN:market_milk", MarketRegion.GLOBAL, "Global Market Milk", "Global Pazar Sütü");
    }

    private void createMarketProduct(String sourceKey, MarketRegion region, String englishName, String turkishName) {
        FoodItemEntity product = product("Market Milk", sourceKey, VerificationStatus.VERIFIED);
        product.setMarketRegion(region);
        product.setPreparationState(FoodPreparationState.RAW);
        product.setDisplayName(englishName);
        product.setShortDisplayName(englishName);
        foodItemRepository.save(product);
        saveLocalization(product, PreferredLanguage.EN, englishName);
        saveLocalization(product, PreferredLanguage.TR, turkishName);
        saveAlias(product, PreferredLanguage.EN, "market milk");
        saveAlias(product, PreferredLanguage.EN, "regional milk");
        saveAlias(product, PreferredLanguage.TR, "pazar sütü");
        saveAlias(product, PreferredLanguage.TR, "pazar sutu");
    }

    private void seedGuardrailProducts() {
        FoodItemEntity rejected = product("Banana", "GOLDEN:FORBIDDEN:REJECTED", VerificationStatus.REJECTED);
        rejected.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        foodItemRepository.save(rejected);
        saveAlias(rejected, PreferredLanguage.EN, "banana");

        FoodItemEntity blocked = product("Milk", "GOLDEN:FORBIDDEN:BLOCKED", VerificationStatus.VERIFIED);
        blocked.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        foodItemRepository.save(blocked);
        saveAlias(blocked, PreferredLanguage.EN, "milk");
        FoodProductQualityIssueEntity issue = new FoodProductQualityIssueEntity();
        issue.setFoodItem(blocked);
        issue.setIssueType(FoodProductQualityIssue.SUSPICIOUS_MACROS);
        issue.setIdentifier(blocked.getSourceKey());
        issue.setReason("Golden gate blocking issue");
        issue.setResolved(false);
        foodProductQualityIssueRepository.save(issue);

        createDistraction("Banana Bread", "GOLDEN:DISTRACTION:BANANA_BREAD");
        createDistraction("Rice Flour", "GOLDEN:DISTRACTION:RICE_FLOUR");
        createDistraction("Chicken Broth", "GOLDEN:DISTRACTION:CHICKEN_BROTH");
        createDistraction("Milk Chocolate", "GOLDEN:DISTRACTION:MILK_CHOCOLATE");
        createDistraction("Broccoli Soup", "GOLDEN:DISTRACTION:BROCCOLI_SOUP");
    }

    private void createDistraction(String name, String sourceKey) {
        FoodItemEntity product = product(name, sourceKey, VerificationStatus.VERIFIED);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setQualityScore(100);
        product.setUsageCount(1_000L);
        foodItemRepository.save(product);
    }

    private void resolveCanonicalBananaDuplicate() {
        FoodItemEntity primary = foodItemRepository.findBySourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:banana")
                .orElseThrow();
        FoodItemEntity duplicate = product("Bananas raw duplicate", "GOLDEN:DUPLICATE:BANANA", VerificationStatus.VERIFIED);
        duplicate.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        duplicate.setCanonicalFoodKey(primary.getCanonicalFoodKey());
        duplicate.setPreparationState(FoodPreparationState.RAW);
        foodItemRepository.save(duplicate);
        saveAlias(duplicate, PreferredLanguage.EN, "banana");

        FoodCanonicalResolutionEntity resolution = new FoodCanonicalResolutionEntity();
        resolution.setCanonicalFoodKey(primary.getCanonicalFoodKey());
        resolution.setPrimaryFoodItem(primary);
        resolution.setResolvedBy("golden-search-gate");
        foodCanonicalResolutionRepository.save(resolution);
    }

    private FoodItemEntity product(String name, String sourceKey, VerificationStatus status) {
        FoodItemEntity product = new FoodItemEntity();
        product.setName(name);
        product.setSourceKey(sourceKey);
        product.setDisplayName(name);
        product.setShortDisplayName(name);
        product.setVerificationStatus(status);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setMarketRegion(MarketRegion.GLOBAL);
        product.setCalories(100.0);
        product.setProtein(5.0);
        product.setFat(2.0);
        product.setCarbs(15.0);
        product.setQualityScore(80);
        product.setUsageCount(0L);
        product.setIsCustom(false);
        return product;
    }

    private void saveLocalization(FoodItemEntity product, PreferredLanguage language, String displayName) {
        FoodItemLocalizationEntity localization = new FoodItemLocalizationEntity();
        localization.setFoodItem(product);
        localization.setLanguage(language);
        localization.setDisplayName(displayName);
        localization.setShortDisplayName(displayName);
        localization.setSource("golden-search-gate");
        localization.setActive(true);
        foodItemLocalizationRepository.save(localization);
    }

    private void saveAlias(FoodItemEntity product, PreferredLanguage language, String aliasValue) {
        FoodItemSearchAliasEntity alias = new FoodItemSearchAliasEntity();
        alias.setFoodItem(product);
        alias.setAlias(aliasValue);
        alias.setNormalizedAlias(FoodProductNormalizationRules.normalizeSearchAlias(aliasValue));
        alias.setLanguage(language);
        alias.setAliasType(FoodSearchAliasType.SYNONYM);
        alias.setSource("golden-search-gate");
        alias.setActive(true);
        foodItemSearchAliasRepository.save(alias);
    }

    private record GoldenFixture(
            String version,
            String description,
            List<String> defaultForbiddenSourceKeys,
            GoldenThresholds thresholds,
            List<GoldenCase> cases
    ) {
    }

    private record GoldenThresholds(
            double top1Rate,
            double recallAt3,
            int criticalZeroResults,
            int forbiddenResults,
            int duplicateGenericResults,
            long p95Milliseconds
    ) {
    }

    private record GoldenCase(
            String id,
            String query,
            PreferredLanguage language,
            MarketRegion marketRegion,
            FoodPreparationState preparationState,
            String expectedTopSourceKey,
            List<String> acceptableTop3SourceKeys,
            boolean critical,
            String expectedDisplayName,
            String expectedServingLabel,
            List<String> tags
    ) {
    }

    private record GoldenMetrics(
            int totalCases,
            int topOneHits,
            int recallAtThreeHits,
            double topOneRate,
            double recallAtThree,
            double meanReciprocalRankAtThree,
            int criticalZeroResults,
            int forbiddenResultCount,
            int duplicateGenericResultCount,
            int displayNameFailureCount,
            int servingFailureCount,
            double averageDistinctSourceKeys,
            double averageDistinctCatalogTypes,
            long p95Milliseconds,
            List<String> failures
    ) {
        private String summary() {
            return "cases=" + totalCases
                    + ", top1=" + String.format(java.util.Locale.ROOT, "%.4f", topOneRate)
                    + ", recall@3=" + String.format(java.util.Locale.ROOT, "%.4f", recallAtThree)
                    + ", mrr@3=" + String.format(java.util.Locale.ROOT, "%.4f", meanReciprocalRankAtThree)
                    + ", zero=" + criticalZeroResults
                    + ", forbidden=" + forbiddenResultCount
                    + ", duplicate=" + duplicateGenericResultCount
                    + ", display=" + displayNameFailureCount
                    + ", serving=" + servingFailureCount
                    + ", avgSources=" + String.format(java.util.Locale.ROOT, "%.2f", averageDistinctSourceKeys)
                    + ", avgCatalogTypes=" + String.format(java.util.Locale.ROOT, "%.2f", averageDistinctCatalogTypes)
                    + ", p95Ms=" + p95Milliseconds;
        }
    }
}

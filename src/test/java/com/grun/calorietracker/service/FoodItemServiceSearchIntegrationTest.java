package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodCanonicalResolutionEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodCategoryEntity;
import com.grun.calorietracker.entity.FoodItemCategoryEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionSource;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodCanonicalResolutionRepository;
import com.grun.calorietracker.repository.FoodCategoryRepository;
import com.grun.calorietracker.repository.FoodItemCategoryRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import com.grun.calorietracker.service.impl.FoodProductImportServiceImpl;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class FoodItemServiceSearchIntegrationTest {

    @Autowired
    private FoodCanonicalResolutionRepository foodCanonicalResolutionRepository;

    @Autowired
    private FoodCategoryRepository foodCategoryRepository;

    @Autowired
    private FoodItemCategoryRepository foodItemCategoryRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private FoodItemLocalizationRepository foodItemLocalizationRepository;

    @Autowired
    private FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Autowired
    private FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;

    @Autowired
    private FoodItemSearchAliasRepository foodItemSearchAliasRepository;

    @Autowired
    private FoodProductQualityIssueRepository foodProductQualityIssueRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private FoodItemServiceImpl foodItemService;
    private OpenFoodFactsService fallbackProvider;
    private CatalogPublicationService fallbackPublisher;
    private FoodProductQualityIssueTracker fallbackIssueTracker;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate tokenSearchJdbc;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"broccoli boiled", "Acme broccoli", "haslanmis brokoli", "brokoli haslanmis"})
    void indexedTokenSearchDoesNotDropReorderedOrCrossFieldMatches(String query) {
        var target = product("Boiled Broccoli", null, VerificationStatus.VERIFIED);
        target.setBrand("Acme");
        target.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        target.setPreparationState(FoodPreparationState.BOILED);
        var raw = product("Raw Broccoli", null, VerificationStatus.VERIFIED);
        var other = product("Boiled Cauliflower", null, VerificationStatus.VERIFIED);
        var brandOnly = product("Cafe Drink", null, VerificationStatus.VERIFIED);
        brandOnly.setBrand("Acme");
        foodItemRepository.saveAllAndFlush(List.of(target, raw, other, brandOnly));
        saveLocalization(target, PreferredLanguage.TR, "Haşlanmış Brokoli");
        var alias = new FoodItemSearchAliasEntity();
        alias.setFoodItem(target);
        alias.setAlias("Haşlanmış Brokoli");
        alias.setNormalizedAlias("haslanmis brokoli");
        alias.setLanguage(PreferredLanguage.TR);
        alias.setAliasType(FoodSearchAliasType.TRANSLATION);
        alias.setSource("token-search-fixture");
        foodItemSearchAliasRepository.saveAndFlush(alias);
        var provider = new com.grun.calorietracker.service.support.PostgresFoodSearchCandidateProvider(tokenSearchJdbc);
        // Execute the actual indexed-provider SQL on both H2 and opt-in PG18.3.
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "postgres", true);
        org.springframework.test.util.ReflectionTestUtils.setField(foodItemService, "postgresFoodSearchCandidateProvider", provider);
        var criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(query);
        criteria.setPreferredLanguage(PreferredLanguage.TR);
        assertEquals(List.of(target.getId()), foodItemService.searchFoodItems(criteria, 0, 25).getContent()
                .stream().map(com.grun.calorietracker.dto.FoodProductDto::getId).toList(), query);
    }

    @BeforeEach
    void setUp() {
        OpenFoodFactsService openFoodFactsService = Mockito.mock(OpenFoodFactsService.class);
        fallbackProvider = openFoodFactsService;
        fallbackPublisher = Mockito.mock(CatalogPublicationService.class);
        FoodProductQualityIssueTracker foodProductQualityIssueTracker = Mockito.mock(FoodProductQualityIssueTracker.class);
        fallbackIssueTracker = foodProductQualityIssueTracker;
        foodItemService = new FoodItemServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                openFoodFactsService,
                foodProductQualityIssueTracker,
                Mockito.mock(com.grun.calorietracker.service.FoodProductEvidenceService.class),
                fallbackPublisher
        );
    }

    private com.grun.calorietracker.dto.FoodProductDto externalFixture(String name, String barcode, double calories) {
        var source = new com.grun.calorietracker.dto.FoodProductDto();
        source.setProductName(name); source.setBarcode(barcode); source.setCalories(calories);
        source.setProtein(3.0); source.setFat(3.0); source.setCarbs(5.0);
        source.setBrand("Acme"); source.setMarketRegion(MarketRegion.UK_IE);
        source.setNutritionReferenceUnit(com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100G);
        source.setPreparationState(FoodPreparationState.UNSPECIFIED); source.setNutriScore("d");
        return source;
    }

    private void externalReplies(List<com.grun.calorietracker.dto.FoodProductDto> sources) {
        Mockito.when(fallbackProvider.searchProductsOutcome(Mockito.any()))
                .thenReturn(com.grun.calorietracker.dto.FoodExternalSearchResult.success(sources));
        Mockito.when(fallbackPublisher.publishNew(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> {
                    FoodItemEntity item = call.getArgument(0);
                    item.setPublicationStatus(com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED);
                    return foodItemRepository.saveAndFlush(item);
                });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"min", "max", "brand", "score", "state", "market", "type", "query"})
    void externalFallbackCannotBypassCanonicalFilters(String filter) {
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        switch (filter) {
            case "min" -> criteria.setMinCalories(80.0);
            case "max" -> criteria.setMaxCalories(50.0);
            case "brand" -> criteria.setBrand("Wanted");
            case "score" -> criteria.setNutriScore("a");
            case "state" -> criteria.setPreparationState(FoodPreparationState.RAW);
            case "market" -> criteria.setMarketRegion(MarketRegion.TR);
            case "type" -> criteria.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
            case "query" -> criteria.setQuery("Coffee");
            default -> throw new AssertionError(filter);
        }
        externalReplies(List.of(externalFixture("Milk", "3017620422003", 60)));
        var result = foodItemService.searchFoodItems(criteria, 0, 25);
        assertTrue(result.getContent().isEmpty(), filter);
        assertEquals(0L, result.getTotalElements(), filter);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"asc", "desc"})
    void fallbackUsesSameSortAndPaginationAsTheNextLocalCall(String direction) {
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        criteria.setSortBy("calories"); criteria.setSortOrder(direction);
        var high = externalFixture("Milk High", "3017620422003", 100);
        var low = externalFixture("Milk Low", "5449000000996", 60);
        externalReplies("asc".equals(direction) ? List.of(high, low) : List.of(low, high));
        var first = foodItemService.searchFoodItems(criteria, 0, 1);
        assertEquals("asc".equals(direction) ? 60.0 : 100.0, first.getContent().get(0).getCalories());
        assertEquals(2L, first.getTotalElements()); assertEquals(2, first.getTotalPages());
        var repeated = foodItemService.searchFoodItems(criteria, 0, 1);
        assertEquals(first.getContent().get(0).getId(), repeated.getContent().get(0).getId());
        var next = foodItemService.searchFoodItems(criteria, 1, 1);
        assertEquals(2L, next.getTotalElements());
        org.junit.jupiter.api.Assertions.assertNotEquals(first.getContent().get(0).getId(), next.getContent().get(0).getId());
        Mockito.verify(fallbackProvider, Mockito.times(1)).searchProductsOutcome(Mockito.any());
    }

    @Test void fallbackDeduplicatesProviderRowsUsingStoredProductIdentity() {
        var source = externalFixture("Milk", "3017620422003", 60);
        externalReplies(List.of(source, source));
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        var result = foodItemService.searchFoodItems(criteria, 0, 25);
        assertEquals(1, result.getContent().size()); assertEquals(1L, result.getTotalElements());
        Mockito.verify(fallbackPublisher, Mockito.times(1)).publishNew(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test void fallbackCannotResurfaceAnActiveBlockingQualityIssue() {
        var stored = product("Milk", "3017620422003", VerificationStatus.VERIFIED);
        foodItemRepository.saveAndFlush(stored);
        var issue = new FoodProductQualityIssueEntity(); issue.setFoodItem(stored);
        issue.setIssueType(FoodProductQualityIssue.MISSING_MACROS); issue.setResolved(false);
        foodProductQualityIssueRepository.saveAndFlush(issue);
        externalReplies(List.of(externalFixture("Milk", stored.getBarcode(), 60)));
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        assertEquals(100.0, foodItemRepository.findById(stored.getId()).orElseThrow().getCalories());
        Mockito.verifyNoInteractions(fallbackPublisher);
    }

    @Test void fallbackCannotReturnCustomProductsExcludedFromPublicSearch() {
        var stored = product("Milk", "3017620422003", VerificationStatus.VERIFIED);
        stored.setIsCustom(true); foodItemRepository.saveAndFlush(stored);
        externalReplies(List.of(externalFixture("Milk", stored.getBarcode(), 60)));
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        Mockito.verifyNoInteractions(fallbackPublisher);
    }

    @Test void fallbackCannotReplaceExistingNamesBrandNutritionOrLocalesByBarcode() {
        var stored = product("Chocolate", "3017620422003", VerificationStatus.VERIFIED);
        stored.setBrand("Original"); foodItemRepository.saveAndFlush(stored);
        saveLocalization(stored, PreferredLanguage.TR, "Çikolata");
        externalReplies(List.of(externalFixture("Milk", stored.getBarcode(), 60)));
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk"); criteria.setPreferredLanguage(PreferredLanguage.TR);
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        var preserved = foodItemRepository.findById(stored.getId()).orElseThrow();
        assertEquals("Chocolate", preserved.getName()); assertEquals("Original", preserved.getBrand());
        assertEquals(100.0, preserved.getCalories());
        assertEquals("Çikolata", foodItemService.getFoodItemById(stored.getId(), null, PreferredLanguage.TR).getProductName());
        Mockito.verifyNoInteractions(fallbackPublisher);
    }

    @Test void fallbackRefreshesIndexedCandidatesAfterPublication() {
        var provider = new com.grun.calorietracker.service.support.PostgresFoodSearchCandidateProvider(tokenSearchJdbc);
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "postgres", true);
        org.springframework.test.util.ReflectionTestUtils.setField(foodItemService, "postgresFoodSearchCandidateProvider", provider);
        externalReplies(List.of(externalFixture("Milk", "3017620422003", 60)));
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        assertEquals(List.of(), provider.findCandidateIds("Milk").orElseThrow());
        var first = foodItemService.searchFoodItems(criteria, 0, 25);
        assertEquals(1, first.getContent().size());
        assertTrue(provider.findCandidateIds("Milk").orElseThrow().contains(first.getContent().get(0).getId()));
        assertEquals(first.getContent().get(0).getId(), foodItemService.searchFoodItems(criteria, 0, 25).getContent().get(0).getId());
    }

    @Test void fallbackRanksExactMatchesButKeepsDistinctBarcodes() {
        externalReplies(List.of(externalFixture("Milk Drink", "3017620422003", 70),
                externalFixture("Milk", "5449000000996", 60),
                externalFixture("Milk", "7622210585149", 65)));
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        var first = foodItemService.searchFoodItems(criteria, 0, 25);
        assertEquals(3L, first.getTotalElements());
        assertEquals(List.of("Milk", "Milk", "Milk Drink"), first.getContent().stream()
                .map(com.grun.calorietracker.dto.FoodProductDto::getProductName).toList());
        assertEquals(3, first.getContent().stream().map(com.grun.calorietracker.dto.FoodProductDto::getBarcode).distinct().count());
        assertEquals(first.getContent().stream().map(com.grun.calorietracker.dto.FoodProductDto::getId).toList(),
                foodItemService.searchFoodItems(criteria, 0, 25).getContent().stream()
                        .map(com.grun.calorietracker.dto.FoodProductDto::getId).toList());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = FoodCatalogType.class, names = {"GENERIC_INGREDIENT", "LOCAL_DISH"})
    void curatedCatalogTypesNeverCallTheBrandedProvider(FoodCatalogType type) {
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk"); criteria.setCatalogType(type);
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        Mockito.verifyNoInteractions(fallbackProvider, fallbackPublisher);
    }

    @Test void fallbackCannotResurfaceAnExplicitlyResolvedCanonicalDuplicate() {
        String key = "GLOBAL:GENERIC_INGREDIENT:UNSPECIFIED:milk";
        var primary = product("Chocolate", null, VerificationStatus.VERIFIED);
        primary.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT); primary.setCanonicalFoodKey(key);
        var duplicate = product("Milk", "3017620422003", VerificationStatus.VERIFIED);
        duplicate.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT); duplicate.setCanonicalFoodKey(key);
        foodItemRepository.saveAllAndFlush(List.of(primary, duplicate));
        var decision = new FoodCanonicalResolutionEntity(); decision.setCanonicalFoodKey(key);
        decision.setPrimaryFoodItem(primary); decision.setResolvedBy("fixture@example.invalid");
        foodCanonicalResolutionRepository.saveAndFlush(decision);
        externalReplies(List.of(externalFixture("Milk", duplicate.getBarcode(), 60)));
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        Mockito.verifyNoInteractions(fallbackPublisher);
    }

    @Test void fallbackRechecksQualityDecisionsCreatedDuringImport() {
        externalReplies(List.of(externalFixture("Milk", "3017620422003", 60)));
        Mockito.doAnswer(call -> {
            var issue = new FoodProductQualityIssueEntity(); issue.setFoodItem(call.getArgument(0));
            issue.setIssueType(FoodProductQualityIssue.MISSING_MACROS); issue.setResolved(false);
            foodProductQualityIssueRepository.save(issue);
            return null;
        }).when(fallbackIssueTracker).syncReviewIssues(Mockito.any(), Mockito.any());
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("Milk");
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        assertEquals(1, foodItemRepository.count());
    }

    @Test
    void importedLocalization_isReturnedForTurkishAliasSearchWithoutDuplicateProduct() {
        FoodProductQualityIssueTracker issueTracker = Mockito.mock(FoodProductQualityIssueTracker.class);
        FoodProductImportServiceImpl importService = new FoodProductImportServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemSearchAliasRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                issueTracker,
                org.mockito.Mockito.mock(com.grun.calorietracker.service.FoodProductEvidenceService.class),
                new ObjectMapper()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "localized-food.csv",
                "text/csv",
                """
                        catalog_type,source_key,name,display_name,short_display_name,display_name_en,short_display_name_en,display_name_tr,short_display_name_tr,alias_tr,calories,protein,fat,carbs,market_region,preparation_state,serving_options_json
                        GENERIC_INGREDIENT,GLOBAL:GENERIC_INGREDIENT:RAW:banana-localized,Banana raw,Raw Banana,Banana,Banana,Banana,Muz,Muz,muz,89,1.1,0.3,22.8,GLOBAL,RAW,"[{""label"":""1 medium banana"",""unitType"":""PIECE"",""quantity"":1,""gramWeight"":118,""defaultOption"":true},{""label"":""1/2 banana"",""unitType"":""PIECE"",""quantity"":0.5,""gramWeight"":59,""defaultOption"":false}]"
                        """.getBytes(StandardCharsets.UTF_8)
        );

        importService.importCsv(file, "admin@test.com");

        publishMassBasedSearchFixturesAfterReview();

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("muz");
        criteria.setMarketRegion(MarketRegion.TR);
        criteria.setPreferredLanguage(PreferredLanguage.TR);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals("Muz", result.getContent().get(0).getProductName());
        assertEquals("Banana raw", result.getContent().get(0).getCanonicalName());
        assertEquals(2, result.getContent().get(0).getServingOptions().size());
        assertEquals("1 medium banana", result.getContent().get(0).getServingOptions().get(0).getLabel());
        assertEquals(true, result.getContent().get(0).getDefaultServingOptionId() != null);
        assertEquals(1, foodItemRepository.count());
        assertEquals(2, foodItemServingOptionRepository.count());
    }
    @Test
    void approvedGenericManifestSeed_importsAndSupportsLocalizedSearch() throws Exception {
        FoodProductImportServiceImpl importService = new FoodProductImportServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemSearchAliasRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                Mockito.mock(FoodProductQualityIssueTracker.class),
                Mockito.mock(com.grun.calorietracker.service.FoodProductEvidenceService.class),
                new ObjectMapper()
        );
        byte[] seed;
        try (var input = getClass().getResourceAsStream("/generic-food-approved-seed-v1.csv")) {
            assertTrue(input != null, "Approved generic food seed is missing");
            seed = input.readAllBytes();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "generic-food-approved-seed-v1.csv",
                "text/csv",
                seed
        );

        FoodProductImportResultDto importResult = importService.importCsv(file, "manifest-gate@grun.app");

        assertEquals(146, importResult.getSavedRows());
        assertEquals(0, importResult.getSkippedRows());
        assertEquals(0, importResult.getQualityWarningCounts().getOrDefault("MISSING_CALORIES", 0));
        assertEquals(0, importResult.getQualityWarningCounts().getOrDefault("MISSING_MACROS", 0));
        assertEquals(0, importResult.getQualityWarningCounts().getOrDefault("INVALID_SERVING_OPTIONS", 0));
        assertEquals(0, importResult.getQualityWarningCounts().getOrDefault("GENERIC_MISSING_PREPARATION_STATE", 0));

        entityManager.flush();
        entityManager.clear();

        assertEquals(146, foodItemRepository.count());
        assertEquals(292, foodItemLocalizationRepository.count());
        assertEquals(146, foodItemServingOptionRepository.count());
        assertEquals(292, foodItemServingOptionLocalizationRepository.count());

        publishMassBasedSearchFixturesAfterReview();

        FoodSearchCriteriaDto turkishBanana = new FoodSearchCriteriaDto();
        turkishBanana.setQuery("muz");
        turkishBanana.setMarketRegion(MarketRegion.TR);
        turkishBanana.setPreferredLanguage(PreferredLanguage.TR);
        FoodProductSearchPageDto bananaResults = foodItemService.searchFoodItems(turkishBanana, 0, 10);
        assertTrue(!bananaResults.getContent().isEmpty());
        assertTrue(bananaResults.getContent().get(0).getProductName().contains("Muz"));
        assertEquals(1, bananaResults.getContent().get(0).getServingOptions().size());

        FoodSearchCriteriaDto turkishRice = new FoodSearchCriteriaDto();
        turkishRice.setQuery("pirinç");
        turkishRice.setMarketRegion(MarketRegion.TR);
        turkishRice.setPreferredLanguage(PreferredLanguage.TR);
        FoodProductSearchPageDto riceResults = foodItemService.searchFoodItems(turkishRice, 0, 10);
        assertTrue(riceResults.getContent().size() >= 2);
    }
    @Test
    void searchFoodItems_excludesRejectedProductsFromUserSearch() {
        FoodItemEntity verifiedProduct = product("Visible Protein Bar", "111111", VerificationStatus.VERIFIED);
        FoodItemEntity rejectedProduct = product("Rejected Protein Bar", "222222", VerificationStatus.REJECTED);
        foodItemRepository.saveAll(List.of(verifiedProduct, rejectedProduct));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("protein");

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals("Visible Protein Bar", result.getContent().get(0).getProductName());
        assertEquals(VerificationStatus.VERIFIED, result.getContent().get(0).getVerificationStatus());
    }

    @Test
    void searchFoodItems_excludesProductsWithBlockingQualityIssuesFromUserSearch() {
        FoodItemEntity cleanProduct = product("Visible Chicken Meal", "121001", VerificationStatus.RAW_IMPORTED);
        cleanProduct.setMarketRegion(MarketRegion.UK_IE);

        FoodItemEntity suspiciousProduct = product("Broken Chicken Meal", "121002", VerificationStatus.RAW_IMPORTED);
        suspiciousProduct.setMarketRegion(MarketRegion.UK_IE);
        suspiciousProduct.setCalories(65600.0);

        foodItemRepository.saveAll(List.of(cleanProduct, suspiciousProduct));

        FoodProductQualityIssueEntity issue = new FoodProductQualityIssueEntity();
        issue.setFoodItem(suspiciousProduct);
        issue.setIssueType(FoodProductQualityIssue.SUSPICIOUS_CALORIES);
        issue.setIdentifier("121002");
        issue.setReason("Calories are not valid for a 100g nutrition basis.");
        issue.setResolved(false);
        foodProductQualityIssueRepository.save(issue);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("chicken meal");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals("Visible Chicken Meal", result.getContent().get(0).getProductName());
    }
    @Test
    void searchFoodItems_whenMarketRegionProvided_filtersProductsByRegion() {
        FoodItemEntity ukIeProduct = product("Regional Milk", "333333", VerificationStatus.VERIFIED);
        ukIeProduct.setMarketRegion(MarketRegion.UK_IE);
        FoodItemEntity trProduct = product("Regional Milk", "444444", VerificationStatus.VERIFIED);
        trProduct.setMarketRegion(MarketRegion.TR);
        foodItemRepository.saveAll(List.of(ukIeProduct, trProduct));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("regional");
        criteria.setMarketRegion(MarketRegion.TR);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals("444444", result.getContent().get(0).getBarcode());
        assertEquals(MarketRegion.TR, result.getContent().get(0).getMarketRegion());
    }

    @Test
    void searchFoodItems_whenMarketRegionProvided_includesConfiguredFallbackRegions() {
        FoodItemEntity ukIeProduct = product("Regional Oats", "555555", VerificationStatus.VERIFIED);
        ukIeProduct.setMarketRegion(MarketRegion.UK_IE);
        FoodItemEntity euProduct = product("Regional Oats", "666666", VerificationStatus.VERIFIED);
        euProduct.setMarketRegion(MarketRegion.EU);
        FoodItemEntity globalProduct = product("Regional Oats", "777777", VerificationStatus.VERIFIED);
        globalProduct.setMarketRegion(MarketRegion.GLOBAL);
        FoodItemEntity trProduct = product("Regional Oats", "888888", VerificationStatus.VERIFIED);
        trProduct.setMarketRegion(MarketRegion.TR);
        foodItemRepository.saveAll(List.of(ukIeProduct, euProduct, globalProduct, trProduct));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("regional");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(3, result.getContent().size());
        assertEquals("555555", result.getContent().get(0).getBarcode());
        assertEquals("666666", result.getContent().get(1).getBarcode());
        assertEquals("777777", result.getContent().get(2).getBarcode());
    }

    @Test
    void searchFoodItems_defaultRanking_prioritizesExactVerifiedLocalResultBeforeRawHighQualityResult() {
        FoodItemEntity exactLocalDish = product("Porridge Oats", null, VerificationStatus.VERIFIED);
        exactLocalDish.setSourceKey("UK_IE:LOCAL_DISH:porridge_oats");
        exactLocalDish.setCatalogType(FoodCatalogType.LOCAL_DISH);
        exactLocalDish.setMarketRegion(MarketRegion.UK_IE);
        exactLocalDish.setQualityScore(70);
        exactLocalDish.setUsageCount(5L);

        FoodItemEntity rawBrandedProduct = product("Organic Porridge Oats Bar", "999001", VerificationStatus.RAW_IMPORTED);
        rawBrandedProduct.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        rawBrandedProduct.setMarketRegion(MarketRegion.UK_IE);
        rawBrandedProduct.setQualityScore(100);
        rawBrandedProduct.setUsageCount(500L);

        FoodItemEntity genericIngredient = product("Rolled Oats", null, VerificationStatus.VERIFIED);
        genericIngredient.setSourceKey("GLOBAL:GENERIC_INGREDIENT:rolled_oats");
        genericIngredient.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        genericIngredient.setMarketRegion(MarketRegion.GLOBAL);
        genericIngredient.setQualityScore(95);
        genericIngredient.setUsageCount(100L);

        foodItemRepository.saveAll(List.of(rawBrandedProduct, genericIngredient, exactLocalDish));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("porridge oats");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(2, result.getContent().size());
        assertEquals("Porridge Oats", result.getContent().get(0).getProductName());
        assertEquals(FoodCatalogType.LOCAL_DISH, result.getContent().get(0).getCatalogType());
        assertEquals("Organic Porridge Oats Bar", result.getContent().get(1).getProductName());
    }

    @Test
    void searchFoodItems_defaultRanking_prioritizesGlobalGenericIngredientBeforeRegionalBrandedProductForSameFoodName() {
        FoodItemEntity regionalBranded = product("Chicken Breast", "333001", VerificationStatus.RAW_IMPORTED);
        regionalBranded.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        regionalBranded.setMarketRegion(MarketRegion.UK_IE);
        regionalBranded.setQualityScore(100);

        FoodItemEntity globalGeneric = product("Chicken Breast", null, VerificationStatus.RAW_IMPORTED);
        globalGeneric.setSourceKey("USDA_FOODDATA:fdc:999001");
        globalGeneric.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        globalGeneric.setMarketRegion(MarketRegion.GLOBAL);
        globalGeneric.setPreparationState(FoodPreparationState.RAW);
        globalGeneric.setQualityScore(80);

        foodItemRepository.saveAll(List.of(regionalBranded, globalGeneric));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("chicken breast");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(2, result.getContent().size());
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, result.getContent().get(0).getCatalogType());
        assertEquals(MarketRegion.GLOBAL, result.getContent().get(0).getMarketRegion());
        assertEquals(FoodCatalogType.BRANDED_PRODUCT, result.getContent().get(1).getCatalogType());
        assertEquals(MarketRegion.UK_IE, result.getContent().get(1).getMarketRegion());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void searchFoodItems_defaultRanking_prioritizesPreparedItemsWithoutOverridingExplicitIntent(boolean indexed) {
        FoodItemEntity prepared = product("Prepared Oats", null, VerificationStatus.VERIFIED);
        prepared.setSourceKey("fixture:prepared-oats");
        prepared.setCatalogType(FoodCatalogType.STANDARD_PREPARED_ITEM);
        prepared.setPreparationState(FoodPreparationState.PREPARED);
        prepared.setUsageCount(1L);

        FoodItemEntity cooked = product("Cooked Oats", null, VerificationStatus.VERIFIED);
        cooked.setSourceKey("fixture:cooked-oats");
        cooked.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        cooked.setPreparationState(FoodPreparationState.COOKED);
        cooked.setUsageCount(100L);

        FoodItemEntity raw = product("Raw Oats", null, VerificationStatus.VERIFIED);
        raw.setSourceKey("fixture:raw-oats");
        raw.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        raw.setPreparationState(FoodPreparationState.RAW);
        raw.setUsageCount(1_000L);

        foodItemRepository.saveAllAndFlush(List.of(raw, cooked, prepared));
        useIndexedSearch(indexed);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("oats");
        FoodProductSearchPageDto defaultResult = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(List.of(prepared.getId(), cooked.getId(), raw.getId()), defaultResult.getContent().stream()
                .map(com.grun.calorietracker.dto.FoodProductDto::getId)
                .toList());

        criteria.setQuery("raw oats");
        assertEquals(raw.getId(), foodItemService.searchFoodItems(criteria, 0, 25).getContent().get(0).getId());

        criteria.setQuery("oats");
        criteria.setPreparationState(FoodPreparationState.RAW);
        assertEquals(List.of(raw.getId()), foodItemService.searchFoodItems(criteria, 0, 25).getContent().stream()
                .map(com.grun.calorietracker.dto.FoodProductDto::getId)
                .toList());
    }

    @Test
    void searchFoodItems_defaultRanking_prioritizesWholeWordFoodMatchBeforeSubstringMatch() {
        FoodItemEntity milkChocolate = product("Milk Chocolate", "111001", VerificationStatus.RAW_IMPORTED);
        milkChocolate.setMarketRegion(MarketRegion.UK_IE);
        milkChocolate.setQualityScore(100);
        milkChocolate.setUsageCount(1000L);

        FoodItemEntity wholeMilk = product("Whole Milk", "111002", VerificationStatus.RAW_IMPORTED);
        wholeMilk.setMarketRegion(MarketRegion.UK_IE);
        wholeMilk.setQualityScore(60);
        wholeMilk.setUsageCount(0L);

        FoodItemEntity shortbread = product("All Butter Scottish Shortbread Fingers", "111003", VerificationStatus.RAW_IMPORTED);
        shortbread.setMarketRegion(MarketRegion.UK_IE);
        shortbread.setQualityScore(100);
        shortbread.setUsageCount(1000L);

        FoodItemEntity whiteBread = product("White Bread", "111004", VerificationStatus.RAW_IMPORTED);
        whiteBread.setMarketRegion(MarketRegion.UK_IE);
        whiteBread.setQualityScore(60);
        whiteBread.setUsageCount(0L);

        foodItemRepository.saveAll(List.of(milkChocolate, wholeMilk, shortbread, whiteBread));

        FoodSearchCriteriaDto milkCriteria = new FoodSearchCriteriaDto();
        milkCriteria.setQuery("milk");
        milkCriteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto milkResult = foodItemService.searchFoodItems(milkCriteria, 0, 25);

        assertEquals("Whole Milk", milkResult.getContent().get(0).getProductName());
        assertEquals("Milk Chocolate", milkResult.getContent().get(1).getProductName());

        FoodSearchCriteriaDto breadCriteria = new FoodSearchCriteriaDto();
        breadCriteria.setQuery("bread");
        breadCriteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto breadResult = foodItemService.searchFoodItems(breadCriteria, 0, 25);

        assertEquals("White Bread", breadResult.getContent().get(0).getProductName());
        assertEquals("All Butter Scottish Shortbread Fingers", breadResult.getContent().get(1).getProductName());
    }

    @Test
    void searchFoodItems_whenBrandProvided_filtersByBrandAndSupportsBrandSearchText() {
        FoodItemEntity tescoMilk = product("Semi Skimmed Milk", "222001", VerificationStatus.RAW_IMPORTED);
        tescoMilk.setBrand("Tesco");
        tescoMilk.setMarketRegion(MarketRegion.UK_IE);

        FoodItemEntity dunnesMilk = product("Semi Skimmed Milk", "222002", VerificationStatus.RAW_IMPORTED);
        dunnesMilk.setBrand("Dunnes Stores");
        dunnesMilk.setMarketRegion(MarketRegion.UK_IE);

        foodItemRepository.saveAll(List.of(tescoMilk, dunnesMilk));

        FoodSearchCriteriaDto brandFilterCriteria = new FoodSearchCriteriaDto();
        brandFilterCriteria.setQuery("milk");
        brandFilterCriteria.setBrand("tesco");
        brandFilterCriteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto filtered = foodItemService.searchFoodItems(brandFilterCriteria, 0, 25);

        assertEquals(1, filtered.getContent().size());
        assertEquals("Tesco", filtered.getContent().get(0).getBrand());

        FoodSearchCriteriaDto brandSearchCriteria = new FoodSearchCriteriaDto();
        brandSearchCriteria.setQuery("dunnes");
        brandSearchCriteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto brandSearch = foodItemService.searchFoodItems(brandSearchCriteria, 0, 25);

        assertEquals(1, brandSearch.getContent().size());
        assertEquals("Dunnes Stores", brandSearch.getContent().get(0).getBrand());
    }

    @Test
    void searchFoodItems_whenPreparationStateProvided_filtersProducts() {
        FoodItemEntity rawRice = product("Rice", null, VerificationStatus.VERIFIED);
        rawRice.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:rice");
        rawRice.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        rawRice.setMarketRegion(MarketRegion.GLOBAL);
        rawRice.setPreparationState(FoodPreparationState.RAW);
        rawRice.setCalories(360.0);

        FoodItemEntity cookedRice = product("Rice", null, VerificationStatus.VERIFIED);
        cookedRice.setSourceKey("GLOBAL:GENERIC_INGREDIENT:COOKED:rice");
        cookedRice.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        cookedRice.setMarketRegion(MarketRegion.GLOBAL);
        cookedRice.setPreparationState(FoodPreparationState.COOKED);
        cookedRice.setCalories(130.0);

        foodItemRepository.saveAll(List.of(rawRice, cookedRice));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("rice");
        criteria.setPreparationState(FoodPreparationState.COOKED);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(FoodPreparationState.COOKED, result.getContent().get(0).getPreparationState());
        assertEquals(130.0, result.getContent().get(0).getCalories());
    }

    @Test
    void searchFoodItems_matchesDbSearchAliasWithoutDuplicatingProductData() {
        FoodItemEntity semiSkimmedMilk = product("Semi Skimmed Milk", "222003", VerificationStatus.RAW_IMPORTED);
        semiSkimmedMilk.setBrand("Tesco");
        semiSkimmedMilk.setMarketRegion(MarketRegion.UK_IE);
        FoodItemEntity savedProduct = foodItemRepository.save(semiSkimmedMilk);

        FoodItemSearchAliasEntity alias = new FoodItemSearchAliasEntity();
        alias.setFoodItem(savedProduct);
        alias.setAlias("yar\u0131m ya\u011fl\u0131 s\u00fct");
        alias.setNormalizedAlias("yarim yagli sut");
        alias.setLanguage(PreferredLanguage.TR);
        alias.setAliasType(FoodSearchAliasType.TRANSLATION);
        alias.setSource("test");
        alias.setActive(true);
        foodItemSearchAliasRepository.save(alias);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("yar\u0131m ya\u011fl\u0131 s\u00fct");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(savedProduct.getId(), result.getContent().get(0).getId());
        assertEquals("Semi Skimmed Milk", result.getContent().get(0).getProductName());
        assertEquals("Tesco", result.getContent().get(0).getBrand());
    }


    @Test
    void searchFoodItems_ignoresInactiveAliasMatches() {
        FoodItemEntity milkChocolate = product("Milk Chocolate", "222004", VerificationStatus.RAW_IMPORTED);
        milkChocolate.setMarketRegion(MarketRegion.UK_IE);
        FoodItemEntity savedProduct = foodItemRepository.save(milkChocolate);

        FoodItemSearchAliasEntity inactiveAlias = new FoodItemSearchAliasEntity();
        inactiveAlias.setFoodItem(savedProduct);
        inactiveAlias.setAlias("hidden-local-name");
        inactiveAlias.setNormalizedAlias("hidden-local-name");
        inactiveAlias.setLanguage(PreferredLanguage.TR);
        inactiveAlias.setAliasType(FoodSearchAliasType.TRANSLATION);
        inactiveAlias.setSource("test");
        inactiveAlias.setActive(false);
        foodItemSearchAliasRepository.save(inactiveAlias);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("hidden-local-name");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(0, result.getContent().size());
    }

    @Test
    void searchFoodItems_defaultRanking_prioritizesGenericIngredientBeforeShortRegionalBrandedSubstringMatch() {
        FoodItemEntity shortRegionalBranded = product("Orzo Rice", "333010", VerificationStatus.RAW_IMPORTED);
        shortRegionalBranded.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        shortRegionalBranded.setMarketRegion(MarketRegion.UK_IE);
        shortRegionalBranded.setQualityScore(100);
        shortRegionalBranded.setUsageCount(1000L);

        FoodItemEntity globalGeneric = product("Rice, white, cooked", null, VerificationStatus.RAW_IMPORTED);
        globalGeneric.setSourceKey("USDA_FOODDATA:fdc:999010");
        globalGeneric.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        globalGeneric.setMarketRegion(MarketRegion.GLOBAL);
        globalGeneric.setPreparationState(FoodPreparationState.COOKED);
        globalGeneric.setQualityScore(50);
        globalGeneric.setUsageCount(0L);

        foodItemRepository.saveAll(List.of(shortRegionalBranded, globalGeneric));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("rice");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(2, result.getContent().size());
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, result.getContent().get(0).getCatalogType());
        assertEquals("Rice, white, cooked", result.getContent().get(0).getProductName());
        assertEquals(FoodCatalogType.BRANDED_PRODUCT, result.getContent().get(1).getCatalogType());
    }


    @Test
    void searchFoodItems_defaultRanking_treatsSimplePluralGenericNameAsWholeWordMatch() {
        FoodItemEntity bananaBread = product("Banana Bread", "333020", VerificationStatus.RAW_IMPORTED);
        bananaBread.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        bananaBread.setMarketRegion(MarketRegion.UK_IE);
        bananaBread.setQualityScore(100);
        bananaBread.setUsageCount(1000L);

        FoodItemEntity genericBanana = product("Bananas, ripe, raw", null, VerificationStatus.RAW_IMPORTED);
        genericBanana.setSourceKey("USDA_FOODDATA:fdc:999020");
        genericBanana.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        genericBanana.setMarketRegion(MarketRegion.GLOBAL);
        genericBanana.setPreparationState(FoodPreparationState.RAW);
        genericBanana.setQualityScore(50);
        genericBanana.setUsageCount(0L);

        foodItemRepository.saveAll(List.of(bananaBread, genericBanana));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("banana");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(2, result.getContent().size());
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, result.getContent().get(0).getCatalogType());
        assertEquals("Bananas, ripe, raw", result.getContent().get(0).getProductName());
        assertEquals(FoodCatalogType.BRANDED_PRODUCT, result.getContent().get(1).getCatalogType());
    }

    @Test
    void searchFoodItems_localizesProductNameWhenPreferredLanguageHasLocalization() {
        FoodItemEntity banana = product("Raw Banana", null, VerificationStatus.VERIFIED);
        banana.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:banana");
        banana.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        banana.setMarketRegion(MarketRegion.GLOBAL);
        FoodItemEntity savedProduct = foodItemRepository.save(banana);

        FoodItemLocalizationEntity localization = new FoodItemLocalizationEntity();
        localization.setFoodItem(savedProduct);
        localization.setLanguage(PreferredLanguage.TR);
        localization.setDisplayName("Muz");
        localization.setShortDisplayName("Muz");
        localization.setSource("test");
        localization.setActive(true);
        foodItemLocalizationRepository.save(localization);

        FoodItemSearchAliasEntity alias = new FoodItemSearchAliasEntity();
        alias.setFoodItem(savedProduct);
        alias.setAlias("muz");
        alias.setNormalizedAlias("muz");
        alias.setLanguage(PreferredLanguage.TR);
        alias.setAliasType(FoodSearchAliasType.TRANSLATION);
        alias.setSource("test");
        alias.setActive(true);
        foodItemSearchAliasRepository.save(alias);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("muz");
        criteria.setMarketRegion(MarketRegion.TR);
        criteria.setPreferredLanguage(PreferredLanguage.TR);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(savedProduct.getId(), result.getContent().get(0).getId());
        assertEquals("Muz", result.getContent().get(0).getProductName());
        assertEquals("Raw Banana", result.getContent().get(0).getCanonicalName());
        assertEquals(PreferredLanguage.TR, result.getContent().get(0).getLanguage());
    }

    @Test
    void searchFoodItems_coreWordDoesNotMatchSubstringInsideAnotherLocalizedWord() {
        FoodItemEntity banana = product("Raw Banana", null, VerificationStatus.VERIFIED);
        banana.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:banana-word-boundary");
        banana.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        banana.setMarketRegion(MarketRegion.GLOBAL);
        FoodItemEntity pork = product("Raw Pork Tenderloin", null, VerificationStatus.VERIFIED);
        pork.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:pork-word-boundary");
        pork.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        pork.setMarketRegion(MarketRegion.GLOBAL);
        foodItemRepository.saveAllAndFlush(List.of(banana, pork));
        saveLocalization(banana, PreferredLanguage.TR, "Çiğ Muz");
        saveLocalization(pork, PreferredLanguage.TR, "Çiğ Domuz Bonfile");

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("muz");
        criteria.setMarketRegion(MarketRegion.TR);
        criteria.setPreferredLanguage(PreferredLanguage.TR);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(banana.getId(), result.getContent().get(0).getId());
    }
    @Test
    void searchFoodItems_matchesLocalizationTextWithoutDuplicatingProductData() {
        FoodItemEntity broccoli = product("Broccoli, raw", null, VerificationStatus.VERIFIED);
        broccoli.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:broccoli");
        broccoli.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        broccoli.setMarketRegion(MarketRegion.GLOBAL);
        FoodItemEntity savedProduct = foodItemRepository.save(broccoli);

        FoodItemLocalizationEntity localization = new FoodItemLocalizationEntity();
        localization.setFoodItem(savedProduct);
        localization.setLanguage(PreferredLanguage.TR);
        localization.setDisplayName("Brokoli");
        localization.setShortDisplayName("Brokoli");
        localization.setSource("test");
        localization.setActive(true);
        foodItemLocalizationRepository.save(localization);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("brokoli");
        criteria.setMarketRegion(MarketRegion.TR);
        criteria.setPreferredLanguage(PreferredLanguage.TR);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(savedProduct.getId(), result.getContent().get(0).getId());
        assertEquals("Brokoli", result.getContent().get(0).getProductName());
    }
    @Test
    void searchFoodItems_defaultRanking_prioritizesExactMatchInPreferredLanguage() {
        FoodItemEntity englishMatch = product("English Localized Candidate", null, VerificationStatus.VERIFIED);
        englishMatch.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:english-milk-ranking");
        englishMatch.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        englishMatch.setMarketRegion(MarketRegion.GLOBAL);
        englishMatch.setQualityScore(40);

        FoodItemEntity otherLanguageMatch = product("Other", null, VerificationStatus.VERIFIED);
        otherLanguageMatch.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:other-language-milk-ranking");
        otherLanguageMatch.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        otherLanguageMatch.setMarketRegion(MarketRegion.GLOBAL);
        otherLanguageMatch.setQualityScore(95);

        foodItemRepository.saveAll(List.of(englishMatch, otherLanguageMatch));
        saveLocalization(englishMatch, PreferredLanguage.EN, "Milk");
        saveLocalization(otherLanguageMatch, PreferredLanguage.TR, "Milk");

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("milk");
        criteria.setMarketRegion(MarketRegion.UK_IE);
        criteria.setPreferredLanguage(PreferredLanguage.EN);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(2, result.getContent().size());
        assertEquals(englishMatch.getId(), result.getContent().get(0).getId());
        assertEquals("Milk", result.getContent().get(0).getProductName());
    }
    @Test
    void searchFoodItems_defaultRanking_prioritizesExactLocalizedCoreFoodBeforeLocalizedVariants() {
        FoodItemEntity broccoli = product("Broccoli, raw", null, VerificationStatus.VERIFIED);
        broccoli.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:broccoli-ranking");
        broccoli.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        broccoli.setMarketRegion(MarketRegion.GLOBAL);

        FoodItemEntity broccoliRaab = product("Broccoli raab, raw", null, VerificationStatus.VERIFIED);
        broccoliRaab.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:broccoli-raab-ranking");
        broccoliRaab.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        broccoliRaab.setMarketRegion(MarketRegion.GLOBAL);

        FoodItemEntity broccoliBabyfood = product("Babyfood, broccoli", null, VerificationStatus.VERIFIED);
        broccoliBabyfood.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:broccoli-babyfood-ranking");
        broccoliBabyfood.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        broccoliBabyfood.setMarketRegion(MarketRegion.GLOBAL);

        foodItemRepository.saveAll(List.of(broccoliRaab, broccoliBabyfood, broccoli));
        saveLocalization(broccoli, "Brokoli");
        saveLocalization(broccoliRaab, "Brokoli Raab");
        saveLocalization(broccoliBabyfood, "Brokoli Bebek Mamasi");

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("brokoli");
        criteria.setMarketRegion(MarketRegion.TR);
        criteria.setPreferredLanguage(PreferredLanguage.TR);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(3, result.getContent().size());
        assertEquals(broccoli.getId(), result.getContent().get(0).getId());
        assertEquals("Brokoli", result.getContent().get(0).getProductName());
        assertEquals(broccoliBabyfood.getId(), result.getContent().get(2).getId());
    }

    @Test
    void searchFoodItems_coreIdentityRanksMilkAheadOfYogurtWhoseSourceDescriptionMentionsMilk() {
        FoodItemEntity yogurt = product(
                "Yogurt, plain, whole milk", null, VerificationStatus.VERIFIED);
        yogurt.setDisplayName("Prepared Plain Yogurt");
        yogurt.setShortDisplayName("Plain Yogurt");
        yogurt.setSourceKey("GLOBAL:GENERIC_INGREDIENT:PREPARED:plain-yogurt-milk-noise");
        yogurt.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        yogurt.setMarketRegion(MarketRegion.GLOBAL);
        yogurt.setPreparationState(FoodPreparationState.PREPARED);
        yogurt.setQualityScore(100);

        FoodItemEntity milk = product(
                "Milk, whole, 3.25% milkfat, with added vitamin D", null, VerificationStatus.VERIFIED);
        milk.setDisplayName("Prepared Whole Milk");
        milk.setShortDisplayName("Whole Milk");
        milk.setSourceKey("GLOBAL:GENERIC_INGREDIENT:PREPARED:whole-milk-identity");
        milk.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        milk.setMarketRegion(MarketRegion.GLOBAL);
        milk.setPreparationState(FoodPreparationState.PREPARED);
        milk.setQualityScore(50);

        foodItemRepository.saveAllAndFlush(List.of(yogurt, milk));
        FoodItemSearchAliasEntity turkishMilkAlias = new FoodItemSearchAliasEntity();
        turkishMilkAlias.setFoodItem(milk);
        turkishMilkAlias.setLanguage(PreferredLanguage.TR);
        turkishMilkAlias.setAlias("tam yağlı süt");
        turkishMilkAlias.setNormalizedAlias("tam yagli sut");
        turkishMilkAlias.setAliasType(FoodSearchAliasType.ADMIN_MANUAL);
        turkishMilkAlias.setActive(true);
        foodItemSearchAliasRepository.saveAndFlush(turkishMilkAlias);

        FoodSearchCriteriaDto english = new FoodSearchCriteriaDto();
        english.setQuery("milk");
        english.setMarketRegion(MarketRegion.UK_IE);
        english.setPreferredLanguage(PreferredLanguage.EN);
        FoodSearchCriteriaDto turkish = new FoodSearchCriteriaDto();
        turkish.setQuery("süt");
        turkish.setMarketRegion(MarketRegion.TR);
        turkish.setPreferredLanguage(PreferredLanguage.TR);

        assertEquals(milk.getId(), foodItemService.searchFoodItems(english, 0, 10).getContent().get(0).getId());
        assertEquals(milk.getId(), foodItemService.searchFoodItems(turkish, 0, 10).getContent().get(0).getId());
    }
    @Test
    void searchFoodItems_defaultRanking_pushesDerivedProductsBelowCoreFood() {
        FoodItemEntity bananaBread = product("Banana Bread", null, VerificationStatus.VERIFIED);
        bananaBread.setSourceKey("GLOBAL:GENERIC_INGREDIENT:PREPARED:banana-bread-ranking");
        bananaBread.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        bananaBread.setMarketRegion(MarketRegion.GLOBAL);
        bananaBread.setQualityScore(100);
        bananaBread.setUsageCount(1000L);

        FoodItemEntity bananaChips = product("Banana Chips", null, VerificationStatus.VERIFIED);
        bananaChips.setSourceKey("GLOBAL:GENERIC_INGREDIENT:PREPARED:banana-chips-ranking");
        bananaChips.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        bananaChips.setMarketRegion(MarketRegion.GLOBAL);
        bananaChips.setQualityScore(100);
        bananaChips.setUsageCount(1000L);

        FoodItemEntity rawBanana = product("Banana, raw", null, VerificationStatus.VERIFIED);
        rawBanana.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:banana-ranking");
        rawBanana.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        rawBanana.setMarketRegion(MarketRegion.GLOBAL);
        rawBanana.setQualityScore(10);
        rawBanana.setUsageCount(0L);

        foodItemRepository.saveAll(List.of(bananaBread, bananaChips, rawBanana));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("banana");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(3, result.getContent().size());
        assertEquals(rawBanana.getId(), result.getContent().get(0).getId());
    }

    @Test
    void searchFoodItems_defaultRanking_pushesRiceFlourBelowCoreRice() {
        FoodItemEntity riceFlour = product("Brown Rice Flour", null, VerificationStatus.VERIFIED);
        riceFlour.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:brown-rice-flour-ranking");
        riceFlour.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        riceFlour.setMarketRegion(MarketRegion.GLOBAL);
        riceFlour.setQualityScore(100);

        FoodItemEntity cookedRice = product("Cooked White Rice", null, VerificationStatus.VERIFIED);
        cookedRice.setSourceKey("GLOBAL:GENERIC_INGREDIENT:COOKED:white-rice-ranking");
        cookedRice.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        cookedRice.setMarketRegion(MarketRegion.GLOBAL);
        cookedRice.setPreparationState(FoodPreparationState.COOKED);
        cookedRice.setQualityScore(50);

        foodItemRepository.saveAll(List.of(riceFlour, cookedRice));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("rice");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(2, result.getContent().size());
        assertEquals(cookedRice.getId(), result.getContent().get(0).getId());
        assertEquals(riceFlour.getId(), result.getContent().get(1).getId());
    }

    @Test
    void searchFoodItems_defaultRanking_pushesChickenBrothBelowCoreChicken() {
        FoodItemEntity chickenBroth = product("Chicken Broth Cubes Dry Soup", null, VerificationStatus.VERIFIED);
        chickenBroth.setSourceKey("GLOBAL:GENERIC_INGREDIENT:PREPARED:chicken-broth-ranking");
        chickenBroth.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        chickenBroth.setMarketRegion(MarketRegion.GLOBAL);
        chickenBroth.setQualityScore(100);

        FoodItemEntity chickenBreast = product("Raw Chicken Breast", null, VerificationStatus.VERIFIED);
        chickenBreast.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:chicken-breast-ranking");
        chickenBreast.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        chickenBreast.setMarketRegion(MarketRegion.GLOBAL);
        chickenBreast.setPreparationState(FoodPreparationState.RAW);
        chickenBreast.setQualityScore(50);

        foodItemRepository.saveAll(List.of(chickenBroth, chickenBreast));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("chicken");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(2, result.getContent().size());
        assertEquals(chickenBreast.getId(), result.getContent().get(0).getId());
        assertEquals(chickenBroth.getId(), result.getContent().get(1).getId());
    }
    @Test
    void searchFoodItems_coreQuery_prioritizesGenericFoodOverBrandedExactAlias() {
        FoodItemEntity genericMilk = product("Whole Milk", null, VerificationStatus.VERIFIED);
        genericMilk.setSourceKey("GLOBAL:GENERIC_INGREDIENT:UNSPECIFIED:whole-milk-core-ranking");
        genericMilk.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        genericMilk.setMarketRegion(MarketRegion.GLOBAL);
        genericMilk.setQualityScore(60);

        FoodItemEntity brandedMilk = product("Tesco Semi Skimmed Milk", "5000111000999", VerificationStatus.VERIFIED);
        brandedMilk.setBrand("Tesco");
        brandedMilk.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        brandedMilk.setMarketRegion(MarketRegion.UK_IE);
        brandedMilk.setQualityScore(100);
        brandedMilk.setUsageCount(1000L);

        foodItemRepository.saveAll(List.of(brandedMilk, genericMilk));

        FoodItemSearchAliasEntity broadAlias = new FoodItemSearchAliasEntity();
        broadAlias.setFoodItem(brandedMilk);
        broadAlias.setAlias("milk");
        broadAlias.setNormalizedAlias("milk");
        broadAlias.setLanguage(PreferredLanguage.EN);
        broadAlias.setAliasType(FoodSearchAliasType.COMMON_NAME);
        broadAlias.setSource("test");
        broadAlias.setActive(true);
        foodItemSearchAliasRepository.save(broadAlias);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("milk");
        criteria.setMarketRegion(MarketRegion.UK_IE);
        criteria.setPreferredLanguage(PreferredLanguage.EN);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(
                List.of(genericMilk.getId(), brandedMilk.getId()),
                result.getContent().stream().map(product -> product.getId()).toList()
        );
    }
    @Test
    void curatedSeed_supportsCoreEnglishAndTurkishSearchReadinessMatrix() throws Exception {
        FoodProductQualityIssueTracker issueTracker = Mockito.mock(FoodProductQualityIssueTracker.class);
        FoodProductImportServiceImpl importService = new FoodProductImportServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemSearchAliasRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                issueTracker,
                org.mockito.Mockito.mock(com.grun.calorietracker.service.FoodProductEvidenceService.class),
                new ObjectMapper()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "food-generic-staples-curated-seed.csv",
                "text/csv",
                Files.readAllBytes(Path.of("src", "test", "resources", "food-generic-staples-curated-seed.csv"))
        );

        var importStatistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        importStatistics.clear();
        importService.importCsv(file, "admin@test.com");
        long curatedImportStatementCount = importStatistics.getPrepareStatementCount();
        long curatedAliasCount = foodItemSearchAliasRepository.count();
        long importedMarketAvailabilityCount = ((Number) entityManager
                .createNativeQuery("select count(*) from food_item_market_regions")
                .getSingleResult()).longValue();
        long importedWriteRowCount = foodItemRepository.count()
                + curatedAliasCount
                + foodItemLocalizationRepository.count()
                + foodItemServingOptionRepository.count()
                + foodItemServingOptionLocalizationRepository.count()
                + importedMarketAvailabilityCount;
        assertTrue(
                curatedAliasCount >= 150,
                "Golden search seed should preserve broad EN/TR alias coverage."
        );
        assertTrue(
                curatedImportStatementCount <= importedWriteRowCount + 10,
                "Curated 14-row import executed " + curatedImportStatementCount
                        + " SQL statements for " + importedWriteRowCount + " persisted rows."
        );

        publishMassBasedSearchFixturesAfterReview();

        List<SearchReadinessExpectation> expectations = List.of(
                new SearchReadinessExpectation("banana", PreferredLanguage.EN, MarketRegion.UK_IE, "Raw Banana", 1),
                new SearchReadinessExpectation("broccoli", PreferredLanguage.EN, MarketRegion.UK_IE, "Raw Broccoli", 1),
                new SearchReadinessExpectation("milk", PreferredLanguage.EN, MarketRegion.UK_IE, "Whole Milk", 1),
                new SearchReadinessExpectation("chicken breast", PreferredLanguage.EN, MarketRegion.UK_IE, "Cooked Chicken Breast", 2),
                new SearchReadinessExpectation("rice", PreferredLanguage.EN, MarketRegion.UK_IE, "Cooked White Rice", 2),
                new SearchReadinessExpectation("muz", PreferredLanguage.TR, MarketRegion.TR, "Muz", 1),
                new SearchReadinessExpectation("brokoli", PreferredLanguage.TR, MarketRegion.TR, "Brokoli", 1),
                new SearchReadinessExpectation("süt", PreferredLanguage.TR, MarketRegion.TR, "Tam Yağlı Süt", 1),
                new SearchReadinessExpectation("tavuk göğsü", PreferredLanguage.TR, MarketRegion.TR, "Pişmiş Tavuk Göğsü", 2),
                new SearchReadinessExpectation("pirinç", PreferredLanguage.TR, MarketRegion.TR, "Pişmiş Beyaz Pirinç", 2)
        );

        for (SearchReadinessExpectation expectation : expectations) {
            FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(expectation.query());
            criteria.setPreferredLanguage(expectation.language());
            criteria.setMarketRegion(expectation.marketRegion());

            FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

            if (Set.of("milk", "süt").contains(expectation.query())) {
                // The legacy milk fixture has sugar 5.1 > carbs 4.8: R1 must not certify it.
                assertTrue(result.getContent().isEmpty(), "Inconsistent milk fixture must remain in review");
                continue;
            }
            assertEquals(true, result.getContent().size() >= expectation.minimumResults(), expectation.query());
            assertEquals(FoodCatalogType.GENERIC_INGREDIENT, result.getContent().get(0).getCatalogType(), expectation.query());
            assertEquals(expectation.expectedFirstDisplayName(), result.getContent().get(0).getProductName(), expectation.query());
            assertEquals(
                    result.getContent().size(),
                    result.getContent().stream().map(product -> product.getId()).distinct().count(),
                    expectation.query()
            );
        }
        FoodSearchCriteriaDto chickenCriteria = new FoodSearchCriteriaDto();
        chickenCriteria.setQuery("chicken breast");
        chickenCriteria.setPreferredLanguage(PreferredLanguage.EN);
        chickenCriteria.setMarketRegion(MarketRegion.UK_IE);
        FoodProductSearchPageDto chickenResults = foodItemService.searchFoodItems(chickenCriteria, 0, 25);
        assertEquals(2, chickenResults.getContent().stream()
                .map(product -> product.getPreparationState())
                .distinct()
                .count());
        assertEquals(2, chickenResults.getContent().stream()
                .map(product -> product.getProductName())
                .distinct()
                .count());

        FoodSearchCriteriaDto riceCriteria = new FoodSearchCriteriaDto();
        riceCriteria.setQuery("pirinç");
        riceCriteria.setPreferredLanguage(PreferredLanguage.TR);
        riceCriteria.setMarketRegion(MarketRegion.TR);
        FoodProductSearchPageDto riceResults = foodItemService.searchFoodItems(riceCriteria, 0, 25);
        assertEquals(2, riceResults.getContent().stream()
                .map(product -> product.getPreparationState())
                .distinct()
                .count());
        assertEquals(2, riceResults.getContent().stream()
                .map(product -> product.getProductName())
                .distinct()
                .count());
FoodSearchCriteriaDto bananaEnglish = new FoodSearchCriteriaDto();
        bananaEnglish.setQuery("banana");
        bananaEnglish.setPreferredLanguage(PreferredLanguage.EN);
        bananaEnglish.setMarketRegion(MarketRegion.UK_IE);
        FoodSearchCriteriaDto bananaTurkish = new FoodSearchCriteriaDto();
        bananaTurkish.setQuery("muz");
        bananaTurkish.setPreferredLanguage(PreferredLanguage.TR);
        bananaTurkish.setMarketRegion(MarketRegion.TR);
        var bananaEnglishResult = foodItemService.searchFoodItems(bananaEnglish, 0, 25).getContent().get(0);
        var bananaTurkishResult = foodItemService.searchFoodItems(bananaTurkish, 0, 25).getContent().get(0);

        assertEquals(14, foodItemRepository.count());
        assertEquals(19, foodItemServingOptionRepository.count());
        assertEquals(38, foodItemServingOptionLocalizationRepository.count());
        assertEquals("1 medium banana", bananaEnglishResult.getServingOptions().get(0).getLabel());
        assertEquals("1 orta boy muz", bananaTurkishResult.getServingOptions().get(0).getLabel());
        assertEquals(
                bananaEnglishResult.getServingOptions().get(0).getId(),
                bananaTurkishResult.getServingOptions().get(0).getId()
        );
    }
    @Test
    void searchFoodItems_localizesServingLabelWithoutDuplicatingServingOption() {
        FoodItemEntity banana = product("Banana raw", null, VerificationStatus.VERIFIED);
        banana.setSourceKey("GLOBAL:GENERIC_INGREDIENT:RAW:banana-serving-localization");
        banana.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        banana.setMarketRegion(MarketRegion.GLOBAL);
        banana.setPreparationState(FoodPreparationState.RAW);
        banana = foodItemRepository.save(banana);
        saveLocalization(banana, "Muz");

        FoodItemServingOptionEntity servingOption = new FoodItemServingOptionEntity();
        servingOption.setFoodItem(banana);
        servingOption.setLabel("1 medium banana");
        servingOption.setUnitType(FoodServingOptionUnit.PIECE);
        servingOption.setQuantity(1.0);
        servingOption.setGramWeight(118.0);
        servingOption.setIsDefault(true);
        servingOption.setSource(FoodServingOptionSource.ADMIN);
        servingOption.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);
        servingOption = foodItemServingOptionRepository.save(servingOption);

        FoodItemServingOptionLocalizationEntity servingLocalization =
                new FoodItemServingOptionLocalizationEntity();
        servingLocalization.setServingOption(servingOption);
        servingLocalization.setLanguage(PreferredLanguage.TR);
        servingLocalization.setLabel("1 orta boy muz");
        servingLocalization.setSource("test");
        servingLocalization.setActive(true);
        foodItemServingOptionLocalizationRepository.save(servingLocalization);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("muz");
        criteria.setMarketRegion(MarketRegion.TR);
        criteria.setPreferredLanguage(PreferredLanguage.TR);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getContent().get(0).getServingOptions().size());
        assertEquals("1 orta boy muz", result.getContent().get(0).getServingOptions().get(0).getLabel());
        assertEquals(servingOption.getId(), result.getContent().get(0).getDefaultServingOptionId());
        assertEquals(1, foodItemServingOptionRepository.count());
    }
    @Test
    void searchFoodItems_pageMappingUsesBoundedQueriesInsteadOfPerProductNPlusOne() {
        List<FoodItemEntity> products = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            FoodItemEntity product = product(
                    "Performance Food " + index,
                    "7000000000" + String.format("%03d", index),
                    VerificationStatus.VERIFIED
            );
            product.setMarketRegion(MarketRegion.UK_IE);
            products.add(product);
        }
        foodItemRepository.saveAll(products);
        entityManager.flush();
        entityManager.clear();

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("performance food");
        criteria.setMarketRegion(MarketRegion.UK_IE);
        criteria.setPreferredLanguage(PreferredLanguage.EN);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(12, result.getContent().size());
        assertTrue(
                statistics.getPrepareStatementCount() <= 6,
                "Search page mapping executed " + statistics.getPrepareStatementCount() + " SQL statements."
        );
    }
    @Test
    void findDuplicateCanonicalFoodKeys_groupsOnlyGenericIngredients() {
        String genericKey = "GLOBAL:GENERIC_INGREDIENT:RAW:banana";

        FoodItemEntity firstGeneric = product("Banana raw", null, VerificationStatus.VERIFIED);
        firstGeneric.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        firstGeneric.setCanonicalFoodKey(genericKey);

        FoodItemEntity secondGeneric = product("Bananas raw", null, VerificationStatus.VERIFIED);
        secondGeneric.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        secondGeneric.setCanonicalFoodKey(genericKey);

        FoodItemEntity uniqueGeneric = product("Apple raw", null, VerificationStatus.VERIFIED);
        uniqueGeneric.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        uniqueGeneric.setCanonicalFoodKey("GLOBAL:GENERIC_INGREDIENT:RAW:apple");

        FoodItemEntity firstBranded = product("Banana Snack A", "5010000000001", VerificationStatus.VERIFIED);
        firstBranded.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        firstBranded.setCanonicalFoodKey("SHOULD_NOT_GROUP");

        FoodItemEntity secondBranded = product("Banana Snack B", "5010000000002", VerificationStatus.VERIFIED);
        secondBranded.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        secondBranded.setCanonicalFoodKey("SHOULD_NOT_GROUP");

        foodItemRepository.saveAll(List.of(
                firstGeneric,
                secondGeneric,
                uniqueGeneric,
                firstBranded,
                secondBranded
        ));
        entityManager.flush();

        var result = foodItemRepository.findDuplicateCanonicalFoodKeys(PageRequest.of(0, 25));

        assertEquals(List.of(genericKey), result.getContent());
        assertEquals(1, result.getTotalElements());
        assertEquals(
                List.of(genericKey),
                foodItemRepository.findUnresolvedDuplicateCanonicalFoodKeys(PageRequest.of(0, 25)).getContent()
        );
        assertTrue(foodItemRepository.findResolvedDuplicateCanonicalFoodKeys(PageRequest.of(0, 25)).isEmpty());

        FoodCanonicalResolutionEntity resolution = new FoodCanonicalResolutionEntity();
        resolution.setCanonicalFoodKey(genericKey);
        resolution.setPrimaryFoodItem(firstGeneric);
        resolution.setResolvedBy("admin@test.com");
        foodCanonicalResolutionRepository.saveAndFlush(resolution);

        assertTrue(foodItemRepository.findUnresolvedDuplicateCanonicalFoodKeys(PageRequest.of(0, 25)).isEmpty());
        assertEquals(
                List.of(genericKey),
                foodItemRepository.findResolvedDuplicateCanonicalFoodKeys(PageRequest.of(0, 25)).getContent()
        );
    }
    private void saveLocalization(FoodItemEntity product, String displayName) {
        saveLocalization(product, PreferredLanguage.TR, displayName);
    }

    @Test
    void fetaVariantsRemainSearchableWithoutAddingRedundantPlainEnglishAlias() {
        var plain = product("Feta Cheese", null, VerificationStatus.VERIFIED);
        plain.setSourceKey("USDA_FOODDATA:fdc:173420");
        plain.setPreparationState(FoodPreparationState.UNSPECIFIED);
        plain.setCanonicalFoodKey("GLOBAL:GENERIC_INGREDIENT:UNSPECIFIED:feta_cheese");
        plain.setCalories(265.0);
        plain.setProtein(14.2);
        plain.setFat(21.5);
        plain.setCarbs(3.9);
        var crumbled = product("Cheese, feta, whole milk, crumbled", null, VerificationStatus.VERIFIED);
        crumbled.setSourceKey("USDA_FOODDATA:fdc:2259796");
        crumbled.setPreparationState(FoodPreparationState.PREPARED);
        crumbled.setCanonicalFoodKey("GLOBAL:GENERIC_INGREDIENT:PREPARED:feta_cheese");
        crumbled.setCalories(273.0);
        crumbled.setProtein(19.7);
        crumbled.setFat(19.1);
        crumbled.setCarbs(5.6);
        for (var item : List.of(plain, crumbled)) {
            item.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
            item.setMarketRegion(MarketRegion.GLOBAL);
        }
        foodItemRepository.saveAllAndFlush(List.of(plain, crumbled));
        saveLocalization(plain, PreferredLanguage.EN, "Feta Cheese");
        saveLocalization(plain, PreferredLanguage.TR, "Feta Peyniri");
        saveLocalization(crumbled, PreferredLanguage.EN, "Prepared Feta Cheese");
        saveLocalization(crumbled, PreferredLanguage.TR, "Hazırlanmış Beyaz Peynir");
        var existingAlias = new FoodItemSearchAliasEntity();
        existingAlias.setFoodItem(crumbled);
        existingAlias.setAlias("feta cheese");
        existingAlias.setNormalizedAlias("feta cheese");
        existingAlias.setLanguage(PreferredLanguage.EN);
        existingAlias.setAliasType(FoodSearchAliasType.ADMIN_MANUAL);
        existingAlias.setSource("test-preserved-external-alias");
        foodItemSearchAliasRepository.saveAndFlush(existingAlias);
        entityManager.clear();
        for (var region : List.of(MarketRegion.UK_IE, MarketRegion.TR)) {
            var english = new FoodSearchCriteriaDto();
            english.setQuery("Feta Cheese");
            english.setPreferredLanguage(PreferredLanguage.EN);
            english.setMarketRegion(region);
            var results = foodItemService.searchFoodItems(english, 0, 25).getContent();
            assertEquals(Set.of(plain.getId(), crumbled.getId()),
                    Set.copyOf(results.stream().map(p -> p.getId()).toList()));
            assertEquals(2, results.size());
            assertEquals(265.0, results.stream().filter(p -> p.getId().equals(plain.getId())).findFirst().orElseThrow().getCalories());
            assertEquals(273.0, results.stream().filter(p -> p.getId().equals(crumbled.getId())).findFirst().orElseThrow().getCalories());
            var turkish = new FoodSearchCriteriaDto();
            turkish.setQuery("Feta Peyniri");
            turkish.setPreferredLanguage(PreferredLanguage.TR);
            turkish.setMarketRegion(region);
            var tr = foodItemService.searchFoodItems(turkish, 0, 25).getContent();
            assertTrue(tr.stream().anyMatch(p -> p.getId().equals(plain.getId()) && p.getProductName().equals("Feta Peyniri")));
        }
        assertEquals(1, foodItemSearchAliasRepository.count());
        assertEquals(2, foodItemRepository.count());
        var explicit = new FoodSearchCriteriaDto();
        explicit.setQuery("whole milk crumbled");
        explicit.setPreferredLanguage(PreferredLanguage.EN);
        explicit.setMarketRegion(MarketRegion.UK_IE);
        assertEquals(List.of(crumbled.getId()), foodItemService.searchFoodItems(explicit, 0, 25).getContent().stream().map(p -> p.getId()).toList());
    }

    private void saveLocalization(
            FoodItemEntity product,
            PreferredLanguage language,
            String displayName
    ) {
        FoodItemLocalizationEntity localization = new FoodItemLocalizationEntity();
        localization.setFoodItem(product);
        localization.setLanguage(language);
        localization.setDisplayName(displayName);
        localization.setShortDisplayName(displayName);
        localization.setSource("test");
        localization.setActive(true);
        foodItemLocalizationRepository.save(localization);
    }
    private record SearchReadinessExpectation(
            String query,
            PreferredLanguage language,
            MarketRegion marketRegion,
            String expectedFirstDisplayName,
            int minimumResults
    ) {
    }
    @Test
    void searchFoodItems_whenCanonicalPrimaryResolved_hidesAlternativesWithoutDeletingThem() {
        String canonicalKey = "GLOBAL:GENERIC_INGREDIENT:RAW:banana";
        FoodItemEntity primary = product("Banana raw", "canonical-1", VerificationStatus.VERIFIED);
        primary.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        primary.setCanonicalFoodKey(canonicalKey);
        primary.setMarketRegion(MarketRegion.GLOBAL);
        FoodItemEntity alternate = product("Bananas raw", "canonical-2", VerificationStatus.VERIFIED);
        alternate.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        alternate.setCanonicalFoodKey(canonicalKey);
        alternate.setMarketRegion(MarketRegion.GLOBAL);
        foodItemRepository.saveAllAndFlush(List.of(primary, alternate));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("banana");
        FoodProductSearchPageDto unresolved = foodItemService.searchFoodItems(criteria, 0, 25);
        assertEquals(2, unresolved.getContent().size());

        FoodCanonicalResolutionEntity resolution = new FoodCanonicalResolutionEntity();
        resolution.setCanonicalFoodKey(canonicalKey);
        resolution.setPrimaryFoodItem(primary);
        resolution.setResolvedBy("admin@test.com");
        foodCanonicalResolutionRepository.saveAndFlush(resolution);
        entityManager.clear();

        FoodProductSearchPageDto resolved = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, resolved.getContent().size());
        assertEquals(primary.getId(), resolved.getContent().get(0).getId());
        assertEquals(2, foodItemRepository.count());
    }
    @Test
    void searchFoodItems_whenResolvedPrimaryBecomesRejected_fallsBackToEligibleAlternate() {
        String canonicalKey = "GLOBAL:GENERIC_INGREDIENT:RAW:banana";
        FoodItemEntity primary = product("Banana raw", "stale-primary", VerificationStatus.VERIFIED);
        primary.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        primary.setCanonicalFoodKey(canonicalKey);
        FoodItemEntity alternate = product("Bananas raw", "eligible-alternate", VerificationStatus.VERIFIED);
        alternate.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        alternate.setCanonicalFoodKey(canonicalKey);
        foodItemRepository.saveAllAndFlush(List.of(primary, alternate));

        FoodCanonicalResolutionEntity resolution = new FoodCanonicalResolutionEntity();
        resolution.setCanonicalFoodKey(canonicalKey);
        resolution.setPrimaryFoodItem(primary);
        resolution.setResolvedBy("admin@test.com");
        foodCanonicalResolutionRepository.saveAndFlush(resolution);
        primary.setVerificationStatus(VerificationStatus.REJECTED);
        foodItemRepository.saveAndFlush(primary);
        entityManager.clear();

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("banana");
        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(alternate.getId(), result.getContent().get(0).getId());
    }

    @Test
    void searchFoodItems_whenResolvedPrimaryGetsBlockingIssue_fallsBackToEligibleAlternate() {
        String canonicalKey = "GLOBAL:GENERIC_INGREDIENT:RAW:banana";
        FoodItemEntity primary = product("Banana raw", "blocked-primary", VerificationStatus.VERIFIED);
        primary.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        primary.setCanonicalFoodKey(canonicalKey);
        FoodItemEntity alternate = product("Bananas raw", "eligible-alternate-2", VerificationStatus.VERIFIED);
        alternate.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        alternate.setCanonicalFoodKey(canonicalKey);
        foodItemRepository.saveAllAndFlush(List.of(primary, alternate));

        FoodCanonicalResolutionEntity resolution = new FoodCanonicalResolutionEntity();
        resolution.setCanonicalFoodKey(canonicalKey);
        resolution.setPrimaryFoodItem(primary);
        resolution.setResolvedBy("admin@test.com");
        foodCanonicalResolutionRepository.saveAndFlush(resolution);
        FoodProductQualityIssueEntity issue = new FoodProductQualityIssueEntity();
        issue.setFoodItem(primary);
        issue.setIssueType(FoodProductQualityIssue.SUSPICIOUS_MACROS);
        issue.setIdentifier("blocked-primary");
        issue.setReason("test blocking issue");
        issue.setResolved(false);
        foodProductQualityIssueRepository.saveAndFlush(issue);
        entityManager.clear();

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("banana");
        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(alternate.getId(), result.getContent().get(0).getId());
    }
    @Test
    void trEuBrandedFixture_preservesRegionalIntentAndLocalizedSourceNames() throws Exception {
        FoodProductImportServiceImpl importService = new FoodProductImportServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemSearchAliasRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                Mockito.mock(FoodProductQualityIssueTracker.class),
                Mockito.mock(FoodProductEvidenceService.class),
                new ObjectMapper()
        );
        byte[] brandedSeed;
        try (var input = getClass().getResourceAsStream("/tr-eu-branded-search-v1.csv")) {
            assertTrue(input != null, "TR/EU branded search fixture is missing");
            brandedSeed = input.readAllBytes();
        }

        var importResult = importService.importCsv(
                new MockMultipartFile("file", "tr-eu-branded-search-v1.csv", "text/csv", brandedSeed),
                "s9-search-gate@grun.app"
        );
        assertEquals(10, importResult.getSavedRows());
        assertEquals(0, importResult.getSkippedRows());
        publishMassBasedSearchFixturesAfterReview();
        entityManager.flush();
        entityManager.clear();

        List<RegionalBrandedSearchExpectation> expectations = List.of(
                new RegionalBrandedSearchExpectation("ülker kakaolu bisküvi", "8690504011507", MarketRegion.TR, PreferredLanguage.TR),
                new RegionalBrandedSearchExpectation("migros mısır patlağı", "8681161102998", MarketRegion.TR, PreferredLanguage.TR),
                new RegionalBrandedSearchExpectation("migros söğüş karides", "8690251481165", MarketRegion.TR, PreferredLanguage.TR),
                new RegionalBrandedSearchExpectation("yer fıstığı ezmesi", "8683347037087", MarketRegion.TR, PreferredLanguage.TR),
                new RegionalBrandedSearchExpectation("eti form tam çavdarlı", "8690526013527", MarketRegion.TR, PreferredLanguage.TR),
                new RegionalBrandedSearchExpectation("carrefour velouté", "0000112407464", MarketRegion.EU, PreferredLanguage.EN),
                new RegionalBrandedSearchExpectation("leclerc brioche", "0202526025912", MarketRegion.EU, PreferredLanguage.EN),
                new RegionalBrandedSearchExpectation("aldi digestives", "00302784", MarketRegion.EU, PreferredLanguage.EN),
                new RegionalBrandedSearchExpectation("hacendado seitán", "00001522", MarketRegion.EU, PreferredLanguage.EN),
                new RegionalBrandedSearchExpectation("bofrost brochette", "00001307", MarketRegion.EU, PreferredLanguage.EN)
        );
        for (RegionalBrandedSearchExpectation expectation : expectations) {
            FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(expectation.query());
            criteria.setMarketRegion(expectation.marketRegion());
            criteria.setPreferredLanguage(expectation.language());
            var results = foodItemService.searchFoodItems(criteria, 0, 10).getContent();
            assertTrue(!results.isEmpty(), expectation.query());
            assertEquals(expectation.expectedBarcode(), results.get(0).getBarcode(), expectation.query());
        }
    }
    @Test
    void searchFoodItems_usesMergedMarketAvailabilityWithoutDuplicatingProduct() {
        FoodItemEntity sharedProduct = product("Shared Market Yogurt", "8690000000001", VerificationStatus.VERIFIED);
        sharedProduct.setSourceKey("barcode:8690000000001");
        sharedProduct.setMarketRegion(MarketRegion.EU);
        sharedProduct.setMarketRegions(Set.of(MarketRegion.EU, MarketRegion.TR));
        foodItemRepository.saveAndFlush(sharedProduct);
        entityManager.clear();

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("shared market yogurt");
        criteria.setMarketRegion(MarketRegion.TR);
        criteria.setPreferredLanguage(PreferredLanguage.TR);

        var result = foodItemService.searchFoodItems(criteria, 0, 10);

        assertEquals(1, result.getContent().size());
        assertEquals("8690000000001", result.getContent().get(0).getBarcode());
        assertEquals(Set.of(MarketRegion.EU, MarketRegion.TR), result.getContent().get(0).getMarketRegions());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "TR,EN", "TR,TR", "UK_IE,EN", "UK_IE,TR", "EU,EN", "EU,TR"
    })
    void categoryBrowseSharedMarketsHaveUniqueTotalsAndStablePages(
            MarketRegion region, PreferredLanguage language) {
        var parent = foodCategoryRepository.save(category("browse-drinks", null, 1));
        var child = foodCategoryRepository.save(category("browse-dairy", parent, 2));
        var otherCategory = foodCategoryRepository.save(category("browse-fruit", null, 3));
        var local = product("Local Drink", "9940000000001", VerificationStatus.VERIFIED);
        local.setMarketRegion(region);
        local.setUsageCount(20L);
        var shared = product("Shared Drink", "9940000000002", VerificationStatus.VERIFIED);
        shared.setMarketRegion(MarketRegion.GLOBAL);
        shared.setMarketRegions(Set.of(MarketRegion.TR, MarketRegion.UK_IE, MarketRegion.EU, MarketRegion.GLOBAL));
        shared.setUsageCount(10L);
        var fallback = product("Generic Drink", null, VerificationStatus.VERIFIED);
        fallback.setSourceKey("fixture:category-browse:global");
        fallback.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        fallback.setMarketRegion(MarketRegion.GLOBAL);
        fallback.setUsageCount(100_000L);
        var wrongCategory = product("Fruit Drink", "9940000000003", VerificationStatus.VERIFIED);
        wrongCategory.setMarketRegion(region);
        var wrongState = product("Raw Drink", "9940000000004", VerificationStatus.VERIFIED);
        wrongState.setMarketRegion(region);
        for (var item : List.of(local, shared, fallback, wrongCategory)) {
            item.setPreparationState(FoodPreparationState.PREPARED);
        }
        wrongState.setPreparationState(FoodPreparationState.RAW);
        foodItemRepository.saveAllAndFlush(List.of(local, shared, fallback, wrongCategory, wrongState));
        foodItemCategoryRepository.saveAllAndFlush(List.of(
                categoryAssignment(local, parent, true), categoryAssignment(shared, child, true),
                categoryAssignment(fallback, child, true), categoryAssignment(wrongCategory, otherCategory, true),
                categoryAssignment(wrongState, child, true)));
        for (var item : List.of(local, shared, fallback)) {
            saveLocalization(item, PreferredLanguage.EN, item.getName());
            saveLocalization(item, PreferredLanguage.TR, "Haz\u0131r " + item.getName());
        }
        var criteria = new FoodSearchCriteriaDto();
        criteria.setMarketRegion(region);
        criteria.setPreferredLanguage(language);
        criteria.setCategoryId(parent.getId());
        criteria.setPreparationState(FoodPreparationState.PREPARED);
        var expected = List.of(local.getId(), shared.getId(), fallback.getId());
        var all = foodItemService.searchFoodItems(criteria, 0, 100);
        assertEquals(3, all.getTotalElements(), "A product belongs to its best market once, not every available market");
        assertEquals(expected, all.getContent().stream().map(p -> p.getId()).toList());
        assertEquals(language == PreferredLanguage.TR ? "Haz\u0131r Local Drink" : "Local Drink",
                all.getContent().get(0).getDisplayName());
        for (int size : List.of(1, 2)) {
            var collected = new java.util.ArrayList<Long>();
            int pages = (expected.size() + size - 1) / size;
            for (int index = 0; index < pages; index++) {
                var page = foodItemService.searchFoodItems(criteria, index, size);
                assertEquals(3, page.getTotalElements());
                assertEquals(pages, page.getTotalPages());
                assertEquals(index == 0, page.getFirst());
                assertEquals(index == pages - 1, page.getLast());
                page.getContent().forEach(item -> collected.add(item.getId()));
            }
            assertEquals(expected, collected, "No repeated IDs or missing products across pages");
            var empty = foodItemService.searchFoodItems(criteria, pages, size);
            assertTrue(empty.getContent().isEmpty());
            assertEquals(3, empty.getTotalElements());
        }
        criteria.setSortBy("usageCount");
        criteria.setSortOrder("desc");
        var explicitSort = foodItemService.searchFoodItems(criteria, 0, 100);
        assertEquals(3, explicitSort.getTotalElements());
        assertEquals(List.of(fallback.getId(), local.getId(), shared.getId()),
                explicitSort.getContent().stream().map(p -> p.getId()).toList());
    }
    @Test
    void ukIeBrandedFixture_importsAndKeepsRelevantProductsAheadOfNoise() throws Exception {
        FoodProductImportServiceImpl importService = new FoodProductImportServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemSearchAliasRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                Mockito.mock(FoodProductQualityIssueTracker.class),
                Mockito.mock(FoodProductEvidenceService.class),
                new ObjectMapper()
        );
        byte[] genericSeed;
        byte[] brandedSeed;
        try (var genericInput = getClass().getResourceAsStream("/generic-food-approved-seed-v1.csv");
             var brandedInput = getClass().getResourceAsStream("/uk-ie-branded-search-v1.csv")) {
            assertTrue(genericInput != null, "Generic manifest seed is missing");
            assertTrue(brandedInput != null, "UK/IE branded search fixture is missing");
            genericSeed = genericInput.readAllBytes();
            brandedSeed = brandedInput.readAllBytes();
        }

        var genericResult = importService.importCsv(
                new MockMultipartFile("file", "generic-food-approved-seed-v1.csv", "text/csv", genericSeed),
                "s8-search-gate@grun.app"
        );
        var brandedResult = importService.importCsv(
                new MockMultipartFile("file", "uk-ie-branded-search-v1.csv", "text/csv", brandedSeed),
                "s8-search-gate@grun.app"
        );
        assertEquals(146, genericResult.getSavedRows());
        assertEquals(9, brandedResult.getSavedRows());
        assertEquals(0, brandedResult.getSkippedRows());
        publishMassBasedSearchFixturesAfterReview();
        entityManager.flush();
        entityManager.clear();

        List<BrandedSearchExpectation> expectations = List.of(
                new BrandedSearchExpectation("tesco melon medley", "00004753"),
                new BrandedSearchExpectation("tesco butter beans", "0004948822069"),
                new BrandedSearchExpectation("dunnes chicken breast", "0099874302384"),
                new BrandedSearchExpectation("dunnes salted cashew nuts", "0023710316605"),
                new BrandedSearchExpectation("sainsbury sliced white bread", "00014137")
        );
        for (BrandedSearchExpectation expectation : expectations) {
            FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(expectation.query());
            criteria.setMarketRegion(MarketRegion.UK_IE);
            criteria.setPreferredLanguage(PreferredLanguage.EN);
            var results = foodItemService.searchFoodItems(criteria, 0, 10).getContent();
            assertTrue(!results.isEmpty(), expectation.query());
            assertEquals(expectation.expectedBarcode(), results.get(0).getBarcode(), expectation.query());
        }

        FoodSearchCriteriaDto broadMilk = new FoodSearchCriteriaDto();
        broadMilk.setQuery("milk");
        broadMilk.setMarketRegion(MarketRegion.UK_IE);
        broadMilk.setPreferredLanguage(PreferredLanguage.EN);
        var milkResults = foodItemService.searchFoodItems(broadMilk, 0, 10).getContent();
        // Legacy USDA milk fixtures contain sodium in source units not normalized for this import.
        // Keep them in review; do not relax publication to obtain a generic result.
        assertEquals(1, milkResults.size());
        assertEquals(FoodCatalogType.BRANDED_PRODUCT, milkResults.get(0).getCatalogType());
        assertTrue(milkResults.stream().anyMatch(product -> "0000493141402".equals(product.getBarcode())));
    }

    @Test
    void brandSeparatorVariants_findSameProductsWithoutMergingSkus() {
        List<String> brands = List.of("Vit Hit", "Vit-Hit", "Vit\u2022Hit", "VitHit");
        for (int i = 0; i < brands.size(); i++) {
            FoodItemEntity item = product("Hydration flavour " + i, "503403300000" + i, VerificationStatus.VERIFIED);
            item.setBrand(brands.get(i));
            foodItemRepository.save(item);
        }
        entityManager.flush();
        for (String query : brands) {
            FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(query);
            var results = foodItemService.searchFoodItems(criteria, 0, 20);
            assertEquals(4, results.getTotalElements(), query);
            assertEquals(4, results.getContent().stream().map(p -> p.getBarcode()).distinct().count());
        }
        FoodSearchCriteriaDto filter = new FoodSearchCriteriaDto();
        filter.setBrand("VitHit");
        assertEquals(4, foodItemService.searchFoodItems(filter, 0, 20).getTotalElements());
    }

    @Test
    void brandSeparatorMatching_doesNotCollapseNumericProductNamesOrExposeHiddenProducts() {
        FoodItemEntity visible = product("Milk 1.5%", "123456700001", VerificationStatus.VERIFIED);
        visible.setBrand("Vit-Hit");
        foodItemRepository.save(visible);
        FoodItemEntity hidden = product("Milk 15%", "123456700002", VerificationStatus.REJECTED);
        hidden.setBrand("Vit Hit");
        foodItemRepository.save(hidden);
        FoodItemEntity custom = product("Private drink", "123456700003", VerificationStatus.VERIFIED);
        custom.setBrand("VitHit");
        custom.setIsCustom(true);
        foodItemRepository.saveAndFlush(custom);
        FoodSearchCriteriaDto query = new FoodSearchCriteriaDto();
        query.setQuery("vithit");
        assertEquals(1, foodItemService.searchFoodItems(query, 0, 20).getTotalElements());
        query.setQuery("Milk 1.5%");
        assertTrue(foodItemService.searchFoodItems(query, 0, 20).getContent().stream()
                .noneMatch(p -> "123456700002".equals(p.getBarcode())));
    }

    @Test
    void reviewedBrandAbbreviationsFindLegacyAndCanonicalRows() {
        var legacy = product("Oat drink", "9910000000001", VerificationStatus.VERIFIED);
        legacy.setBrand("Marks & Spencer");
        var canonical = product("Rice drink", "9910000000002", VerificationStatus.VERIFIED);
        canonical.setBrand("M&S");
        foodItemRepository.saveAll(List.of(legacy, canonical));
        entityManager.flush();
        for (String term : List.of("Marks & Spencer", "M&S", "Marks and Spencer")) {
            FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(term);
            var page = foodItemService.searchFoodItems(criteria, 0, 20);
            assertEquals(2, page.getTotalElements(), term);
            assertTrue(page.getContent().stream().allMatch(item -> "M&S".equals(item.getBrand())));
            criteria.setQuery(null);
            criteria.setBrand(term);
            assertEquals(2, foodItemService.searchFoodItems(criteria, 0, 20).getTotalElements(), term);
        }
        assertEquals("Marks & Spencer", legacy.getBrand());
    }

    @Test
    void categoryFilterIncludesDirectChildrenAndExcludesUnrelatedProducts() {
        FoodCategoryEntity parent = category("drinks", null, 1);
        FoodCategoryEntity child = category("milk", parent, 2);
        FoodCategoryEntity unrelated = category("fruit", null, 3);
        foodCategoryRepository.saveAllAndFlush(List.of(parent, child, unrelated));

        FoodItemEntity parentProduct = product("Mixed Drink", "9940000000001", VerificationStatus.VERIFIED);
        FoodItemEntity childProduct = product("Whole Milk", "9940000000002", VerificationStatus.VERIFIED);
        FoodItemEntity unrelatedProduct = product("Fresh Apple", "9940000000003", VerificationStatus.VERIFIED);
        parentProduct.setMarketRegion(MarketRegion.TR);
        childProduct.setMarketRegion(MarketRegion.TR);
        unrelatedProduct.setMarketRegion(MarketRegion.TR);
        unrelatedProduct.setCatalogType(FoodCatalogType.LOCAL_DISH);
        foodItemRepository.saveAllAndFlush(List.of(parentProduct, childProduct, unrelatedProduct));
        foodItemCategoryRepository.saveAllAndFlush(List.of(
                categoryAssignment(parentProduct, parent, true),
                categoryAssignment(childProduct, child, true),
                categoryAssignment(unrelatedProduct, unrelated, true)
        ));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setCategoryId(parent.getId());
        criteria.setMarketRegion(MarketRegion.TR);
        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 20);

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().anyMatch(item -> "9940000000001".equals(item.getBarcode())));
        assertTrue(result.getContent().stream().anyMatch(item -> "9940000000002".equals(item.getBarcode())));
        assertTrue(result.getContent().stream().noneMatch(item -> "9940000000003".equals(item.getBarcode())));
    }

    private FoodCategoryEntity category(String slug, FoodCategoryEntity parent, int order) {
        FoodCategoryEntity category = new FoodCategoryEntity();
        category.setSlug(slug);
        category.setNameEn(slug);
        category.setNameTr(slug);
        category.setParent(parent);
        category.setSortOrder(order);
        category.setActive(true);
        return category;
    }

    private FoodItemCategoryEntity categoryAssignment(
            FoodItemEntity product, FoodCategoryEntity category, boolean primary) {
        FoodItemCategoryEntity assignment = new FoodItemCategoryEntity();
        assignment.setFoodItem(product);
        assignment.setCategory(category);
        assignment.setPrimaryCategory(primary);
        assignment.setAssignmentSource(com.grun.calorietracker.enums.FoodCategoryAssignmentSource.IMPORT);
        assignment.setConfidenceScore(95);
        assignment.setReviewed(false);
        return assignment;
    }

    @Test
    void rangeBrandFilterKeepsLegacyRowsWithoutIncludingWholeRetailer() {
        var plain = product("Oat drink", "9920000000001", VerificationStatus.VERIFIED);
        plain.setBrand("Waitrose");
        var range = product("Rice drink", "9920000000002", VerificationStatus.VERIFIED);
        range.setBrand("Waitrose,Essential Waitrose");
        var legacy = product("Soya drink", "9920000000003", VerificationStatus.VERIFIED);
        legacy.setBrand("Essential Waitrose & Partners");
        foodItemRepository.saveAll(List.of(plain, range, legacy));
        entityManager.flush();
        for (String term : List.of("Essential Waitrose", "Essential Waitrose & Partners")) {
            FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
            criteria.setBrand(term);
            var page = foodItemService.searchFoodItems(criteria, 0, 20);
            assertEquals(2, page.getTotalElements(), term);
            assertTrue(page.getContent().stream().noneMatch(item -> plain.getBarcode().equals(item.getBarcode())));
            assertTrue(page.getContent().stream().anyMatch(item -> "Waitrose, Essential Waitrose".equals(item.getBrand())));
        }
        assertEquals("Waitrose,Essential Waitrose", range.getBrand());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "Milk,S\u00fct,Sheep Milk,Ricotta cheese made from milk",
            "Banana,Muz,Banana Chips,Banana Bread",
            "Broccoli,Brokoli,Broccoli Raab,Broccoli Soup",
            "Rice,Pirin\u00e7,Rice Flour,Rice Cake",
            "Chicken Breast,Tavuk G\u00f6\u011fs\u00fc,Chicken Breast Soup,Chicken Breast Broth"
    })
    void coreSearch_directFoodBeatsGenericVariantsAndNoisyAliases(String english, String turkish,
            String variantName, String derivativeName) {
        var direct = product(english, "9930000000001", VerificationStatus.VERIFIED);
        direct.setMarketRegion(MarketRegion.TR);
        var variant = product(variantName, "9930000000002", VerificationStatus.VERIFIED);
        variant.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        variant.setMarketRegion(MarketRegion.GLOBAL);
        variant.setUsageCount(10000L);
        var derivative = product(derivativeName, "9930000000003", VerificationStatus.VERIFIED);
        derivative.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        derivative.setMarketRegion(MarketRegion.GLOBAL);
        derivative.setUsageCount(20000L);
        foodItemRepository.saveAllAndFlush(List.of(direct, variant, derivative));
        saveLocalization(direct, turkish);
        var alias = new FoodItemSearchAliasEntity();
        alias.setFoodItem(derivative);
        alias.setAlias(turkish);
        alias.setNormalizedAlias(com.grun.calorietracker.service.support.FoodProductNormalizationRules.normalizeSearchAlias(turkish));
        alias.setLanguage(PreferredLanguage.TR);
        alias.setAliasType(FoodSearchAliasType.COMMON_NAME);
        alias.setSource("test-noisy-alias");
        alias.setActive(true);
        foodItemSearchAliasRepository.saveAndFlush(alias);
        for (String queryText : List.of(english, turkish)) {
            var criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(queryText);
            criteria.setPreferredLanguage(PreferredLanguage.TR);
            criteria.setMarketRegion(MarketRegion.TR);
            var result = foodItemService.searchFoodItems(criteria, 0, 10);
            assertEquals(direct.getId(), result.getContent().get(0).getId(), queryText);
            assertEquals(turkish, result.getContent().get(0).getProductName());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "banana,Raw Banana,Muz,RAW,EN,UK_IE",
            "banana,Raw Banana,Muz,RAW,TR,TR",
            "broccoli,Raw Broccoli,Brokoli,RAW,EN,UK_IE",
            "broccoli,Raw Broccoli,Brokoli,RAW,TR,TR",
            "milk,Prepared Whole Milk,S\u00fct,PREPARED,EN,UK_IE",
            "milk,Prepared Whole Milk,S\u00fct,PREPARED,TR,TR",
            "chicken breast,Raw Chicken Breast,Tavuk G\u00f6\u011fs\u00fc,RAW,EN,UK_IE",
            "chicken breast,Raw Chicken Breast,Tavuk G\u00f6\u011fs\u00fc,RAW,TR,TR",
            "rice,Raw White Rice,Pirin\u00e7,RAW,EN,UK_IE",
            "rice,Raw White Rice,Pirin\u00e7,RAW,TR,TR"
    })
    void coreSearch_preparationQualifiedGenericSurvivesExactBrandedCrowding(
            String english, String genericName, String turkish, FoodPreparationState state,
            PreferredLanguage language, MarketRegion region) {
        var generic = product(genericName, null, VerificationStatus.VERIFIED);
        generic.setDisplayName(genericName);
        generic.setShortDisplayName(genericName);
        generic.setSourceKey("fixture:qualified-core:" + english);
        generic.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        generic.setMarketRegion(MarketRegion.GLOBAL);
        generic.setPreparationState(state);
        foodItemRepository.saveAndFlush(generic);
        saveLocalization(generic, PreferredLanguage.EN, genericName);
        saveLocalization(generic, switch (english) {
            case "milk" -> "Haz\u0131rlanm\u0131\u015f Tam Ya\u011fl\u0131 S\u00fct";
            case "rice" -> "\u00c7i\u011f Beyaz Pirin\u00e7";
            default -> "\u00c7i\u011f " + turkish;
        });
        for (int i = 0; i < 12; i++) {
            var branded = product(english, "99400000000" + String.format("%02d", i), VerificationStatus.VERIFIED);
            branded.setBrand("Retail Brand");
            branded.setMarketRegion(region);
            branded.setUsageCount(100000L);
            branded.setSearchSelectionCount(100000L);
            foodItemRepository.saveAndFlush(branded);
            saveLocalization(branded, turkish);
        }
        var criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(language == PreferredLanguage.TR ? turkish : english);
        criteria.setPreferredLanguage(language);
        criteria.setMarketRegion(region);
        var first = foodItemService.searchFoodItems(criteria, 0, 5);
        assertEquals(13, first.getTotalElements());
        assertEquals(generic.getId(), first.getContent().get(0).getId());
        assertEquals(state, first.getContent().get(0).getPreparationState());
        var second = foodItemService.searchFoodItems(criteria, 1, 5);
        var firstIds = first.getContent().stream().map(p -> p.getId()).collect(java.util.stream.Collectors.toSet());
        assertTrue(second.getContent().stream().noneMatch(p -> firstIds.contains(p.getId())));

        criteria.setBrand("Retail Brand");
        var filtered = foodItemService.searchFoodItems(criteria, 0, 5);
        assertEquals(12, filtered.getTotalElements());
        assertTrue(filtered.getContent().stream().allMatch(p -> p.getCatalogType() == FoodCatalogType.BRANDED_PRODUCT));
        criteria.setBrand(null);
        criteria.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        assertEquals(12, foodItemService.searchFoodItems(criteria, 0, 5).getTotalElements());
        criteria.setCatalogType(null);
        criteria.setSortBy("usageCount");
        criteria.setSortOrder("desc");
        assertEquals(FoodCatalogType.BRANDED_PRODUCT,
                foodItemService.searchFoodItems(criteria, 0, 5).getContent().get(0).getCatalogType());
        var sortedFirstIds = foodItemService.searchFoodItems(criteria, 0, 5).getContent().stream()
                .map(p -> p.getId()).collect(java.util.stream.Collectors.toSet());
        assertTrue(foodItemService.searchFoodItems(criteria, 1, 5).getContent().stream()
                .noneMatch(p -> sortedFirstIds.contains(p.getId())));
        criteria.setSortBy(null);
        criteria.setSortOrder(null);
        criteria.setPreparationState(state);
        assertEquals(List.of(generic.getId()), foodItemService.searchFoodItems(criteria, 0, 5).getContent()
                .stream().map(p -> p.getId()).toList());
        assertEquals(genericName, foodItemRepository.findById(generic.getId()).orElseThrow().getName());
    }

    private record BrandedSearchExpectation(String query, String expectedBarcode) {
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> literalQueryCases() {
        var queries = List.of("%", "50%", "_", "shake_", "!", "bang!%", "path\\shake", "chef's", "ya\u011f_%",
                "shake_ juice", "bang!% shake");
        var direct = queries.stream().flatMap(text -> java.util.stream.Stream.of(false, true)
                .map(indexed -> org.junit.jupiter.params.provider.Arguments.of(text, "NAME", indexed)));
        var fields = List.of("DISPLAY", "SHORT", "BRAND", "ALIAS", "LOCALIZATION").stream()
                .flatMap(field -> java.util.stream.Stream.of(false, true)
                        .map(indexed -> org.junit.jupiter.params.provider.Arguments.of("50%", field, indexed)));
        return java.util.stream.Stream.concat(direct, fields);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("literalQueryCases")
    void userLikeCharactersRemainLiteralAcrossSearchFields(String text, String field, boolean indexed) {
        var target = product("Neutral Target", "9940000000101", VerificationStatus.VERIFIED);
        String distractor = text.replace("%", "X").replace("_", "X").replace("\\", "");
        if (distractor.equals(text)) {
            distractor = text.replace("!", "X").replace("'", "");
        }
        var noise = product(distractor + " Noise", "9940000000102", VerificationStatus.VERIFIED);
        switch (field) {
            case "NAME" -> target.setName(text + " Exact");
            case "DISPLAY" -> target.setDisplayName(text + " Exact");
            case "SHORT" -> target.setShortDisplayName(text + " Exact");
            case "BRAND" -> target.setBrand(text + " Exact");
            default -> { }
        }
        var category = foodCategoryRepository.save(category("literal-search", null, 1));
        foodItemRepository.saveAllAndFlush(List.of(target, noise));
        foodItemCategoryRepository.saveAllAndFlush(List.of(
                categoryAssignment(target, category, true), categoryAssignment(noise, category, true)));
        if (field.equals("ALIAS")) {
            var alias = new FoodItemSearchAliasEntity();
            alias.setFoodItem(target);
            alias.setAlias(text + " Exact");
            alias.setNormalizedAlias(FoodProductNormalizationRules.normalizeSearchAlias(alias.getAlias()));
            alias.setLanguage(PreferredLanguage.EN);
            alias.setAliasType(FoodSearchAliasType.SYNONYM);
            alias.setSource("literal-search-fixture");
            alias.setActive(true);
            foodItemSearchAliasRepository.saveAndFlush(alias);
        }
        if (field.equals("LOCALIZATION")) {
            saveLocalization(target, PreferredLanguage.EN, text + " Exact");
        }
        useIndexedSearch(indexed);
        var criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(text);
        criteria.setCategoryId(category.getId());
        criteria.setPreferredLanguage(PreferredLanguage.EN);
        var result = foodItemService.searchFoodItems(criteria, 0, 25);
        assertEquals(List.of(target.getId()), result.getContent().stream().map(p -> p.getId()).toList(),
                "Literal query=" + text + " field=" + field + " indexed=" + indexed);
        assertEquals(1, result.getTotalElements());
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> exactBrandProductCases() {
        return List.of("black coffee", "sade kahve", "Acme black coffee", "black coffee Acme",
                        "Acme sade kahve", "sade kahve Acme", "Acme  black coffee", "black coffee\tAcme",
                        "Acme\tsade\tkahve").stream()
                .flatMap(text -> java.util.stream.Stream.of(false, true)
                        .map(indexed -> org.junit.jupiter.params.provider.Arguments.of(text, indexed)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("exactBrandProductCases")
    void exactBrandProductIntentBeatsPopularDerivative(String text, boolean indexed) {
        var generic = product("Black Coffee", null, VerificationStatus.VERIFIED);
        generic.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        generic.setSourceKey("fixture:coffee-generic");
        generic.setMarketRegion(MarketRegion.GLOBAL);
        var branded = product("Black Coffee", "9940000000201", VerificationStatus.VERIFIED);
        branded.setBrand("Acme");
        var cake = product("Black Coffee Cake", "9940000000202", VerificationStatus.VERIFIED);
        cake.setBrand("Acme");
        cake.setUsageCount(1_000_000L);
        cake.setSearchSelectionCount(1_000_000L);
        var otherBrand = product("Black Coffee", "9940000000203", VerificationStatus.VERIFIED);
        otherBrand.setBrand("Other");
        otherBrand.setUsageCount(1_000_000L);
        var category = foodCategoryRepository.save(category("coffee-intent", null, 1));
        foodItemRepository.saveAllAndFlush(List.of(generic, branded, cake, otherBrand));
        for (var item : List.of(generic, branded, cake, otherBrand)) {
            if (item != generic) {
                item.setMarketRegion(MarketRegion.TR);
            }
            item.setPreparationState(FoodPreparationState.PREPARED);
            saveLocalization(item, PreferredLanguage.EN, item.getName());
            saveLocalization(item, PreferredLanguage.TR, item == cake ? "Sade Kahveli Kek" : "Sade Kahve");
            foodItemCategoryRepository.saveAndFlush(categoryAssignment(item, category, true));
        }
        useIndexedSearch(indexed);
        var criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(text);
        criteria.setCategoryId(category.getId());
        criteria.setPreferredLanguage(text.contains("kahve") ? PreferredLanguage.TR : PreferredLanguage.EN);
        criteria.setMarketRegion(MarketRegion.TR);
        var result = foodItemService.searchFoodItems(criteria, 0, 25);
        boolean brandedIntent = text.contains("Acme");
        assertEquals(brandedIntent ? branded.getId() : generic.getId(), result.getContent().get(0).getId(), text);
        assertEquals(brandedIntent ? 2 : 4, result.getTotalElements());
        var first = foodItemService.searchFoodItems(criteria, 0, 1);
        var second = foodItemService.searchFoodItems(criteria, 1, 1);
        assertEquals(result.getContent().get(0).getId(), first.getContent().get(0).getId());
        assertTrue(!first.getContent().get(0).getId().equals(second.getContent().get(0).getId()));
        assertEquals(result.getTotalElements(), first.getTotalElements());
        assertEquals(result.getTotalElements(), second.getTotalElements());
        if (brandedIntent) {
            assertTrue(result.getContent().stream().noneMatch(p -> p.getId().equals(generic.getId())
                    || p.getId().equals(otherBrand.getId())));
        }
        criteria.setQuery(text.contains("kahve") ? "sade kahve" : "black coffee");
        criteria.setBrand("Acme");
        assertEquals(branded.getId(), foodItemService.searchFoodItems(criteria, 0, 25).getContent().get(0).getId());
        criteria.setSortBy("usageCount");
        criteria.setSortOrder("desc");
        assertEquals(cake.getId(), foodItemService.searchFoodItems(criteria, 0, 25).getContent().get(0).getId(),
                "An explicit user sort still overrides default relevance");
    }

    private void useIndexedSearch(boolean indexed) {
        var provider = indexed
                ? new com.grun.calorietracker.service.support.PostgresFoodSearchCandidateProvider(tokenSearchJdbc) : null;
        if (provider != null) {
            org.springframework.test.util.ReflectionTestUtils.setField(provider, "postgres", true);
        }
        org.springframework.test.util.ReflectionTestUtils.setField(foodItemService, "postgresFoodSearchCandidateProvider", provider);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"50%", "shake_", "bang!%", "path\\shake"})
    void brandFilterDoesNotInterpretUserTextAsWildcard(String text) {
        var target = product("Neutral Drink", "9940000000301", VerificationStatus.VERIFIED);
        target.setBrand(text + " Range");
        var noise = product("Neutral Drink", "9940000000302", VerificationStatus.VERIFIED);
        noise.setBrand(text.replace("%", "X").replace("_", "X").replace("\\", "") + " Range");
        var category = foodCategoryRepository.save(category("literal-brand", null, 1));
        foodItemRepository.saveAllAndFlush(List.of(target, noise));
        foodItemCategoryRepository.saveAllAndFlush(List.of(
                categoryAssignment(target, category, true), categoryAssignment(noise, category, true)));
        var criteria = new FoodSearchCriteriaDto();
        criteria.setCategoryId(category.getId());
        criteria.setBrand(text);
        assertEquals(List.of(target.getId()), foodItemService.searchFoodItems(criteria, 0, 25).getContent()
                .stream().map(p -> p.getId()).toList());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void inactiveTranslationCannotMakeDerivativeAnExactBrandProduct(boolean indexed) {
        var plain = product("Black Coffee", "9940000000401", VerificationStatus.VERIFIED);
        plain.setBrand("Acme");
        var derivative = product("Sade Kahveli Kek", "9940000000402", VerificationStatus.VERIFIED);
        derivative.setBrand("Acme");
        derivative.setUsageCount(1_000_000L);
        var category = foodCategoryRepository.save(category("inactive-name", null, 1));
        foodItemRepository.saveAllAndFlush(List.of(plain, derivative));
        saveLocalization(plain, PreferredLanguage.TR, "Sade Kahve");
        var inactive = new FoodItemLocalizationEntity();
        inactive.setFoodItem(derivative);
        inactive.setLanguage(PreferredLanguage.TR);
        inactive.setDisplayName("Sade Kahve");
        inactive.setActive(false);
        foodItemLocalizationRepository.saveAndFlush(inactive);
        foodItemCategoryRepository.saveAllAndFlush(List.of(
                categoryAssignment(plain, category, true), categoryAssignment(derivative, category, true)));
        useIndexedSearch(indexed);
        var criteria = new FoodSearchCriteriaDto();
        criteria.setCategoryId(category.getId());
        criteria.setQuery("Acme sade kahve");
        criteria.setPreferredLanguage(PreferredLanguage.TR);
        assertEquals(plain.getId(), foodItemService.searchFoodItems(criteria, 0, 25).getContent().get(0).getId());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void plainRiceRanksBeforeCreamEvenWithHighUsageAndLegacyTranslation(boolean legacyBroadLabel) {
        var raw = product("Raw White Rice", null, VerificationStatus.VERIFIED);
        raw.setSourceKey("fixture:plain-rice-raw");
        raw.setPreparationState(FoodPreparationState.RAW);
        var cooked = product("Cooked White Rice", null, VerificationStatus.VERIFIED);
        cooked.setSourceKey("fixture:plain-rice-cooked");
        cooked.setPreparationState(FoodPreparationState.COOKED);
        var cream = product("Cream of Rice Cooked with Water", null, VerificationStatus.VERIFIED);
        cream.setSourceKey("fixture:rice-cream");
        cream.setPreparationState(FoodPreparationState.COOKED);
        cream.setUsageCount(1000000L);
        cream.setSearchSelectionCount(1000000L);
        cream.setQualityScore(100);
        for (var food : List.of(raw, cooked, cream)) {
            food.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
            food.setMarketRegion(MarketRegion.GLOBAL);
        }
        foodItemRepository.saveAllAndFlush(List.of(raw, cooked, cream));
        saveLocalization(raw, "Çiğ Beyaz Pirinç");
        saveLocalization(cooked, "Pişmiş Beyaz Pirinç");
        saveLocalization(cream, legacyBroadLabel ? "Pirinç" : "Suyla Pişmiş Pirinç Kreması");
        for (String text : List.of("rice", "pirinç")) {
            var criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(text);
            criteria.setPreferredLanguage(PreferredLanguage.TR);
            criteria.setMarketRegion(MarketRegion.TR);
            var results = foodItemService.searchFoodItems(criteria, 0, 10).getContent();
            assertEquals(3, results.size());
            assertEquals(Set.of(raw.getId(), cooked.getId()), Set.of(results.get(0).getId(), results.get(1).getId()), text);
            assertEquals(cream.getId(), results.get(2).getId());
        }
        var explicit = new FoodSearchCriteriaDto();
        explicit.setQuery("cream of rice");
        explicit.setPreferredLanguage(PreferredLanguage.EN);
        explicit.setMarketRegion(MarketRegion.UK_IE);
        assertEquals(cream.getId(), foodItemService.searchFoodItems(explicit, 0, 10).getContent().get(0).getId());
    }
    private record RegionalBrandedSearchExpectation(
            String query,
            String expectedBarcode,
            MarketRegion marketRegion,
            PreferredLanguage language
    ) {
    }
    private FoodItemEntity product(String name, String barcode, VerificationStatus verificationStatus) {
        FoodItemEntity product = new FoodItemEntity();
        product.setName(name);
        product.setBarcode(barcode);
        product.setNormalizedBarcode(barcode);
        product.setVerificationStatus(verificationStatus);
        product.setPublicationStatus(com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setCalories(100.0);
        product.setProtein(0.0);
        product.setFat(0.0);
        product.setCarbs(0.0);
        product.setNutritionReferenceUnit(com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100G);
        product.setQualityScore(50);
        product.setUsageCount(0L);
        product.setIsCustom(false);
        return product;
    }

    @Test
    void searchBlocksIncompleteAndInvalidPublishedNutritionWithoutWaitingForIssueRescan() {
        FoodItemEntity valid = product("Milk standard", "9970000000001", VerificationStatus.VERIFIED);
        FoodItemEntity partial = product("Milk partial", "9970000000002", VerificationStatus.VERIFIED);
        partial.setFat(null);
        FoodItemEntity negative = product("Milk negative", "9970000000003", VerificationStatus.VERIFIED);
        negative.setCarbs(-1.0);
        FoodItemEntity nonFinite = product("Milk infinite", "9970000000004", VerificationStatus.VERIFIED);
        nonFinite.setCalories(Double.POSITIVE_INFINITY);
        foodItemRepository.saveAll(List.of(valid, partial, negative, nonFinite));
        entityManager.flush();
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("milk");
        var results = foodItemService.searchFoodItems(criteria, 0, 25).getContent();
        assertEquals(List.of(valid.getId()), results.stream().map(com.grun.calorietracker.dto.FoodProductDto::getId).toList());
    }

    @Test
    void invalidImportRowDoesNotFlushChangesToManagedProductWhileOtherRowsSucceed() {
        FoodItemEntity original = foodItemRepository.save(product("Original Milk", "9970000000011", VerificationStatus.VERIFIED));
        entityManager.flush();
        var importer = new FoodProductImportServiceImpl(foodItemRepository, foodItemLocalizationRepository,
                foodItemSearchAliasRepository, foodItemServingOptionRepository, foodItemServingOptionLocalizationRepository,
                Mockito.mock(FoodProductQualityIssueTracker.class), Mockito.mock(FoodProductEvidenceService.class), new ObjectMapper());
        var file = new MockMultipartFile("file", "invalid-update.csv", "text/csv", """
                barcode,name,calories,protein,fat,carbs,nutrition_reference_unit
                9970000000011,Changed Milk,60,broken,3,5,PER_100G
                9970000000012,Valid Milk,60,3,3,5,PER_100G
                """.getBytes(StandardCharsets.UTF_8));
        var result = importer.importCsv(file, "review@grun.local");
        assertEquals(1, result.getSkippedRows());
        assertEquals(1, result.getSavedRows());
        entityManager.flush();
        entityManager.clear();
        var unchanged = foodItemRepository.findById(original.getId()).orElseThrow();
        assertEquals("Original Milk", unchanged.getName());
        assertEquals(100.0, unchanged.getCalories());
        assertEquals(0.0, unchanged.getProtein());
        assertEquals(2, foodItemRepository.count());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "çiğ tavuk göğsü, Çiğ Tavuk Göğsü, RAW, false",
            "çiğ tavuk göğsü, Çiğ Tavuk Göğsü, RAW, true",
            "tavuk gogsu cig, Çiğ Tavuk Göğsü, RAW, false",
            "tavuk gogsu cig, Çiğ Tavuk Göğsü, RAW, true",
            "soslu tavuk göğsü, Soslu Tavuk Göğsü, PREPARED, false",
            "soslu tavuk göğsü, Soslu Tavuk Göğsü, PREPARED, true",
            "tavuk gogsu soslu, Soslu Tavuk Göğsü, PREPARED, false",
            "tavuk gogsu soslu, Soslu Tavuk Göğsü, PREPARED, true"
    })
    void compoundChickenAliasesMustNotDiscardPreparationOrComposition(
            String query, String label, FoodPreparationState state, boolean indexed) {
        var target = product(state == FoodPreparationState.RAW ? "Raw Chicken Breast" : "Chicken Breast with Sauce",
                null, VerificationStatus.VERIFIED);
        target.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        target.setMarketRegion(MarketRegion.GLOBAL); target.setPreparationState(state);
        var wrong = product(state == FoodPreparationState.RAW ? "Chicken Breast with Sauce" : "Raw Chicken Breast",
                null, VerificationStatus.VERIFIED);
        wrong.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT); wrong.setMarketRegion(MarketRegion.GLOBAL);
        wrong.setPreparationState(state == FoodPreparationState.RAW ? FoodPreparationState.PREPARED : FoodPreparationState.RAW);
        wrong.setUsageCount(99999L);
        foodItemRepository.saveAllAndFlush(List.of(target, wrong));
        saveLocalization(target, PreferredLanguage.TR, label);
        saveLocalization(wrong, PreferredLanguage.TR, state == FoodPreparationState.RAW ? "Soslu Tavuk Göğsü" : "Çiğ Tavuk Göğsü");
        for (var item : List.of(target, wrong)) {
            var alias = new FoodItemSearchAliasEntity(); alias.setFoodItem(item);
            alias.setAlias(item == target ? label : state == FoodPreparationState.RAW ? "Soslu Tavuk Göğsü" : "Çiğ Tavuk Göğsü");
            alias.setNormalizedAlias(FoodProductNormalizationRules.normalizeSearchAlias(alias.getAlias()));
            alias.setLanguage(PreferredLanguage.TR); alias.setAliasType(FoodSearchAliasType.TRANSLATION);
            alias.setSource("synthetic-compound-query-fixture"); foodItemSearchAliasRepository.saveAndFlush(alias);
        }
        useCompoundQueryIndex(indexed);
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(query);
        criteria.setPreferredLanguage(PreferredLanguage.TR); criteria.setMarketRegion(MarketRegion.TR);
        var result = foodItemService.searchFoodItems(criteria, 0, 25);
        assertEquals(List.of(target.getId()), result.getContent().stream()
                .map(com.grun.calorietracker.dto.FoodProductDto::getId).toList(), query);
        assertEquals(label, result.getContent().get(0).getDisplayName());
        assertEquals(state, result.getContent().get(0).getPreparationState());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "Vit-hit Apple, VITHIT, EN, false", "Vit-hit Apple, VITHIT, EN, true",
            "Apple Vit-hit, VITHIT, EN, false", "Apple Vit-hit, VITHIT, EN, true",
            "Vit•hit Elma, VITHIT, TR, false", "Vit•hit Elma, VITHIT, TR, true",
            "Elma Vit-hit, VITHIT, TR, false", "Elma Vit-hit, VITHIT, TR, true",
            "M&S Milk, Marks & Spencer, EN, false", "M&S Milk, Marks & Spencer, EN, true",
            "Milk M&S, Marks & Spencer, EN, false", "Milk M&S, Marks & Spencer, EN, true",
            "M&S Süt, Marks & Spencer, TR, false", "M&S Süt, Marks & Spencer, TR, true",
            "Süt M&S, Marks & Spencer, TR, false", "Süt M&S, Marks & Spencer, TR, true",
            "Marks and Spencer Milk, M&S, EN, false", "Marks and Spencer Milk, M&S, EN, true",
            "Milk Marks and Spencer, M&S, EN, false", "Milk Marks and Spencer, M&S, EN, true",
            "M & S Süt, M&S, TR, false", "M & S Süt, M&S, TR, true",
            "Süt M & S, M&S, TR, false", "Süt M & S, M&S, TR, true",
            "Vit Hit Apple, VITHIT, EN, false", "Vit Hit Apple, VITHIT, EN, true",
            "Apple Vit Hit, VITHIT, EN, false", "Apple Vit Hit, VITHIT, EN, true",
            "Tesco Finest Milk, Tesco Finest, EN, false", "Tesco Finest Milk, Tesco Finest, EN, true",
            "Milk Tesco Finest, Tesco Finest, EN, false", "Milk Tesco Finest, Tesco Finest, EN, true"
    })
    void compoundBrandVariantsFindAndRankTheExactLocalizedProduct(
            String query, String brand, PreferredLanguage language, boolean indexed) {
        boolean apple = brand.equals("VITHIT");
        var exact = product(apple ? "Apple" : "Milk", "9970000000101", VerificationStatus.VERIFIED);
        exact.setBrand(brand); exact.setMarketRegion(MarketRegion.TR);
        var derivative = product(apple ? "Apple Juice" : "Milk Chocolate", "9970000000102", VerificationStatus.VERIFIED);
        derivative.setBrand(brand); derivative.setMarketRegion(MarketRegion.TR); derivative.setUsageCount(99999L);
        var otherBrand = product(exact.getName(), "9970000000103", VerificationStatus.VERIFIED);
        otherBrand.setBrand("Other"); otherBrand.setMarketRegion(MarketRegion.TR);
        foodItemRepository.saveAllAndFlush(List.of(exact, derivative, otherBrand));
        saveLocalization(exact, PreferredLanguage.TR, apple ? "Elma" : "Süt");
        saveLocalization(derivative, PreferredLanguage.TR, apple ? "Elma Suyu" : "Süt Çikolatalı");
        useCompoundQueryIndex(indexed);
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(query);
        criteria.setPreferredLanguage(language); criteria.setMarketRegion(MarketRegion.TR);
        var first = foodItemService.searchFoodItems(criteria, 0, 1);
        assertEquals(2L, first.getTotalElements(), query);
        assertEquals(exact.getId(), first.getContent().get(0).getId(), query);
        assertEquals(language == PreferredLanguage.TR ? apple ? "Elma" : "Süt" : exact.getName(), first.getContent().get(0).getDisplayName());
        var next = foodItemService.searchFoodItems(criteria, 1, 1);
        assertEquals(derivative.getId(), next.getContent().get(0).getId());
        assertEquals(2L, next.getTotalElements());
        assertEquals(brand, foodItemRepository.findById(exact.getId()).orElseThrow().getBrand());
        assertEquals("9970000000101", first.getContent().get(0).getBarcode());
        criteria.setMarketRegion(MarketRegion.UK_IE);
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty(),
                "A matching brand must not bypass the actual market availability");
    }

    private void useCompoundQueryIndex(boolean enabled) {
        var provider = new com.grun.calorietracker.service.support.PostgresFoodSearchCandidateProvider(tokenSearchJdbc);
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "postgres", true);
        org.springframework.test.util.ReflectionTestUtils.setField(foodItemService, "postgresFoodSearchCandidateProvider", enabled ? provider : null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "Cooked premium chicken breast fillet skinless roasted, Roasted Cooked Premium Chicken Breast Fillet Skinless, Boiled Cooked Premium Chicken Breast Fillet Skinless, EN, false",
            "Cooked premium chicken breast fillet skinless roasted, Roasted Cooked Premium Chicken Breast Fillet Skinless, Boiled Cooked Premium Chicken Breast Fillet Skinless, EN, true",
            "pişmiş taze tavuk göğsü fileto derisiz ızgara, Izgara Pişmiş Taze Tavuk Göğsü Fileto Derisiz, Haşlanmış Pişmiş Taze Tavuk Göğsü Fileto Derisiz, TR, false",
            "pişmiş taze tavuk göğsü fileto derisiz ızgara, Izgara Pişmiş Taze Tavuk Göğsü Fileto Derisiz, Haşlanmış Pişmiş Taze Tavuk Göğsü Fileto Derisiz, TR, true",
            "Marks and Spencer cooked chicken breast roasted, Roasted Cooked Chicken Breast, Boiled Cooked Chicken Breast, EN, false",
            "Marks and Spencer cooked chicken breast roasted, Roasted Cooked Chicken Breast, Boiled Cooked Chicken Breast, EN, true"
    })
    void longQueriesRetainTheirFinalPreparationQualifier(
            String query, String targetName, String wrongName, PreferredLanguage language, boolean indexed) {
        var target = product(targetName, "9970000000201", VerificationStatus.VERIFIED);
        var wrong = product(wrongName, "9970000000202", VerificationStatus.VERIFIED);
        target.setBrand("M&S"); wrong.setBrand("M&S"); wrong.setUsageCount(99999L);
        target.setMarketRegion(MarketRegion.GLOBAL); wrong.setMarketRegion(MarketRegion.GLOBAL);
        foodItemRepository.saveAllAndFlush(List.of(target, wrong));
        for (var item : List.of(target, wrong)) {
            saveLocalization(item, language, item.getName());
            var alias = new FoodItemSearchAliasEntity(); alias.setFoodItem(item);
            alias.setAlias(item.getName()); alias.setNormalizedAlias(FoodProductNormalizationRules.normalizeSearchAlias(item.getName()));
            alias.setLanguage(language); alias.setAliasType(FoodSearchAliasType.TRANSLATION);
            alias.setSource("synthetic-long-query-fixture"); foodItemSearchAliasRepository.saveAndFlush(alias);
        }
        useCompoundQueryIndex(indexed);
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(query); criteria.setPreferredLanguage(language);
        var result = foodItemService.searchFoodItems(criteria, 0, 1);
        assertEquals(1L, result.getTotalElements());
        assertEquals(target.getId(), result.getContent().get(0).getId());
        var beyond = foodItemService.searchFoodItems(criteria, 1, 1);
        assertTrue(beyond.getContent().isEmpty()); assertEquals(1L, beyond.getTotalElements());
        Mockito.verifyNoInteractions(fallbackProvider, fallbackPublisher);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"salted,false", "salted,true", "unsalted,false", "unsalted,true"})
    void saltQualifiersMatchWordsNotTheirOppositeOrBrand(String qualifier, boolean indexed) {
        var salted = product("Rice (Salted)", "9970000000301", VerificationStatus.VERIFIED);
        var unsalted = product("Rice-Unsalted", "9970000000302", VerificationStatus.VERIFIED);
        var misleadingBrand = product("Plain Rice", "9970000000303", VerificationStatus.VERIFIED);
        misleadingBrand.setBrand("Salted Foods");
        foodItemRepository.saveAllAndFlush(List.of(salted, unsalted, misleadingBrand));
        useCompoundQueryIndex(indexed);
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(qualifier);
        assertEquals(List.of(qualifier.equals("salted") ? salted.getId() : unsalted.getId()),
                foodItemService.searchFoodItems(criteria, 0, 25).getContent().stream()
                        .map(com.grun.calorietracker.dto.FoodProductDto::getId).toList());
    }

    private void publishMassBasedSearchFixturesAfterReview() {
        // These legacy fixtures document mass-based source profiles but omit the explicit basis column.
        // R1 now correctly requires review; search tests release them via the real publication guard.
        var publication = new com.grun.calorietracker.service.impl.CatalogPublicationServiceImpl(
                foodItemRepository, Mockito.mock(AdminAuditService.class));
        for (FoodItemEntity product : foodItemRepository.findAll()) {
            assertEquals(com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100G, product.getNutritionReferenceUnit());
            if (com.grun.calorietracker.service.support.FoodProductQualityRules.hasCriticalIssue(product)) {
                continue;
            }
            product.setVerificationStatus(VerificationStatus.VERIFIED);
            com.grun.calorietracker.service.support.FoodProductQualityRules.markReviewed(product);
            publication.publish(product.getId(), "fixture-review@grun.local", "Mass-based source fixture reviewed", null);
            for (var option : foodItemServingOptionRepository.findByFoodItemId(product.getId())) {
                option.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);
            }
        }
        entityManager.flush();
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> explicitFoodQualifierCases() {
        String[][] cases = {
                {"sweetened", "Unsweetened Milk", "EN"}, {"unsweetened", "Sweetened Milk", "EN"},
                {"şekerli", "Şekersiz Süt", "TR"}, {"şekersiz", "Şekerli Süt", "TR"},
                {"caffeinated", "Decaffeinated Coffee", "EN"}, {"decaffeinated", "Caffeinated Coffee", "EN"},
                {"kafeinli", "Kafeinsiz Kahve", "TR"}, {"kafeinsiz", "Kafeinli Kahve", "TR"},
                {"raw", "Strawberry Drink", "EN"}, {"cooked", "Uncooked Rice", "EN"},
                {"çiğ", "Çiğdem Bar", "TR"}, {"pişmiş", "Çiğ Pirinç", "TR"}
        };
        return java.util.Arrays.stream(cases).flatMap(values -> java.util.stream.Stream.of(false, true)
                .map(indexed -> org.junit.jupiter.params.provider.Arguments.of(values[0], values[1], values[2], indexed)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("explicitFoodQualifierCases")
    void explicitFoodQualifiersRejectOppositeSubstringAndBrandOnly(
            String qualifier, String oppositeName, String language, boolean indexed) {
        var expected = product("Reference Food (" + qualifier + ")", null, VerificationStatus.VERIFIED);
        var opposite = product(oppositeName, null, VerificationStatus.VERIFIED);
        var brandOnly = product("Neutral Food", null, VerificationStatus.VERIFIED);
        brandOnly.setBrand(qualifier + " Foods");
        foodItemRepository.saveAllAndFlush(List.of(expected, opposite, brandOnly));
        useCompoundQueryIndex(indexed);
        var criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(qualifier);
        criteria.setPreferredLanguage(PreferredLanguage.valueOf(language));
        assertEquals(List.of(expected.getId()), foodItemService.searchFoodItems(criteria, 0, 25)
                .getContent().stream().map(com.grun.calorietracker.dto.FoodProductDto::getId).toList());
        var explicitBrand = new FoodSearchCriteriaDto();
        explicitBrand.setBrand(brandOnly.getBrand());
        assertEquals(List.of(brandOnly.getId()), foodItemService.searchFoodItems(explicitBrand, 0, 25)
                .getContent().stream().map(com.grun.calorietracker.dto.FoodProductDto::getId).toList());
        assertEquals(3, foodItemRepository.count());
        Mockito.verifyNoInteractions(fallbackProvider, fallbackPublisher);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void searchTracksCurrentAliasLocaleCategoryAndServingDependencies(boolean indexed) {
        var item = foodItemRepository.saveAndFlush(product("Dependency Base Food", null, VerificationStatus.VERIFIED));
        var root = new FoodCategoryEntity(); root.setSlug("dependency-root"); root.setNameEn("Root"); root.setNameTr("Ana");
        foodCategoryRepository.saveAndFlush(root);
        var child = new FoodCategoryEntity(); child.setSlug("dependency-child"); child.setNameEn("Child"); child.setNameTr("Alt"); child.setParent(root);
        foodCategoryRepository.saveAndFlush(child);
        var assignment = foodItemCategoryRepository.saveAndFlush(categoryAssignment(item, child, true));
        var alias = new FoodItemSearchAliasEntity(); alias.setFoodItem(item); alias.setAlias("controlledalias");
        alias.setNormalizedAlias("controlledalias"); alias.setLanguage(PreferredLanguage.TR);
        alias.setAliasType(FoodSearchAliasType.ADMIN_MANUAL); alias.setSource("owned-dependency-fixture");
        foodItemSearchAliasRepository.saveAndFlush(alias);
        saveLocalization(item, PreferredLanguage.TR, "Kontrollü Görünen Ad");
        var option = new FoodItemServingOptionEntity(); option.setFoodItem(item); option.setLabel("Reference slice");
        option.setUnitType(FoodServingOptionUnit.SLICE); option.setQuantity(1.0); option.setGramWeight(30.0);
        option.setIsDefault(true); option.setSource(FoodServingOptionSource.ADMIN);
        option.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED); option.setUserVisible(true);
        foodItemServingOptionRepository.saveAndFlush(option);
        useCompoundQueryIndex(indexed);
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("controlledalias");
        criteria.setCategoryId(root.getId()); criteria.setPreferredLanguage(PreferredLanguage.TR);
        var first = foodItemService.searchFoodItems(criteria, 0, 25);
        assertEquals(1, first.getTotalElements());
        assertEquals("Kontrollü Görünen Ad", first.getContent().get(0).getDisplayName());
        assertEquals(List.of(option.getId()), first.getContent().get(0).getServingOptions().stream().map(o -> o.getId()).toList());
        var localized = foodItemLocalizationRepository.findByFoodItemIdAndLanguage(item.getId(), PreferredLanguage.TR).orElseThrow();
        localized.setDisplayName("Yeni Denetlenen Ad"); localized.setShortDisplayName("Yeni Denetlenen Ad");
        foodItemLocalizationRepository.saveAndFlush(localized);
        assertEquals("Yeni Denetlenen Ad", foodItemService.searchFoodItems(criteria, 0, 25).getContent().get(0).getDisplayName());
        assertEquals("Kontrollü Görünen Ad", first.getContent().get(0).getDisplayName());
        localized.setActive(false); foodItemLocalizationRepository.saveAndFlush(localized);
        assertEquals(item.getName(), foodItemService.searchFoodItems(criteria, 0, 25).getContent().get(0).getDisplayName());
        localized.setActive(true); foodItemLocalizationRepository.saveAndFlush(localized);
        alias.setActive(false); foodItemSearchAliasRepository.saveAndFlush(alias);
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        alias.setActive(true); foodItemSearchAliasRepository.saveAndFlush(alias);
        option.setUserVisible(false); foodItemServingOptionRepository.saveAndFlush(option);
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().get(0).getServingOptions().isEmpty());
        foodItemCategoryRepository.delete(assignment); foodItemCategoryRepository.flush();
        assertTrue(foodItemService.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        assertEquals(1, foodItemRepository.count());
        assertEquals(100.0, foodItemRepository.findById(item.getId()).orElseThrow().getCalories());
        assertEquals(30.0, foodItemServingOptionRepository.findById(option.getId()).orElseThrow().getGramWeight());
        Mockito.verifyNoInteractions(fallbackProvider, fallbackPublisher);
    }

    @Test
    void preparedEdamameKeepsFrozenQualifierAndDoesNotMatchBoiledOrRawFilter() {
        var edamame = product("Prepared Edamame (From Frozen)", null, VerificationStatus.VERIFIED);
        edamame.setDisplayName(edamame.getName());
        edamame.setShortDisplayName(edamame.getName());
        edamame.setSourceKey("USDA_FOODDATA:fdc:168411");
        edamame.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        edamame.setMarketRegion(MarketRegion.GLOBAL);
        edamame.setPreparationState(FoodPreparationState.PREPARED);
        edamame.setCanonicalFoodKey("GLOBAL:GENERIC_INGREDIENT:PREPARED:edamame_from_frozen");
        foodItemRepository.saveAndFlush(edamame);
        saveLocalization(edamame, PreferredLanguage.EN, "Prepared Edamame (From Frozen)");
        saveLocalization(edamame, PreferredLanguage.TR, "Dondurulmuştan Hazırlanmış Edamame");
        for (var language : List.of(PreferredLanguage.EN, PreferredLanguage.TR)) {
            var criteria = new FoodSearchCriteriaDto();
            criteria.setQuery("edamame");
            criteria.setPreferredLanguage(language);
            criteria.setMarketRegion(language == PreferredLanguage.TR ? MarketRegion.TR : MarketRegion.UK_IE);
            criteria.setPreparationState(FoodPreparationState.PREPARED);
            var results = foodItemService.searchFoodItems(criteria, 0, 10).getContent();
            assertEquals(1, results.size());
            assertEquals(edamame.getId(), results.get(0).getId());
            assertEquals(language == PreferredLanguage.TR ? "Dondurulmuştan Hazırlanmış Edamame"
                    : "Prepared Edamame (From Frozen)", results.get(0).getDisplayName());
            for (var wrong : List.of(FoodPreparationState.RAW, FoodPreparationState.BOILED, FoodPreparationState.STEAMED)) {
                criteria.setPreparationState(wrong);
                assertTrue(foodItemService.searchFoodItems(criteria, 0, 10).getContent().isEmpty());
            }
        }
    }
}

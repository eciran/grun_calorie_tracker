package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodCanonicalResolutionEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
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

    @BeforeEach
    void setUp() {
        OpenFoodFactsService openFoodFactsService = Mockito.mock(OpenFoodFactsService.class);
        FoodProductQualityIssueTracker foodProductQualityIssueTracker = Mockito.mock(FoodProductQualityIssueTracker.class);
        foodItemService = new FoodItemServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                openFoodFactsService,
                foodProductQualityIssueTracker,
                Mockito.mock(com.grun.calorietracker.service.FoodProductEvidenceService.class)
        );
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

        List<SearchReadinessExpectation> expectations = List.of(
                new SearchReadinessExpectation("banana", PreferredLanguage.EN, MarketRegion.UK_IE, "Raw Banana", 1),
                new SearchReadinessExpectation("broccoli", PreferredLanguage.EN, MarketRegion.UK_IE, "Raw Broccoli", 1),
                new SearchReadinessExpectation("milk", PreferredLanguage.EN, MarketRegion.UK_IE, "Whole Milk", 1),
                new SearchReadinessExpectation("chicken breast", PreferredLanguage.EN, MarketRegion.UK_IE, "Raw Chicken Breast", 2),
                new SearchReadinessExpectation("rice", PreferredLanguage.EN, MarketRegion.UK_IE, "Raw White Rice", 2),
                new SearchReadinessExpectation("muz", PreferredLanguage.TR, MarketRegion.TR, "Muz", 1),
                new SearchReadinessExpectation("brokoli", PreferredLanguage.TR, MarketRegion.TR, "Brokoli", 1),
                new SearchReadinessExpectation("süt", PreferredLanguage.TR, MarketRegion.TR, "Tam Yağlı Süt", 1),
                new SearchReadinessExpectation("tavuk göğsü", PreferredLanguage.TR, MarketRegion.TR, "Çiğ Tavuk Göğsü", 2),
                new SearchReadinessExpectation("pirinç", PreferredLanguage.TR, MarketRegion.TR, "Çiğ Beyaz Pirinç", 2)
        );

        for (SearchReadinessExpectation expectation : expectations) {
            FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
            criteria.setQuery(expectation.query());
            criteria.setPreferredLanguage(expectation.language());
            criteria.setMarketRegion(expectation.marketRegion());

            FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

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
        assertTrue(milkResults.size() >= 2);
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, milkResults.get(0).getCatalogType());
        assertTrue(milkResults.stream().anyMatch(product -> "0000493141402".equals(product.getBarcode())));
    }

    private record BrandedSearchExpectation(String query, String expectedBarcode) {
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
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setCalories(100.0);
        product.setProtein(0.0);
        product.setQualityScore(50);
        product.setUsageCount(0L);
        product.setIsCustom(false);
        return product;
    }
}

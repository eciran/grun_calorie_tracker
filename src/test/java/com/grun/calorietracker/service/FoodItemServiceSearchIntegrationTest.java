package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class FoodItemServiceSearchIntegrationTest {

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private FoodItemLocalizationRepository foodItemLocalizationRepository;

    @Autowired
    private FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Autowired
    private FoodItemSearchAliasRepository foodItemSearchAliasRepository;

    @Autowired
    private FoodProductQualityIssueRepository foodProductQualityIssueRepository;

    private FoodItemServiceImpl foodItemService;

    @BeforeEach
    void setUp() {
        OpenFoodFactsService openFoodFactsService = Mockito.mock(OpenFoodFactsService.class);
        FoodProductQualityIssueTracker foodProductQualityIssueTracker = Mockito.mock(FoodProductQualityIssueTracker.class);
        foodItemService = new FoodItemServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemServingOptionRepository,
                openFoodFactsService,
                foodProductQualityIssueTracker
        );
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
    private void saveLocalization(FoodItemEntity product, String displayName) {
        FoodItemLocalizationEntity localization = new FoodItemLocalizationEntity();
        localization.setFoodItem(product);
        localization.setLanguage(PreferredLanguage.TR);
        localization.setDisplayName(displayName);
        localization.setShortDisplayName(displayName);
        localization.setSource("test");
        localization.setActive(true);
        foodItemLocalizationRepository.save(localization);
    }
    private FoodItemEntity product(String name, String barcode, VerificationStatus verificationStatus) {
        FoodItemEntity product = new FoodItemEntity();
        product.setName(name);
        product.setBarcode(barcode);
        product.setNormalizedBarcode(barcode);
        product.setVerificationStatus(verificationStatus);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setCalories(100.0);
        product.setQualityScore(50);
        product.setUsageCount(0L);
        product.setIsCustom(false);
        return product;
    }
}

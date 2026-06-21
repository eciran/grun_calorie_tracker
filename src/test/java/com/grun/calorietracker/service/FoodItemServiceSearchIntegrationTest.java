package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
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
    private FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Autowired
    private FoodItemSearchAliasRepository foodItemSearchAliasRepository;

    private FoodItemServiceImpl foodItemService;

    @BeforeEach
    void setUp() {
        OpenFoodFactsService openFoodFactsService = Mockito.mock(OpenFoodFactsService.class);
        FoodProductQualityIssueTracker foodProductQualityIssueTracker = Mockito.mock(FoodProductQualityIssueTracker.class);
        foodItemService = new FoodItemServiceImpl(
                foodItemRepository,
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
    void searchFoodItems_matchesDbSearchAliasWithoutDuplicatingProductData() {
        FoodItemEntity semiSkimmedMilk = product("Semi Skimmed Milk", "222003", VerificationStatus.RAW_IMPORTED);
        semiSkimmedMilk.setBrand("Tesco");
        semiSkimmedMilk.setMarketRegion(MarketRegion.UK_IE);
        FoodItemEntity savedProduct = foodItemRepository.save(semiSkimmedMilk);

        FoodItemSearchAliasEntity alias = new FoodItemSearchAliasEntity();
        alias.setFoodItem(savedProduct);
        alias.setAlias("yarım yağlı süt");
        alias.setNormalizedAlias("yarim yagli sut");
        alias.setLanguage(PreferredLanguage.TR);
        alias.setAliasType(FoodSearchAliasType.TRANSLATION);
        alias.setSource("test");
        alias.setActive(true);
        foodItemSearchAliasRepository.save(alias);

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("yarım yağlı süt");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(savedProduct.getId(), result.getContent().get(0).getId());
        assertEquals("Semi Skimmed Milk", result.getContent().get(0).getProductName());
        assertEquals("Tesco", result.getContent().get(0).getBrand());
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

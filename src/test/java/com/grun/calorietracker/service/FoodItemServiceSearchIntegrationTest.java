package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
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

    private FoodItemServiceImpl foodItemService;

    @BeforeEach
    void setUp() {
        OpenFoodFactsService openFoodFactsService = Mockito.mock(OpenFoodFactsService.class);
        foodItemService = new FoodItemServiceImpl(foodItemRepository, openFoodFactsService);
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

    private FoodItemEntity product(String name, String barcode, VerificationStatus verificationStatus) {
        FoodItemEntity product = new FoodItemEntity();
        product.setName(name);
        product.setBarcode(barcode);
        product.setNormalizedBarcode(barcode);
        product.setVerificationStatus(verificationStatus);
        product.setCalories(100.0);
        product.setQualityScore(50);
        product.setUsageCount(0L);
        product.setIsCustom(false);
        return product;
    }
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodItemServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private OpenFoodFactsService openFoodFactsService;

    @InjectMocks
    private FoodItemServiceImpl foodItemService;

    @Test
    void getOrSaveFoodItemByBarcode_whenLocalProductExists_returnsProduct() {
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setBarcode("123456");
        foodItem.setNormalizedBarcode("123456");
        foodItem.setName("Greek Yogurt");

        when(foodItemRepository.findByNormalizedBarcode("123456")).thenReturn(Optional.of(foodItem));

        FoodItemEntity result = foodItemService.getOrSaveFoodItemByBarcode(" 123456 ");

        assertEquals("Greek Yogurt", result.getName());
        verify(foodItemRepository).findByNormalizedBarcode("123456");
    }

    @Test
    void getOrSaveFoodItemByBarcode_whenLocalProductDoesNotExist_throwsProductNotFound() {
        when(foodItemRepository.findByNormalizedBarcode("999999")).thenReturn(Optional.empty());
        when(foodItemRepository.findByBarcode("999999")).thenReturn(Optional.empty());
        when(openFoodFactsService.getProductByBarcode("999999")).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> foodItemService.getOrSaveFoodItemByBarcode("999999"));
    }

    @Test
    void getOrSaveFoodItemByBarcode_whenExternalProductExists_cachesWithQualityStatus() {
        FoodProductDto externalProduct = new FoodProductDto();
        externalProduct.setBarcode("3017620422003");
        externalProduct.setProductName("Nutella");
        externalProduct.setImageUrl("https://images.openfoodfacts.org/nutella.jpg");
        externalProduct.setCalories(539.0);

        when(foodItemRepository.findByNormalizedBarcode("3017620422003")).thenReturn(Optional.empty());
        when(foodItemRepository.findByBarcode("3017620422003")).thenReturn(Optional.empty());
        when(openFoodFactsService.getProductByBarcode("3017620422003")).thenReturn(Optional.of(externalProduct));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodItemEntity result = foodItemService.getOrSaveFoodItemByBarcode("3017620422003");

        assertEquals("Nutella", result.getName());
        assertEquals("3017620422003", result.getBarcode());
        assertEquals("3017620422003", result.getNormalizedBarcode());
        assertEquals(FoodDataSource.OPEN_FOOD_FACTS, result.getDataSource());
        assertEquals(VerificationStatus.RAW_IMPORTED, result.getVerificationStatus());
        assertEquals("https://images.openfoodfacts.org/nutella.jpg", result.getExternalImageUrl());
        assertEquals(ImageSource.OPEN_FOOD_FACTS, result.getImageSource());
        assertEquals(ImageStatus.NEEDS_REVIEW, result.getImageStatus());
        verify(foodItemRepository).save(any(FoodItemEntity.class));
    }

    @Test
    void getOrSaveFoodItemByBarcode_whenBarcodeHasSeparators_normalizesBeforeLookup() {
        FoodProductDto externalProduct = new FoodProductDto();
        externalProduct.setBarcode("301-762 0422003");
        externalProduct.setProductName("Nutella");

        when(foodItemRepository.findByNormalizedBarcode("3017620422003")).thenReturn(Optional.empty());
        when(foodItemRepository.findByBarcode("3017620422003")).thenReturn(Optional.empty());
        when(openFoodFactsService.getProductByBarcode("3017620422003")).thenReturn(Optional.of(externalProduct));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodItemEntity result = foodItemService.getOrSaveFoodItemByBarcode(" 301-762 0422003 ");

        assertEquals("3017620422003", result.getBarcode());
        assertEquals("3017620422003", result.getNormalizedBarcode());
        verify(openFoodFactsService).getProductByBarcode("3017620422003");
    }

    @Test
    void getOrSaveFoodItemByBarcode_whenBarcodeIsBlank_throwsProductNotFound() {
        assertThrows(ProductNotFoundException.class,
                () -> foodItemService.getOrSaveFoodItemByBarcode(" "));
    }

    @Test
    void searchFoodItems_whenCriteriaHasQueryAndCalories_returnsMappedProducts() {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("yogurt");
        criteria.setMinCalories(50.0);
        criteria.setMaxCalories(150.0);
        criteria.setNutriScore("A");
        criteria.setSortBy("calories");
        criteria.setSortOrder("desc");

        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setBarcode("123456");
        foodItem.setName("Greek Yogurt");
        foodItem.setCalories(90.0);
        foodItem.setProtein(10.0);
        foodItem.setNutriScore("a");

        when(foodItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(foodItem)));

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals("123456", result.getContent().get(0).getBarcode());
        assertEquals("Greek Yogurt", result.getContent().get(0).getProductName());
        assertEquals(90.0, result.getContent().get(0).getCalories());
        assertEquals("a", result.getContent().get(0).getNutriScore());
    }

    @Test
    void searchFoodItems_whenCriteriaIsNull_usesDefaultSortAndDoesNotFail() {
        when(foodItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<FoodProductDto> result = foodItemService.searchFoodItems(null);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void searchFoodItems_whenSortFieldIsNotAllowed_fallsBackToNameSort() {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setSortBy("id");

        when(foodItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        foodItemService.searchFoodItems(criteria);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(foodItemRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("name");
        assertNotNull(order);
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void searchFoodItems_whenLocalSearchIsEmpty_cachesAndReturnsExternalResults() {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("nutella");

        FoodProductDto externalProduct = new FoodProductDto();
        externalProduct.setBarcode("3017620422003");
        externalProduct.setProductName("Nutella");
        externalProduct.setImageUrl("https://images.openfoodfacts.org/nutella.jpg");
        externalProduct.setCalories(539.0);

        when(foodItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(openFoodFactsService.searchProductsByCriteria(criteria)).thenReturn(List.of(externalProduct));
        when(foodItemRepository.findByNormalizedBarcode("3017620422003")).thenReturn(Optional.empty());
        when(foodItemRepository.findByBarcode("3017620422003")).thenReturn(Optional.empty());
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> {
            FoodItemEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        FoodProductSearchPageDto result = foodItemService.searchFoodItems(criteria, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals("3017620422003", result.getContent().get(0).getBarcode());
        assertEquals("Nutella", result.getContent().get(0).getProductName());
        assertEquals(FoodDataSource.OPEN_FOOD_FACTS, result.getContent().get(0).getDataSource());
        assertEquals(VerificationStatus.RAW_IMPORTED, result.getContent().get(0).getVerificationStatus());
        verify(openFoodFactsService).searchProductsByCriteria(criteria);
        verify(foodItemRepository).save(any(FoodItemEntity.class));
    }
}

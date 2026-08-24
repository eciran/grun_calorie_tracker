package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodItemPublicationVisibilityTest {

    @Mock
    private FoodItemRepository foodItemRepository;
    @Mock
    private FoodItemLocalizationRepository foodItemLocalizationRepository;
    @Mock
    private FoodItemServingOptionRepository foodItemServingOptionRepository;
    @Mock
    private FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;
    @Mock
    private OpenFoodFactsService openFoodFactsService;
    @Mock
    private FoodProductQualityIssueTracker foodProductQualityIssueTracker;
    @Mock
    private FoodProductEvidenceService foodProductEvidenceService;
    @Mock
    private CatalogPublicationService catalogPublicationService;
    @InjectMocks
    private FoodItemServiceImpl service;

    @Test
    void internalReviewProductCannotBeReadByDirectId() {
        FoodItemEntity product = internalProduct();
        product.setId(42L);
        when(foodItemRepository.findById(42L)).thenReturn(Optional.of(product));

        assertThrows(
                ProductNotFoundException.class,
                () -> service.getFoodItemById(42L, "user@example.com")
        );
    }

    @Test
    void internalReviewProductCannotBeReadByBarcodeOrTriggerExternalFallback() {
        FoodItemEntity product = internalProduct();
        product.setBarcode("123456789");
        product.setNormalizedBarcode("123456789");
        when(foodItemRepository.findByNormalizedBarcode("123456789")).thenReturn(Optional.of(product));

        assertThrows(
                ProductNotFoundException.class,
                () -> service.getOrSaveFoodItemByBarcode("123456789")
        );

        verify(openFoodFactsService, never()).getProductByBarcode("123456789");
    }

    private FoodItemEntity internalProduct() {
        FoodItemEntity product = new FoodItemEntity();
        product.setName("Pending product");
        product.setIsCustom(false);
        product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        return product;
    }
}

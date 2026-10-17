package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodServingOptionDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.service.impl.FoodServingOptionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodServingOptionServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Mock
    private FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;

    @InjectMocks
    private FoodServingOptionServiceImpl service;

    @Test
    void retiredMeasureIsHiddenWhileSourceReplacementAndHistoryWeightRemainIntact() {
        var food = new FoodItemEntity();
        food.setId(180987L);
        completeNutrition(food);
        food.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        var old = new FoodItemServingOptionEntity();
        old.setId(2301L);
        old.setFoodItem(food);
        old.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.PIECE);
        old.setQuantity(1.0);
        old.setGramWeight(50.0);
        old.setLabel("Old large egg");
        old.setUserVisible(false);
        old.setIsDefault(false);
        var replacement = new FoodItemServingOptionEntity();
        replacement.setId(9001L);
        replacement.setFoodItem(food);
        replacement.setQuantity(1.0);
        replacement.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.PIECE);
        replacement.setGramWeight(46.0);
        replacement.setUserVisible(true);
        replacement.setIsDefault(true);
        replacement.setLabel("1 large egg (46g)");
        var localized = new FoodItemServingOptionLocalizationEntity();
        localized.setServingOption(replacement);
        localized.setLanguage(PreferredLanguage.TR);
        localized.setLabel("1 büyük yumurta (46g)");
        localized.setActive(true);
        when(foodItemRepository.findById(food.getId())).thenReturn(Optional.of(food));
        when(foodItemServingOptionRepository.findByFoodItemAndQualityStatusOrderByIsDefaultDescLabelAsc(
                food, FoodServingOptionQualityStatus.VERIFIED)).thenReturn(List.of(old, replacement));
        when(foodItemServingOptionLocalizationRepository.findByServingOptionIdInAndLanguageInAndActiveTrue(
                anyCollection(), anyCollection())).thenReturn(List.of(localized));
        var result = service.getServingOptions(food.getId(), "user@example.com", PreferredLanguage.TR);
        assertEquals(List.of(9001L), result.stream().map(FoodServingOptionDto::getId).toList());
        assertEquals("1 büyük yumurta (46g)", result.get(0).getLabel());
        assertEquals(46.0, result.get(0).getGramWeight());
        assertEquals(50.0, old.getGramWeight());
        assertEquals("Old large egg", old.getLabel());
    }

    @Test
    void getServingOptions_hidesInternalReviewProducts() {
        FoodItemEntity product = new FoodItemEntity();
        product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        when(foodItemRepository.findById(12L)).thenReturn(Optional.of(product));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.grun.calorietracker.exception.ProductNotFoundException.class,
                () -> service.getServingOptions(12L, "user@example.com", PreferredLanguage.EN));
        org.mockito.Mockito.verifyNoInteractions(foodItemServingOptionRepository);
    }

    @Test
    void getServingOptions_whenTurkishRequested_returnsLocalizedLabel() {
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setId(12L);
        completeNutrition(foodItem);
        foodItem.setIsCustom(false);
        foodItem.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        FoodItemServingOptionEntity option = new FoodItemServingOptionEntity();
        option.setId(5L);
        option.setQuantity(1.0);
        option.setFoodItem(foodItem);
        option.setLabel("1 portion Chicken Breast");
        option.setGramWeight(120.0);
        option.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.SERVING);
        FoodItemServingOptionLocalizationEntity localization = new FoodItemServingOptionLocalizationEntity();
        localization.setServingOption(option);
        localization.setLanguage(PreferredLanguage.TR);
        localization.setLabel("1 porsiyon Tavuk Göğsü");
        localization.setActive(true);
        when(foodItemRepository.findById(12L)).thenReturn(Optional.of(foodItem));
        when(foodItemServingOptionRepository.findByFoodItemAndQualityStatusOrderByIsDefaultDescLabelAsc(
                foodItem,
                FoodServingOptionQualityStatus.VERIFIED
        ))
                .thenReturn(List.of(option));
        when(foodItemServingOptionLocalizationRepository
                .findByServingOptionIdInAndLanguageInAndActiveTrue(anyCollection(), anyCollection()))
                .thenReturn(List.of(localization));

        List<FoodServingOptionDto> result = service.getServingOptions(12L, "user@example.com", PreferredLanguage.TR);

        assertEquals(1, result.size());
        assertEquals("1 porsiyon Tavuk Göğsü", result.get(0).getLabel());
        assertEquals(120.0, result.get(0).getGramWeight());
    }

    @Test
    void getServingOptions_exposesOnlyVerifiedOptions() {
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setId(13L);
        completeNutrition(foodItem);
        foodItem.setIsCustom(false);
        FoodItemServingOptionEntity verified = new FoodItemServingOptionEntity();
        verified.setId(7L);
        verified.setQuantity(1.0);
        verified.setFoodItem(foodItem);
        verified.setLabel("1 slice");
        verified.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.SLICE);
        verified.setGramWeight(30.0);
        verified.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);

        when(foodItemRepository.findById(13L)).thenReturn(Optional.of(foodItem));
        when(foodItemServingOptionRepository.findByFoodItemAndQualityStatusOrderByIsDefaultDescLabelAsc(
                foodItem,
                FoodServingOptionQualityStatus.VERIFIED
        )).thenReturn(List.of(verified));
        when(foodItemServingOptionLocalizationRepository
                .findByServingOptionIdInAndLanguageInAndActiveTrue(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        List<FoodServingOptionDto> result = service.getServingOptions(13L, "user@example.com", PreferredLanguage.EN);

        assertEquals(List.of(7L), result.stream().map(FoodServingOptionDto::getId).toList());
    }

    @Test
    void getServingOptions_hidesSourceEvidenceThatIsNotSuitableForMobile() {
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setId(14L);
        completeNutrition(foodItem);
        foodItem.setIsCustom(false);
        FoodItemServingOptionEntity piece = new FoodItemServingOptionEntity();
        piece.setId(8L);
        piece.setQuantity(1.0);
        piece.setFoodItem(foodItem);
        piece.setLabel("1 medium peach");
        piece.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.PIECE);
        piece.setGramWeight(161.0);
        piece.setUserVisible(true);
        FoodItemServingOptionEntity cupSlices = new FoodItemServingOptionEntity();
        cupSlices.setId(9L);
        cupSlices.setQuantity(1.0);
        cupSlices.setFoodItem(foodItem);
        cupSlices.setLabel("1 cup peach slices");
        cupSlices.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.CUP);
        cupSlices.setGramWeight(154.0);
        cupSlices.setUserVisible(false);

        when(foodItemRepository.findById(14L)).thenReturn(Optional.of(foodItem));
        when(foodItemServingOptionRepository.findByFoodItemAndQualityStatusOrderByIsDefaultDescLabelAsc(
                foodItem,
                FoodServingOptionQualityStatus.VERIFIED
        )).thenReturn(List.of(piece, cupSlices));
        when(foodItemServingOptionLocalizationRepository
                .findByServingOptionIdInAndLanguageInAndActiveTrue(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        List<FoodServingOptionDto> result = service.getServingOptions(14L, "user@example.com", PreferredLanguage.EN);

        assertEquals(List.of(8L), result.stream().map(FoodServingOptionDto::getId).toList());
    }
    private void completeNutrition(FoodItemEntity product) {
        product.setCalories(120.0);
        product.setProtein(23.0);
        product.setFat(3.0);
        product.setCarbs(0.0);
    }
}

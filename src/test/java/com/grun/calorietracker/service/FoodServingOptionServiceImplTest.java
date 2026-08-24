package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodServingOptionDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
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
    void getServingOptions_whenTurkishRequested_returnsLocalizedLabel() {
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setId(12L);
        foodItem.setIsCustom(false);
        FoodItemServingOptionEntity option = new FoodItemServingOptionEntity();
        option.setId(5L);
        option.setFoodItem(foodItem);
        option.setLabel("1 portion Chicken Breast");
        option.setGramWeight(120.0);
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
        foodItem.setIsCustom(false);
        FoodItemServingOptionEntity verified = new FoodItemServingOptionEntity();
        verified.setId(7L);
        verified.setFoodItem(foodItem);
        verified.setLabel("1 slice");
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
}

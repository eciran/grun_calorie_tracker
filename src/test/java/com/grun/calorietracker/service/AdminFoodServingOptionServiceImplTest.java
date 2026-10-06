package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminFoodServingOptionUpsertRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.service.impl.AdminFoodServingOptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFoodServingOptionServiceImplTest {

    @Mock private FoodItemRepository foodItemRepository;
    @Mock private FoodItemServingOptionRepository servingOptionRepository;
    @Mock private FoodItemServingOptionLocalizationRepository localizationRepository;
    @Mock private FoodProductReviewAuditRepository auditRepository;

    private AdminFoodServingOptionServiceImpl service;
    private FoodItemEntity product;

    @BeforeEach
    void setUp() {
        service = new AdminFoodServingOptionServiceImpl(
                foodItemRepository, servingOptionRepository, localizationRepository, auditRepository);
        product = new FoodItemEntity();
        product.setId(12L);
        when(foodItemRepository.findById(12L)).thenReturn(Optional.of(product));
    }

    @Test
    void createStoresAdminOptionAndEnglishTurkishLabels() {
        AdminFoodServingOptionUpsertRequestDto request = validRequest();
        when(servingOptionRepository.findByFoodItemOrderByIsDefaultDescLabelAsc(product)).thenReturn(List.of());
        when(servingOptionRepository.save(any())).thenAnswer(invocation -> {
            FoodItemServingOptionEntity value = invocation.getArgument(0);
            value.setId(44L);
            return value;
        });
        when(localizationRepository.findByServingOptionIdIn(List.of(44L))).thenReturn(List.of());
        when(localizationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(12L, request, "admin@grun.app");

        assertEquals(44L, result.id());
        assertEquals(true, result.userVisible());
        assertEquals(2, result.localizations().size());
        verify(auditRepository).save(any());
    }

    @Test
    void createRejectsEquivalentConversion() {
        AdminFoodServingOptionUpsertRequestDto request = validRequest();
        FoodItemServingOptionEntity existing = new FoodItemServingOptionEntity();
        existing.setId(9L);
        existing.setUnitType(FoodServingOptionUnit.PIECE);
        existing.setQuantity(1.0);
        existing.setGramWeight(118.0);
        when(servingOptionRepository.findByFoodItemOrderByIsDefaultDescLabelAsc(product))
                .thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> service.create(12L, request, "admin@grun.app"));
    }

    @Test
    void createRejectsHiddenDefaultOption() {
        AdminFoodServingOptionUpsertRequestDto request = validRequest();
        request.setUserVisible(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(12L, request, "admin@grun.app"));
    }

    @Test
    void createRequiresEnglishAndTurkishLabels() {
        AdminFoodServingOptionUpsertRequestDto request = validRequest();
        request.setLocalizations(List.of(
                new AdminFoodServingOptionUpsertRequestDto.Localization(PreferredLanguage.EN, "1 medium banana"),
                new AdminFoodServingOptionUpsertRequestDto.Localization(PreferredLanguage.EN, "Medium banana")
        ));

        assertThrows(IllegalArgumentException.class,
                () -> service.create(12L, request, "admin@grun.app"));
    }

    private AdminFoodServingOptionUpsertRequestDto validRequest() {
        AdminFoodServingOptionUpsertRequestDto request = new AdminFoodServingOptionUpsertRequestDto();
        request.setLabel("1 medium banana");
        request.setUnitType(FoodServingOptionUnit.PIECE);
        request.setQuantity(1.0);
        request.setGramWeight(118.0);
        request.setDefaultOption(true);
        request.setUserVisible(true);
        request.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);
        request.setLocalizations(List.of(
                new AdminFoodServingOptionUpsertRequestDto.Localization(PreferredLanguage.EN, "1 medium banana"),
                new AdminFoodServingOptionUpsertRequestDto.Localization(PreferredLanguage.TR, "1 orta boy muz")
        ));
        return request;
    }
}

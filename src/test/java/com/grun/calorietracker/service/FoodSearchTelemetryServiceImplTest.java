package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodSearchTelemetryEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodSearchTelemetryRepository;
import com.grun.calorietracker.service.impl.FoodSearchTelemetryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodSearchTelemetryServiceImplTest {
    @Mock FoodSearchTelemetryRepository telemetryRepository;
    @Mock FoodItemRepository foodItemRepository;
    @InjectMocks FoodSearchTelemetryServiceImpl service;

    @Test
    void recordSearch_redactsPiiAndStoresNoUserIdentity() {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("milk user@example.com +353 87 123 4567");
        criteria.setMarketRegion(MarketRegion.UK_IE);
        criteria.setPreferredLanguage(PreferredLanguage.EN);
        FoodProductSearchPageDto page = new FoodProductSearchPageDto();
        page.setTotalElements(12L);
        when(telemetryRepository.save(any())).thenAnswer(invocation -> {
            FoodSearchTelemetryEntity event = invocation.getArgument(0);
            event.setId("search-1");
            return event;
        });

        assertEquals("search-1", service.recordSearch(criteria, page));

        ArgumentCaptor<FoodSearchTelemetryEntity> captor = ArgumentCaptor.forClass(FoodSearchTelemetryEntity.class);
        verify(telemetryRepository).save(captor.capture());
        assertEquals("milk [email] [phone]", captor.getValue().getSafeQuery());
        assertEquals(64, captor.getValue().getQueryFingerprint().length());
        assertEquals(12, captor.getValue().getResultCount());
    }

    @Test
    void recordSelection_acceptsOnceAndIncrementsBoundedPopularity() {
        FoodSearchTelemetryEntity event = new FoodSearchTelemetryEntity();
        event.setId("search-1");
        event.setSearchedAt(Instant.now());
        event.setResultCount(3);
        event.setResultFoodItemIds("41,42,43");
        FoodItemEntity product = new FoodItemEntity();
        product.setId(42L);
        when(telemetryRepository.findById("search-1")).thenReturn(Optional.of(event));
        when(foodItemRepository.findById(42L)).thenReturn(Optional.of(product));

        service.recordSelection("search-1", 42L, 2);

        assertEquals(product, event.getSelectedFoodItem());
        assertEquals(2, event.getSelectedRank());
        assertNotNull(event.getSelectedAt());
        verify(foodItemRepository).incrementSearchSelectionCount(42L);
    }

    @Test
    void recordSelection_rejectsRankOutsideReturnedResults() {
        FoodSearchTelemetryEntity event = new FoodSearchTelemetryEntity();
        event.setSearchedAt(Instant.now());
        event.setResultCount(1);
        event.setResultFoodItemIds("42");
        when(telemetryRepository.findById("search-1")).thenReturn(Optional.of(event));

        assertThrows(IllegalArgumentException.class, () -> service.recordSelection("search-1", 42L, 2));
        verifyNoInteractions(foodItemRepository);
    }
}
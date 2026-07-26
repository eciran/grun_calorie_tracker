package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.impl.FoodCatalogMaintenanceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodCatalogMaintenanceServiceImplTest {
    @Mock FoodItemRepository foodItemRepository;
    @Mock FoodProductQualityIssueRepository issueRepository;
    @Mock ProductQualitySuggestionService qualitySuggestionService;
    @InjectMocks FoodCatalogMaintenanceServiceImpl service;

    @Test
    void refreshStaleSourceQueue_createsReviewIssueWithoutChangingProductVisibility() {
        ReflectionTestUtils.setField(service, "staleDays", 180);
        ReflectionTestUtils.setField(service, "batchSize", 500);
        FoodItemEntity product = new FoodItemEntity();
        product.setId(7L);
        product.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        product.setSourceKey("OPEN_FOOD_FACTS:123");
        when(foodItemRepository.findStaleExternalProducts(anyCollection(), any(), any())).thenReturn(List.of(product));
        when(issueRepository.findByFoodItemIdAndIssueTypeAndResolvedFalse(7L, FoodProductQualityIssue.STALE_SOURCE))
                .thenReturn(Optional.empty());

        assertEquals(1, service.refreshStaleSourceQueue());

        verify(issueRepository).save(argThat(issue ->
                issue.getFoodItem() == product
                        && issue.getIssueType() == FoodProductQualityIssue.STALE_SOURCE
                        && Boolean.FALSE.equals(issue.getResolved())));
        verify(foodItemRepository, never()).save(any());
    }

    @Test
    void refreshStaleSourceQueue_resolvesIssueAfterExternalProductIsRefreshed() {
        ReflectionTestUtils.setField(service, "staleDays", 180);
        ReflectionTestUtils.setField(service, "batchSize", 500);
        FoodItemEntity product = new FoodItemEntity();
        product.setId(7L);
        product.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        product.setLastExternalSyncAt(LocalDateTime.now());
        FoodProductQualityIssueEntity issue = new FoodProductQualityIssueEntity();
        issue.setFoodItem(product);
        issue.setIssueType(FoodProductQualityIssue.STALE_SOURCE);
        issue.setResolved(false);
        when(issueRepository.findByIssueTypeAndResolvedFalse(
                eq(FoodProductQualityIssue.STALE_SOURCE), any())).thenReturn(List.of(issue));
        when(foodItemRepository.findStaleExternalProducts(anyCollection(), any(), any())).thenReturn(List.of());

        assertEquals(0, service.refreshStaleSourceQueue());

        assertTrue(issue.getResolved());
        assertNotNull(issue.getResolvedAt());
        assertEquals("system:product-maintenance", issue.getResolvedBy());
        verify(issueRepository).save(issue);
    }
}

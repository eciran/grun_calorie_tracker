package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.support.FoodCategoryImportResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodCategoryBackfillServiceTest {
    @Mock private FoodItemRepository foodItemRepository;
    @Mock private FoodCategoryImportResolutionService resolutionService;
    @Mock private FoodProductQualityIssueRepository qualityIssueRepository;

    private FoodCategoryBackfillService service;

    @BeforeEach
    void setUp() {
        service = new FoodCategoryBackfillService(
                foodItemRepository, qualityIssueRepository, resolutionService);
    }

    @Test
    void backfillUsesStableCursorAndAggregatesBoundedBatches() {
        FoodItemEntity first = product(11L);
        FoodItemEntity second = product(12L);
        FoodItemEntity third = product(25L);
        when(foodItemRepository.findCategoryResolutionBackfillIds(eq(10L), any(Pageable.class)))
                .thenReturn(List.of(11L, 12L));
        when(foodItemRepository.findByIdIn(eq(List.of(11L, 12L)), any(Sort.class)))
                .thenReturn(List.of(first, second));
        when(resolutionService.resolveAfterImport(List.of(first, second), "admin@test.com"))
                .thenReturn(new FoodCategoryImportResolutionService.ResolutionSummary(1, 0, 1));
        when(foodItemRepository.findCategoryResolutionBackfillIds(eq(12L), any(Pageable.class)))
                .thenReturn(List.of(25L));
        when(foodItemRepository.findByIdIn(eq(List.of(25L)), any(Sort.class)))
                .thenReturn(List.of(third));
        when(resolutionService.resolveAfterImport(List.of(third), "admin@test.com"))
                .thenReturn(new FoodCategoryImportResolutionService.ResolutionSummary(1, 0, 0));
        when(qualityIssueRepository.resolveOutOfScopeCanonicalCategoryIssues("admin@test.com"))
                .thenReturn(7);

        var result = service.backfill(2, 10, 10L, "admin@test.com");

        assertThat(result.scannedProducts()).isEqualTo(3);
        assertThat(result.assignedProducts()).isEqualTo(2);
        assertThat(result.reviewProducts()).isEqualTo(1);
        assertThat(result.reconciledIssues()).isEqualTo(7);
        assertThat(result.processedBatches()).isEqualTo(2);
        assertThat(result.lastProcessedId()).isEqualTo(25L);
        verify(foodItemRepository, never())
                .findCategoryResolutionBackfillIds(eq(25L), any(Pageable.class));
        verify(qualityIssueRepository).resolveOutOfScopeCanonicalCategoryIssues("admin@test.com");
    }

    @Test
    void backfillStopsAtMaximumBatchCountAndReturnsResumeCursor() {
        FoodItemEntity first = product(101L);
        when(foodItemRepository.findCategoryResolutionBackfillIds(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(101L));
        when(foodItemRepository.findByIdIn(eq(List.of(101L)), any(Sort.class)))
                .thenReturn(List.of(first));
        when(resolutionService.resolveAfterImport(List.of(first), null))
                .thenReturn(new FoodCategoryImportResolutionService.ResolutionSummary(0, 0, 1));

        var result = service.backfill(1, 1, 0L, null);

        assertThat(result.processedBatches()).isEqualTo(1);
        assertThat(result.lastProcessedId()).isEqualTo(101L);
        verify(foodItemRepository, never())
                .findCategoryResolutionBackfillIds(eq(101L), any(Pageable.class));
    }

    private FoodItemEntity product(long id) {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(id);
        return product;
    }
}

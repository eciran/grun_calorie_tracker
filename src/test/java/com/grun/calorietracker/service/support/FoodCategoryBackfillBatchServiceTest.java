package com.grun.calorietracker.service.support;

import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.FoodCategoryBackfillService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FoodCategoryBackfillBatchServiceTest {
    @Test void fenceDenialPrecedesIdInventoryAndResolver() {
        var foods = mock(FoodItemRepository.class); var resolver = mock(FoodCategoryImportResolutionService.class);
        var fence = mock(ExternalFoodImportFence.class);
        var failure = new IllegalStateException("No writable fence"); doThrow(failure).when(fence).acquire();
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> new FoodCategoryBackfillBatchService(foods, resolver, fence).process(0, 100, "fixture")));
        verifyNoInteractions(foods, resolver);
    }

    @Test void invalidCursorOrUnboundedLimitDoesNotTouchDatabase() {
        var foods = mock(FoodItemRepository.class); var resolver = mock(FoodCategoryImportResolutionService.class);
        var fence = mock(ExternalFoodImportFence.class); var batch = new FoodCategoryBackfillBatchService(foods, resolver, fence);
        for (int limit : List.of(0, 1001, Integer.MAX_VALUE))
            assertThrows(IllegalArgumentException.class, () -> batch.process(0, limit, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> batch.process(-1, 100, "fixture"));
        verifyNoInteractions(foods, resolver, fence);
    }

    @Test void orchestratorRejectsOuterTransactionsBeforeAnyReads() {
        var foods = mock(FoodItemRepository.class); var issues = mock(FoodProductQualityIssueRepository.class);
        var batch = mock(FoodCategoryBackfillBatchService.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class,
                    () -> new FoodCategoryBackfillService(foods, issues, batch).backfill(10, 2, 0, "fixture"));
            verifyNoInteractions(foods, issues, batch);
        } finally { TransactionSynchronizationManager.clear(); }
    }

    @Test void emptyBatchDoesNotInvokeResolverOrAdvanceCursor() {
        var foods = mock(FoodItemRepository.class); var resolver = mock(FoodCategoryImportResolutionService.class);
        var fence = mock(ExternalFoodImportFence.class);
        var result = new FoodCategoryBackfillBatchService(foods, resolver, fence).process(37, 100, "fixture");
        assertEquals(0, result.selected()); assertEquals(37, result.nextId()); verifyNoInteractions(resolver);
        var order = inOrder(fence, foods); order.verify(fence).acquire();
        order.verify(foods).findCategoryResolutionBackfillIds(eq(37L), any());
    }
}

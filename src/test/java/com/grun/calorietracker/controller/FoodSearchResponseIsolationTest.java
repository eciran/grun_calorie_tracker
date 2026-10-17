package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.service.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoodSearchResponseIsolationTest {
    @Test
    void providerFailureIsNotRecordedAsAnOrdinaryZeroResultSearch() {
        FoodItemService items = mock(FoodItemService.class);
        FoodSearchTelemetryService telemetry = mock(FoodSearchTelemetryService.class);
        FoodItemController controller = new FoodItemController(items,
                mock(CatalogClassificationService.class), mock(FoodServingOptionService.class),
                mock(FailedBarcodeScanService.class), mock(ProductCorrectionSuggestionService.class),
                mock(UserProductLibraryService.class), mock(UserService.class), telemetry);
        var failed = new FoodProductSearchPageDto(); failed.setContent(List.of()); failed.setTotalElements(0L);
        failed.setCacheable(false);
        when(items.searchFoodItems(any(), eq(0), eq(25))).thenReturn(failed);
        var response = search(controller);
        assertTrue(response.getContent().isEmpty()); assertNull(response.getSearchRequestId());
        assertFalse(response.isCacheable()); verifyNoInteractions(telemetry);
    }
    @Test
    void requestIdentifiersMustNotMutateCachedPageOrEarlierResponses() {
        FoodItemService items = mock(FoodItemService.class);
        FoodSearchTelemetryService telemetry = mock(FoodSearchTelemetryService.class);
        FoodItemController controller = new FoodItemController(items,
                mock(CatalogClassificationService.class), mock(FoodServingOptionService.class),
                mock(FailedBarcodeScanService.class), mock(ProductCorrectionSuggestionService.class),
                mock(UserProductLibraryService.class), mock(UserService.class), telemetry);
        FoodProductDto product = new FoodProductDto();
        product.setId(42L);
        FoodProductSearchPageDto cached = new FoodProductSearchPageDto();
        cached.setContent(List.of(product));
        cached.setPage(0);
        cached.setSize(25);
        cached.setTotalElements(1L);
        cached.setTotalPages(1);
        cached.setFirst(true);
        cached.setLast(true);
        when(items.searchFoodItems(any(), eq(0), eq(25))).thenReturn(cached);
        when(telemetry.recordSearch(any(), any())).thenReturn("request-one", "request-two");

        FoodProductSearchPageDto first = search(controller);
        FoodProductSearchPageDto second = search(controller);

        assertAll(
                () -> assertNull(cached.getSearchRequestId()),
                () -> assertNotSame(cached, first),
                () -> assertNotSame(first, second),
                () -> assertEquals("request-one", first.getSearchRequestId()),
                () -> assertEquals("request-two", second.getSearchRequestId()),
                () -> assertEquals(cached.getContent(), first.getContent()),
                () -> assertEquals(cached.getPage(), first.getPage()),
                () -> assertEquals(cached.getSize(), first.getSize()),
                () -> assertEquals(cached.getTotalElements(), first.getTotalElements()),
                () -> assertEquals(cached.getTotalPages(), first.getTotalPages()),
                () -> assertEquals(cached.getFirst(), first.getFirst()),
                () -> assertEquals(cached.getLast(), first.getLast()));
        verify(telemetry, times(2)).recordSearch(any(), any());
        first.getContent().clear();
        assertEquals(1, cached.getContent().size());
        assertEquals(1, second.getContent().size());
    }

    private FoodProductSearchPageDto search(FoodItemController controller) {
        return controller.searchProducts("milk", null, 0, 25, MarketRegion.TR,
                null, null, null, null, PreferredLanguage.TR, null, null).getBody();
    }
}

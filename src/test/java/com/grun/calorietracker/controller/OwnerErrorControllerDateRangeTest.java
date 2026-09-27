package com.grun.calorietracker.controller;

import com.grun.calorietracker.service.OwnerErrorGroupLifecycleService;
import com.grun.calorietracker.service.OwnerErrorRecorder;
import com.grun.calorietracker.service.support.OwnerErrorStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OwnerErrorControllerDateRangeTest {
    @Test
    void futureUpperBoundIsClampedToCurrentTime() {
        OwnerErrorStore store = mock(OwnerErrorStore.class);
        when(store.find(any())).thenReturn(new OwnerErrorStore.Page(List.of(), 0, 0, 0, 25));
        OwnerErrorController controller = new OwnerErrorController(store, mock(OwnerErrorRecorder.class),
                mock(OwnerErrorGroupLifecycleService.class));
        Instant before = Instant.now();

        controller.list(before.minus(7, ChronoUnit.DAYS), before.plus(30, ChronoUnit.DAYS),
                null, null, null, null, null, null, null, 0, 25);

        var query = org.mockito.ArgumentCaptor.forClass(OwnerErrorStore.Query.class);
        verify(store).find(query.capture());
        assertThat(query.getValue().to()).isBetween(before, Instant.now());
    }
}

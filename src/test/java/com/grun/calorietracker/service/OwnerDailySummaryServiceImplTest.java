package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.AdminApprovalStatus;
import com.grun.calorietracker.repository.AdminApprovalRequestRepository;
import com.grun.calorietracker.repository.OwnerOperationalAlertRepository;
import com.grun.calorietracker.service.impl.OwnerDailySummaryServiceImpl;
import com.grun.calorietracker.service.support.OwnerErrorStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OwnerDailySummaryServiceImplTest {
    private final OwnerOperationalAlertRepository alerts = mock(OwnerOperationalAlertRepository.class);
    private final AdminApprovalRequestRepository approvals = mock(AdminApprovalRequestRepository.class);
    private final OwnerOperationalAlertService outbox = mock(OwnerOperationalAlertService.class);
    private final OwnerErrorStore errorStore = mock(OwnerErrorStore.class);
    private final OwnerDailySummaryServiceImpl service = new OwnerDailySummaryServiceImpl(alerts, approvals, outbox, errorStore);

    @Test void buildsDublinDayBoundariesAndEnqueuesSameSnapshot() {
        ReflectionTestUtils.setField(service, "reportZone", "Europe/Dublin");
        when(alerts.sumOccurrencesByCategoryBetween(eq("BACKEND_ERROR"), any(), any())).thenReturn(7L);
        when(alerts.countByCategoryAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan(eq("BACKEND_ERROR"), any(), any())).thenReturn(2L);
        when(alerts.countByCategoryAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan(eq("FINANCIAL_APPROVAL"), any(), any())).thenReturn(3L);
        when(alerts.countByCategoryAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan(eq("OPERATIONAL_APPROVAL"), any(), any())).thenReturn(1L);
        when(approvals.countByStatus(AdminApprovalStatus.PENDING)).thenReturn(4L);
        when(errorStore.lifecycleSummary(any(), any())).thenReturn(new OwnerErrorStore.LifecycleSummary(2, 3, 4, 1));
        var result = service.enqueue(LocalDate.of(2026, 9, 17));
        assertEquals(7L, result.backendErrorOccurrences()); assertEquals(4L, result.pendingApprovals());
        assertEquals(2L, result.newErrorGroups()); assertEquals(3L, result.investigatingErrorGroups());
        assertEquals(4L, result.resolvedErrorGroups()); assertEquals(1L, result.reopenedErrorGroups());
        verify(outbox).recordDailySummary(result);
        Instant expected = ZonedDateTime.of(LocalDate.of(2026,9,17), LocalTime.MIDNIGHT, ZoneId.of("Europe/Dublin")).toInstant();
        verify(alerts).sumOccurrencesByCategoryBetween("BACKEND_ERROR", expected, expected.plus(1, java.time.temporal.ChronoUnit.DAYS));
    }

    @Test void invalidConfiguredZoneFallsBackSafely() {
        ReflectionTestUtils.setField(service, "reportZone", "invalid/zone");
        assertDoesNotThrow(service::today);
    }
}

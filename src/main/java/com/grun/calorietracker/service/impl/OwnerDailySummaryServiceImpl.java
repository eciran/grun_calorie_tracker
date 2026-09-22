package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.OwnerDailySummaryDto;
import com.grun.calorietracker.enums.AdminApprovalStatus;
import com.grun.calorietracker.repository.AdminApprovalRequestRepository;
import com.grun.calorietracker.repository.OwnerOperationalAlertRepository;
import com.grun.calorietracker.service.OwnerDailySummaryService;
import com.grun.calorietracker.service.OwnerOperationalAlertService;
import com.grun.calorietracker.service.support.OwnerErrorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
@RequiredArgsConstructor
public class OwnerDailySummaryServiceImpl implements OwnerDailySummaryService {
    private final OwnerOperationalAlertRepository alerts;
    private final AdminApprovalRequestRepository approvals;
    private final OwnerOperationalAlertService alertService;
    private final OwnerErrorStore errorStore;
    @Value("${grun.owner-alerts.daily-report-zone:Europe/Dublin}") private String reportZone;

    @Override @Transactional(readOnly = true)
    public OwnerDailySummaryDto get(LocalDate date) {
        LocalDate safeDate = date == null ? today() : date;
        ZoneId zone = zone(); Instant from = safeDate.atStartOfDay(zone).toInstant(); Instant to = safeDate.plusDays(1).atStartOfDay(zone).toInstant();
        var lifecycle=errorStore.lifecycleSummary(from,to);
        return new OwnerDailySummaryDto(safeDate, Instant.now(),
                alerts.countByCategoryAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan("BACKEND_ERROR", from, to),
                alerts.sumOccurrencesByCategoryBetween("BACKEND_ERROR", from, to),
                alerts.countByCategoryAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan("FINANCIAL_APPROVAL", from, to),
                alerts.countByCategoryAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan("OPERATIONAL_APPROVAL", from, to),
                approvals.countByStatus(AdminApprovalStatus.PENDING),
                approvals.countByStatusAndDecidedAtGreaterThanEqualAndDecidedAtLessThan(AdminApprovalStatus.APPROVED, from, to),
                approvals.countByStatusAndDecidedAtGreaterThanEqualAndDecidedAtLessThan(AdminApprovalStatus.REJECTED, from, to),
                alerts.countByStatusAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan("SENT", from, to),
                alerts.countByStatusAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan("FAILED", from, to),
                lifecycle.newGroups(),lifecycle.investigatingGroups(),lifecycle.resolvedGroups(),lifecycle.reopenedGroups());
    }

    @Override
    public OwnerDailySummaryDto enqueue(LocalDate date) {
        OwnerDailySummaryDto summary = get(date); alertService.recordDailySummary(summary); return summary;
    }

    @Scheduled(cron = "${grun.owner-alerts.daily-report-cron:0 55 23 * * *}", zone = "${grun.owner-alerts.daily-report-zone:Europe/Dublin}")
    public void enqueueScheduled() { enqueue(today()); }

    @Override public LocalDate today() { return LocalDate.now(zone()); }
    private ZoneId zone() { try { return ZoneId.of(reportZone); } catch (DateTimeException ignored) { return ZoneId.of("Europe/Dublin"); } }
}

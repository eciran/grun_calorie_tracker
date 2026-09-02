package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.entity.MealReminderOutboxEntity;
import com.grun.calorietracker.entity.MealReminderScheduleEntity;
import com.grun.calorietracker.enums.MealReminderOutboxStatus;
import com.grun.calorietracker.repository.MealReminderOutboxRepository;
import com.grun.calorietracker.repository.MealReminderScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealReminderClaimService {
    private final MealReminderScheduleRepository scheduleRepository;
    private final MealReminderOutboxRepository outboxRepository;
    private final MealReminderDeliveryProperties properties;

    @Transactional
    public int bootstrapMissingSchedules(Instant now) {
        return scheduleRepository.bootstrapMissing(now, properties.getCandidateBatchSize());
    }

    @Transactional
    public List<Long> claimSchedules(String workerId, Instant now) {
        List<MealReminderScheduleEntity> rows = scheduleRepository.lockDue(now, properties.getCandidateBatchSize());
        rows.forEach(row -> {
            row.setLeaseOwner(workerId);
            row.setLeaseUntil(now.plus(properties.getScheduleLease()));
            row.setUpdatedAt(now);
        });
        scheduleRepository.saveAll(rows);
        return rows.stream().map(MealReminderScheduleEntity::getUserId).toList();
    }

    @Transactional
    public void completeSchedule(Long userId, String workerId, Instant startedAt, Instant completedAt, String errorCode) {
        scheduleRepository.findById(userId).ifPresent(schedule -> {
            if (!workerId.equals(schedule.getLeaseOwner())) return;
            schedule.setLeaseOwner(null);
            schedule.setLeaseUntil(null);
            schedule.setLastDurationMs(Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli()));
            schedule.setLastErrorCode(errorCode);
            schedule.setNextEvaluationAt(completedAt.plus(MealReminderContract.SCAN_INTERVAL)
                    .plusSeconds(jitterSeconds(userId)));
            schedule.setUpdatedAt(completedAt);
            scheduleRepository.save(schedule);
        });
    }

    @Transactional
    public List<Long> claimOutbox(String workerId, Instant now) {
        List<MealReminderOutboxEntity> rows = outboxRepository.lockDue(now, properties.getOutboxBatchSize());
        rows.forEach(row -> {
            row.setStatus(MealReminderOutboxStatus.PROCESSING);
            row.setLeaseOwner(workerId);
            row.setLeaseUntil(now.plus(properties.getOutboxLease()));
            row.setDispatchCount(row.getDispatchCount() + 1);
            row.setUpdatedAt(now);
        });
        outboxRepository.saveAll(rows);
        return rows.stream().map(MealReminderOutboxEntity::getId).toList();
    }

    private long jitterSeconds(Long stableId) {
        long bound = Math.max(0, properties.getDeterministicJitter().toSeconds());
        return bound == 0 ? 0 : Math.floorMod(stableId, bound + 1);
    }
}

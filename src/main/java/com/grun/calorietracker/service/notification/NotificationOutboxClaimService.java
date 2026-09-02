package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.enums.NotificationOccurrenceStatus;
import com.grun.calorietracker.enums.NotificationOutboxStatus;
import com.grun.calorietracker.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationOutboxClaimService {
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationDeliveryProperties properties;

    @Transactional
    public List<Long> claim(String workerId, Instant now) {
        if (workerId == null || workerId.isBlank() || now == null) return List.of();
        return outboxRepository.lockDue(now, Math.max(1, Math.min(properties.getOutboxBatchSize(), 500)))
                .stream().map(outbox -> {
                    outbox.setStatus(NotificationOutboxStatus.PROCESSING);
                    outbox.setLeaseOwner(workerId);
                    outbox.setLeaseUntil(now.plus(properties.getLease()));
                    outbox.setDispatchCount(outbox.getDispatchCount() + 1);
                    outbox.setUpdatedAt(now);
                    outbox.getOccurrence().setStatus(NotificationOccurrenceStatus.PROCESSING);
                    outbox.getOccurrence().setUpdatedAt(now);
                    return outbox.getId();
                }).toList();
    }
}

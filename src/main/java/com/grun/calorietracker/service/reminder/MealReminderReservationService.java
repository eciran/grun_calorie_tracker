package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.MealReminderOccurrenceStatus;
import com.grun.calorietracker.enums.MealReminderOutboxStatus;
import com.grun.calorietracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MealReminderReservationService {
    private final MealReminderDeliveryProperties properties;
    private final MealReminderDailyBudgetRepository budgetRepository;
    private final MealReminderOccurrenceRepository occurrenceRepository;
    private final NotificationRepository notificationRepository;
    private final MealReminderOutboxRepository outboxRepository;
    private final MealReminderPolicyFactory policyFactory;

    @Transactional
    public ReservationResult reserve(UserEntity user, MealReminderDecision decision, Instant now) {
        MealReminderContract.Mode mode = policyFactory.current().mode();
        if (!properties.isDeliveryEnabled()
                || (mode != MealReminderContract.Mode.PILOT && mode != MealReminderContract.Mode.LIVE)) {
            return new ReservationResult(false, null, MealReminderContract.Reason.SYSTEM_DISABLED);
        }
        if (!decision.shouldSend() || decision.slot() == null || decision.expiresAt() == null
                || !now.isBefore(decision.expiresAt())) {
            return new ReservationResult(false, null, MealReminderContract.Reason.STALE_SLOT);
        }
        var existing = occurrenceRepository.findByUserIdAndLocalDateAndSlot(
                user.getId(), decision.localDate(), decision.slot());
        if (existing.isPresent()) {
            return new ReservationResult(false, existing.get().getId(), MealReminderContract.Reason.ALREADY_HANDLED);
        }

        MealReminderDailyBudgetEntity budget = budgetRepository
                .findForUpdate(user.getId(), decision.localDate())
                .orElseGet(() -> newBudget(user, decision, now));
        if (budget.getReservedCount() >= MealReminderContract.MAX_DAILY_OCCURRENCES) {
            return new ReservationResult(false, null, MealReminderContract.Reason.DAILY_LIMIT);
        }
        if (occurrenceRepository.countRollingReservations(user.getId(), now.minusSeconds(86_400), null)
                >= MealReminderContract.MAX_ROLLING_24H_OCCURRENCES) {
            return new ReservationResult(false, null, MealReminderContract.Reason.ROLLING_LIMIT);
        }
        if (decision.candidate() == MealReminderDecision.Candidate.DAILY_CATCHUP
                && budget.getCatchupCount() >= MealReminderContract.MAX_DAILY_CATCHUPS) {
            return new ReservationResult(false, null, MealReminderContract.Reason.CATCHUP_LIMIT);
        }

        NotificationEntity notification = notification(user, decision, now);
        notificationRepository.save(notification);
        MealReminderOccurrenceEntity occurrence = occurrence(user, decision, notification, now);
        occurrenceRepository.save(occurrence);
        MealReminderOutboxEntity outbox = outbox(occurrence, notification, decision, now);
        outboxRepository.save(outbox);

        budget.setReservedCount(budget.getReservedCount() + 1);
        if (decision.candidate() == MealReminderDecision.Candidate.DAILY_CATCHUP) {
            budget.setCatchupCount(budget.getCatchupCount() + 1);
        }
        budget.setUpdatedAt(now);
        budgetRepository.save(budget);
        return new ReservationResult(true, occurrence.getId(), MealReminderContract.Reason.ELIGIBLE);
    }

    private MealReminderDailyBudgetEntity newBudget(UserEntity user, MealReminderDecision decision, Instant now) {
        MealReminderDailyBudgetEntity budget = new MealReminderDailyBudgetEntity();
        budget.setUser(user);
        budget.setLocalDate(decision.localDate());
        budget.setUpdatedAt(now);
        return budgetRepository.saveAndFlush(budget);
    }

    private NotificationEntity notification(UserEntity user, MealReminderDecision decision, Instant now) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(user);
        notification.setTitle(decision.renderedCopy().title());
        notification.setMessage(decision.renderedCopy().body());
        notification.setType("meal_reminder");
        notification.setSeverity("INFO");
        notification.setSource("MEAL_REMINDER");
        notification.setTargetType("DAILY_DIARY");
        notification.setTargetId(decision.localDate().toString());
        notification.setTargetRoute("diary");
        notification.setPrimaryAction("OPEN_DIARY");
        notification.setVisibleInApp(true);
        notification.setIsRead(false);
        notification.setCreatedAt(java.time.LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
        return notification;
    }

    private MealReminderOccurrenceEntity occurrence(
            UserEntity user, MealReminderDecision decision, NotificationEntity notification, Instant now) {
        MealReminderOccurrenceEntity occurrence = new MealReminderOccurrenceEntity();
        occurrence.setUser(user);
        occurrence.setLocalDate(decision.localDate());
        occurrence.setSlot(decision.slot());
        occurrence.setCandidate(decision.candidate());
        occurrence.setMessageVariant(decision.message());
        occurrence.setStatus(MealReminderOccurrenceStatus.QUEUED);
        occurrence.setPolicyVersion(decision.policyVersion());
        occurrence.setEligibleAt(decision.eligibleAt());
        occurrence.setExpiresAt(decision.expiresAt());
        occurrence.setReservationActive(true);
        occurrence.setNotification(notification);
        occurrence.setCreatedAt(now);
        occurrence.setUpdatedAt(now);
        return occurrence;
    }

    private MealReminderOutboxEntity outbox(
            MealReminderOccurrenceEntity occurrence, NotificationEntity notification,
            MealReminderDecision decision, Instant now) {
        MealReminderOutboxEntity outbox = new MealReminderOutboxEntity();
        outbox.setOccurrence(occurrence);
        outbox.setNotification(notification);
        outbox.setStatus(MealReminderOutboxStatus.PENDING);
        outbox.setAvailableAt(now);
        outbox.setExpiresAt(decision.expiresAt());
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        return outbox;
    }

    public record ReservationResult(boolean reserved, Long occurrenceId, MealReminderContract.Reason reason) { }
}

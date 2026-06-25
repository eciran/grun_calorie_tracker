package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FastingDailySummaryDto;
import com.grun.calorietracker.dto.FastingDailyTrendDto;
import com.grun.calorietracker.dto.FastingPlanDto;
import com.grun.calorietracker.dto.FastingPlanRequestDto;
import com.grun.calorietracker.dto.FastingRangeSummaryDto;
import com.grun.calorietracker.dto.FastingSessionCancelRequestDto;
import com.grun.calorietracker.dto.FastingSessionDto;
import com.grun.calorietracker.dto.FastingSessionFinishRequestDto;
import com.grun.calorietracker.dto.FastingSessionPageDto;
import com.grun.calorietracker.dto.FastingSessionStartRequestDto;
import com.grun.calorietracker.entity.FastingPlanEntity;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FastingPlanType;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FastingPlanRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FastingTrackingService;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FastingTrackingServiceImpl implements FastingTrackingService {

    private static final int DEFAULT_FASTING_HOURS = 16;
    private static final int DEFAULT_EATING_WINDOW_HOURS = 8;
    private static final String FASTING_REMINDER_TYPE = "fasting_reminder";
    private static final String FASTING_REMINDER_MESSAGE = "Your fasting window is almost complete.";
    private static final int DEFAULT_SESSION_PAGE_SIZE = 20;
    private static final int MAX_SESSION_PAGE_SIZE = 100;

    private final FastingPlanRepository fastingPlanRepository;
    private final FastingSessionRepository fastingSessionRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserTimeZoneSupport userTimeZoneSupport;
    private final PushDeliveryService pushDeliveryService;

    @Value("${grun.fasting.reminders.enabled:true}")
    private boolean fastingRemindersEnabled;

    @Value("${grun.fasting.reminders.lead-minutes:30}")
    private int fastingReminderLeadMinutes;

    @Override
    @Transactional(readOnly = true)
    public FastingPlanDto getPlan(String email) {
        UserEntity user = getUser(email);
        return toPlanDto(getOrDefaultPlan(user));
    }

    @Override
    @Transactional
    public FastingPlanDto updatePlan(String email, FastingPlanRequestDto request) {
        validatePlanRequest(request);
        UserEntity user = getUser(email);
        FastingPlanEntity plan = fastingPlanRepository.findByUser(user).orElseGet(() -> defaultPlan(user));
        plan.setPlanType(request.getPlanType());
        plan.setFastingHours(request.getFastingHours());
        plan.setEatingWindowHours(request.getEatingWindowHours());
        plan.setPreferredStartTime(request.getPreferredStartTime());
        plan.setActive(request.getActive());
        plan.setReminderEnabled(request.getReminderEnabled());
        plan.setNote(normalizeNote(request.getNote()));
        return toPlanDto(fastingPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public FastingSessionDto startSession(String email, FastingSessionStartRequestDto request) {
        UserEntity user = getUser(email);
        fastingSessionRepository.findTopByUserAndStatusOrderByStartedAtDesc(user, FastingSessionStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("An active fasting session already exists.");
                });

        FastingPlanEntity plan = getOrCreatePlan(user);
        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new IllegalArgumentException("Active fasting plan is required to start a session.");
        }
        LocalDateTime startedAt = request.getStartedAt() == null ? userTimeZoneSupport.now(user) : request.getStartedAt();
        int targetMinutes = request.getTargetMinutes() == null
                ? Math.max(30, plan.getFastingHours() * 60)
                : request.getTargetMinutes();

        FastingSessionEntity session = new FastingSessionEntity();
        session.setUser(user);
        session.setPlan(plan);
        session.setStatus(FastingSessionStatus.ACTIVE);
        session.setFastingDate(startedAt.toLocalDate());
        session.setStartedAt(startedAt);
        session.setTargetMinutes(targetMinutes);
        session.setTargetEndAt(startedAt.plusMinutes(targetMinutes));
        session.setTargetReached(false);
        session.setNote(normalizeNote(request.getNote()));
        return toSessionDto(fastingSessionRepository.save(session));
    }

    @Override
    @Transactional
    public FastingSessionDto finishSession(String email, Long sessionId, FastingSessionFinishRequestDto request) {
        UserEntity user = getUser(email);
        FastingSessionEntity session = fastingSessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Fasting session not found"));
        if (session.getStatus() != FastingSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active fasting sessions can be finished.");
        }

        LocalDateTime endedAt = request.getEndedAt() == null ? userTimeZoneSupport.now(user) : request.getEndedAt();
        if (endedAt.isBefore(session.getStartedAt())) {
            throw new IllegalArgumentException("endedAt must be after startedAt.");
        }

        int actualMinutes = toWholeMinutes(session.getStartedAt(), endedAt);
        session.setEndedAt(endedAt);
        session.setActualMinutes(actualMinutes);
        session.setTargetReached(actualMinutes >= session.getTargetMinutes());
        session.setStatus(FastingSessionStatus.COMPLETED);
        session.setNote(normalizeNote(request.getNote()));
        return toSessionDto(fastingSessionRepository.save(session));
    }

    @Override
    @Transactional
    public FastingSessionDto cancelSession(String email, Long sessionId, FastingSessionCancelRequestDto request) {
        UserEntity user = getUser(email);
        FastingSessionEntity session = fastingSessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Fasting session not found"));
        if (session.getStatus() != FastingSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active fasting sessions can be cancelled.");
        }

        LocalDateTime cancelledAt = request.getCancelledAt() == null ? userTimeZoneSupport.now(user) : request.getCancelledAt();
        if (cancelledAt.isBefore(session.getStartedAt())) {
            throw new IllegalArgumentException("cancelledAt must be after startedAt.");
        }

        session.setEndedAt(cancelledAt);
        session.setActualMinutes(toWholeMinutes(session.getStartedAt(), cancelledAt));
        session.setTargetReached(false);
        session.setStatus(FastingSessionStatus.CANCELLED);
        session.setNote(normalizeNote(request.getNote()));
        return toSessionDto(fastingSessionRepository.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public FastingDailySummaryDto getDailySummary(String email, LocalDate date) {
        UserEntity user = getUser(email);
        List<FastingSessionDto> sessions = fastingSessionRepository.findByUserAndFastingDateOrderByStartedAtAsc(user, date)
                .stream()
                .map(this::toSessionDto)
                .toList();
        FastingSessionDto activeSession = fastingSessionRepository
                .findTopByUserAndStatusOrderByStartedAtDesc(user, FastingSessionStatus.ACTIVE)
                .map(this::toSessionDto)
                .orElse(null);

        FastingDailySummaryDto summary = new FastingDailySummaryDto();
        summary.setDate(date);
        summary.setPlan(toPlanDto(getOrDefaultPlan(user)));
        summary.setActiveSession(activeSession);
        summary.setSessions(sessions);
        summary.setTotalCompletedMinutes(sessions.stream()
                .filter(session -> session.getActualMinutes() != null)
                .mapToInt(FastingSessionDto::getActualMinutes)
                .sum());
        if (activeSession != null) {
            int elapsed = Math.max(0, toWholeMinutes(activeSession.getStartedAt(), userTimeZoneSupport.now(user)));
            summary.setActiveElapsedMinutes(elapsed);
            summary.setActiveRemainingMinutes(Math.max(0, activeSession.getTargetMinutes() - elapsed));
        } else {
            summary.setActiveElapsedMinutes(0);
            summary.setActiveRemainingMinutes(0);
        }
        summary.setCurrentStreakDays(calculateCurrentStreakDays(user, date));
        return summary;
    }

    @Override
    @Transactional(readOnly = true)
    public FastingRangeSummaryDto getRangeSummary(String email, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        UserEntity user = getUser(email);
        List<FastingSessionEntity> sessions = fastingSessionRepository
                .findByUserAndFastingDateBetweenOrderByFastingDateAscStartedAtAsc(user, startDate, endDate);
        List<FastingSessionEntity> completedSessions = sessions.stream()
                .filter(session -> session.getStatus() == FastingSessionStatus.COMPLETED)
                .toList();
        int targetReachedCount = (int) completedSessions.stream()
                .filter(session -> Boolean.TRUE.equals(session.getTargetReached()))
                .count();
        int totalCompletedMinutes = completedSessions.stream()
                .map(FastingSessionEntity::getActualMinutes)
                .filter(minutes -> minutes != null)
                .mapToInt(Integer::intValue)
                .sum();
        int bestSessionMinutes = completedSessions.stream()
                .map(FastingSessionEntity::getActualMinutes)
                .filter(minutes -> minutes != null)
                .max(Comparator.naturalOrder())
                .orElse(0);

        FastingRangeSummaryDto summary = new FastingRangeSummaryDto();
        summary.setStartDate(startDate);
        summary.setEndDate(endDate);
        summary.setDayCount((int) ChronoUnit.DAYS.between(startDate, endDate) + 1);
        summary.setSessionCount(sessions.size());
        summary.setCompletedSessionCount(completedSessions.size());
        summary.setTargetReachedSessionCount(targetReachedCount);
        summary.setTotalCompletedMinutes(totalCompletedMinutes);
        summary.setAverageCompletedMinutes(completedSessions.isEmpty() ? 0 : totalCompletedMinutes / completedSessions.size());
        summary.setBestSessionMinutes(bestSessionMinutes);
        summary.setTargetSuccessRate(completedSessions.isEmpty() ? 0D : (double) targetReachedCount / completedSessions.size());
        summary.setCurrentStreakDays(calculateCurrentStreakDays(user, endDate));
        summary.setDailyTrends(buildDailyTrends(startDate, endDate, sessions));
        return summary;
    }

    @Override
    @Transactional(readOnly = true)
    public FastingSessionPageDto getSessions(
            String email,
            FastingSessionStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        if (startDate != null && endDate != null) {
            validateRange(startDate, endDate);
        }
        UserEntity user = getUser(email);
        Page<FastingSessionEntity> sessionPage = fastingSessionRepository.findHistory(
                user,
                status,
                startDate,
                endDate,
                PageRequest.of(safePage(page), safePageSize(size))
        );

        FastingSessionPageDto dto = new FastingSessionPageDto();
        dto.setContent(sessionPage.getContent().stream().map(this::toSessionDto).toList());
        dto.setPage(sessionPage.getNumber());
        dto.setSize(sessionPage.getSize());
        dto.setTotalElements(sessionPage.getTotalElements());
        dto.setTotalPages(sessionPage.getTotalPages());
        return dto;
    }
    @Override
    @Scheduled(fixedDelayString = "${grun.fasting.reminders.scan-interval-ms:300000}")
    @Transactional
    public int createDueReminderNotifications() {
        if (!fastingRemindersEnabled) {
            return 0;
        }
        List<FastingSessionEntity> dueSessions = fastingSessionRepository.findReminderCandidateSessions(FastingSessionStatus.ACTIVE)
                .stream()
                .filter(this::isReminderDue)
                .toList();
        dueSessions.forEach(session -> {
            LocalDateTime userNow = userTimeZoneSupport.now(session.getUser());
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(session.getUser());
            notification.setType(FASTING_REMINDER_TYPE);
            notification.setMessage(FASTING_REMINDER_MESSAGE);
            notification.setIsRead(false);
            notification.setCreatedAt(userNow);
            NotificationEntity saved = notificationRepository.save(notification);
            pushDeliveryService.deliver(saved);
            session.setReminderSentAt(userNow);
        });
        fastingSessionRepository.saveAll(dueSessions);
        return dueSessions.size();
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private FastingPlanEntity getOrDefaultPlan(UserEntity user) {
        return fastingPlanRepository.findByUser(user).orElseGet(() -> defaultPlan(user));
    }

    private FastingPlanEntity getOrCreatePlan(UserEntity user) {
        return fastingPlanRepository.findByUser(user).orElseGet(() -> fastingPlanRepository.save(defaultPlan(user)));
    }

    private FastingPlanEntity defaultPlan(UserEntity user) {
        FastingPlanEntity plan = new FastingPlanEntity();
        plan.setUser(user);
        plan.setPlanType(FastingPlanType.FASTING_16_8);
        plan.setFastingHours(DEFAULT_FASTING_HOURS);
        plan.setEatingWindowHours(DEFAULT_EATING_WINDOW_HOURS);
        plan.setPreferredStartTime(LocalTime.of(20, 0));
        plan.setActive(true);
        plan.setReminderEnabled(false);
        return plan;
    }

    private void validatePlanRequest(FastingPlanRequestDto request) {
        if (request.getFastingHours() + request.getEatingWindowHours() > 48) {
            throw new IllegalArgumentException("fastingHours and eatingWindowHours total cannot exceed 48.");
        }
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be on or before endDate.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 366) {
            throw new IllegalArgumentException("Fasting summary range cannot exceed 366 days.");
        }
    }

    private List<FastingDailyTrendDto> buildDailyTrends(
            LocalDate startDate,
            LocalDate endDate,
            List<FastingSessionEntity> sessions
    ) {
        Map<LocalDate, List<FastingSessionEntity>> byDate = new LinkedHashMap<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            byDate.put(cursor, new ArrayList<>());
            cursor = cursor.plusDays(1);
        }
        sessions.forEach(session -> byDate.computeIfAbsent(session.getFastingDate(), ignored -> new ArrayList<>()).add(session));

        return byDate.entrySet().stream()
                .map(entry -> {
                    List<FastingSessionEntity> daySessions = entry.getValue();
                    List<FastingSessionEntity> completedSessions = daySessions.stream()
                            .filter(session -> session.getStatus() == FastingSessionStatus.COMPLETED)
                            .toList();
                    FastingDailyTrendDto trend = new FastingDailyTrendDto();
                    trend.setDate(entry.getKey());
                    trend.setSessionCount(daySessions.size());
                    trend.setCompletedSessionCount(completedSessions.size());
                    trend.setTargetReachedSessionCount((int) completedSessions.stream()
                            .filter(session -> Boolean.TRUE.equals(session.getTargetReached()))
                            .count());
                    trend.setTotalCompletedMinutes(completedSessions.stream()
                            .map(FastingSessionEntity::getActualMinutes)
                            .filter(minutes -> minutes != null)
                            .mapToInt(Integer::intValue)
                            .sum());
                    return trend;
                })
                .toList();
    }

    private int calculateCurrentStreakDays(UserEntity user, LocalDate date) {
        int streak = 0;
        LocalDate cursor = date;
        while (fastingSessionRepository.existsByUserAndFastingDateAndStatusAndTargetReachedTrue(
                user,
                cursor,
                FastingSessionStatus.COMPLETED
        )) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int toWholeMinutes(LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        return minutes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) minutes;
    }

    private boolean isReminderDue(FastingSessionEntity session) {
        LocalDateTime now = userTimeZoneSupport.now(session.getUser());
        LocalDateTime threshold = now.plusMinutes(Math.max(1, fastingReminderLeadMinutes));
        return !session.getTargetEndAt().isBefore(now) && !session.getTargetEndAt().isAfter(threshold);
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safePageSize(int size) {
        if (size < 1) {
            return DEFAULT_SESSION_PAGE_SIZE;
        }
        return Math.min(size, MAX_SESSION_PAGE_SIZE);
    }
    private FastingPlanDto toPlanDto(FastingPlanEntity entity) {
        FastingPlanDto dto = new FastingPlanDto();
        dto.setId(entity.getId());
        dto.setPlanType(entity.getPlanType());
        dto.setFastingHours(entity.getFastingHours());
        dto.setEatingWindowHours(entity.getEatingWindowHours());
        dto.setPreferredStartTime(entity.getPreferredStartTime());
        dto.setActive(Boolean.TRUE.equals(entity.getActive()));
        dto.setReminderEnabled(Boolean.TRUE.equals(entity.getReminderEnabled()));
        dto.setNote(entity.getNote());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private FastingSessionDto toSessionDto(FastingSessionEntity entity) {
        FastingSessionDto dto = new FastingSessionDto();
        dto.setId(entity.getId());
        dto.setStatus(entity.getStatus());
        dto.setFastingDate(entity.getFastingDate());
        dto.setStartedAt(entity.getStartedAt());
        dto.setTargetEndAt(entity.getTargetEndAt());
        dto.setEndedAt(entity.getEndedAt());
        dto.setReminderSentAt(entity.getReminderSentAt());
        dto.setTargetMinutes(entity.getTargetMinutes());
        dto.setActualMinutes(entity.getActualMinutes());
        dto.setTargetReached(Boolean.TRUE.equals(entity.getTargetReached()));
        dto.setNote(entity.getNote());
        return dto;
    }
}

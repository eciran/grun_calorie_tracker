package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminUserAnalyticsDayDto;
import com.grun.calorietracker.dto.AdminUserAnalyticsDto;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.SubscriptionPlanCountProjection;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserActivityDailyCountProjection;
import com.grun.calorietracker.repository.UserDailyActivityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminUserAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserAnalyticsServiceImpl implements AdminUserAnalyticsService {

    private static final int MAX_RANGE_DAYS = 366;

    private final UserRepository userRepository;
    private final UserDailyActivityRepository activityRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock analyticsClock;

    @Value("${grun.analytics.time-zone:Europe/Dublin}")
    private String configuredTimeZone;

    @Override
    @Transactional(readOnly = true)
    public AdminUserAnalyticsDto getAnalytics(LocalDate from, LocalDate to, String timeZone) {
        ZoneId zoneId = validateRangeAndResolveZone(from, to, timeZone);
        Instant generatedAt = analyticsClock.instant();
        Instant fromInclusive = from.atStartOfDay(zoneId).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(zoneId).toInstant();

        Map<LocalDate, Long> registrationsByDay = registrationsByDay(
                userRepository.findRegistrationTimestamps(fromInclusive, toExclusive),
                zoneId
        );
        Map<LocalDate, Long> activityByDay = activityByDay(
                activityRepository.countDailyActiveUsers(from, to)
        );
        List<AdminUserAnalyticsDayDto> daily = buildDailySeries(
                from,
                to,
                registrationsByDay,
                activityByDay
        );

        long totalUsers = userRepository.count();
        long registrationsInRange = registrationsByDay.values().stream().mapToLong(Long::longValue).sum();
        long activeUsersInRange = activityRepository.countDistinctUsersBetween(from, to);

        return new AdminUserAnalyticsDto(
                from,
                to,
                zoneId.getId(),
                "DAILY",
                generatedAt,
                totalUsers,
                userRepository.countByCreatedAtIsNull(),
                registrationsInRange,
                activeUsersInRange,
                activityRepository.countByActivityDate(to),
                activityRepository.countDistinctUsersBetween(to.minusDays(6), to),
                activityRepository.countDistinctUsersBetween(to.minusDays(29), to),
                planDistribution(totalUsers),
                daily
        );
    }

    private ZoneId validateRangeAndResolveZone(LocalDate from, LocalDate to, String timeZone) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Analytics from and to dates are required.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Analytics from date must not be after to date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Analytics range must not exceed 366 days.");
        }

        String requestedZone = timeZone == null || timeZone.isBlank() ? configuredTimeZone : timeZone.trim();
        ZoneId configuredZone = parseZone(configuredTimeZone);
        ZoneId requestedZoneId = parseZone(requestedZone);
        if (!configuredZone.equals(requestedZoneId)) {
            throw new IllegalArgumentException(
                    "User analytics timeZone must match the configured analytics time zone: " + configuredZone.getId()
            );
        }
        return configuredZone;
    }

    private ZoneId parseZone(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Analytics timeZone must be a valid IANA time zone.", exception);
        }
    }

    private Map<LocalDate, Long> registrationsByDay(List<Instant> registrationTimestamps, ZoneId zoneId) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Instant timestamp : registrationTimestamps) {
            LocalDate date = timestamp.atZone(zoneId).toLocalDate();
            counts.merge(date, 1L, Long::sum);
        }
        return counts;
    }

    private Map<LocalDate, Long> activityByDay(List<UserActivityDailyCountProjection> rows) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (UserActivityDailyCountProjection row : rows) {
            counts.put(row.getActivityDate(), row.getActiveUsers());
        }
        return counts;
    }

    private List<AdminUserAnalyticsDayDto> buildDailySeries(LocalDate from,
                                                            LocalDate to,
                                                            Map<LocalDate, Long> registrations,
                                                            Map<LocalDate, Long> activeUsers) {
        List<AdminUserAnalyticsDayDto> daily = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            daily.add(new AdminUserAnalyticsDayDto(
                    date,
                    registrations.getOrDefault(date, 0L),
                    activeUsers.getOrDefault(date, 0L)
            ));
        }
        return List.copyOf(daily);
    }

    private Map<SubscriptionPlan, Long> planDistribution(long totalUsers) {
        Map<SubscriptionPlan, Long> counts = new EnumMap<>(SubscriptionPlan.class);
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            counts.put(plan, 0L);
        }
        for (SubscriptionPlanCountProjection row : subscriptionRepository.countCurrentUsersByPlan()) {
            counts.put(row.getPlanType(), row.getSubscriptionCount());
        }

        long paidUsers = counts.entrySet().stream()
                .filter(entry -> entry.getKey() != SubscriptionPlan.FREE)
                .mapToLong(Map.Entry::getValue)
                .sum();
        counts.put(SubscriptionPlan.FREE, Math.max(0, totalUsers - paidUsers));
        return Map.copyOf(counts);
    }
}

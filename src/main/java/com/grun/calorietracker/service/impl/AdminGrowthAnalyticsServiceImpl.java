package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminDashboardGrowthDto;
import com.grun.calorietracker.dto.AdminGrowthFunnelStepDto;
import com.grun.calorietracker.dto.AdminGrowthKpiDto;
import com.grun.calorietracker.dto.AdminGrowthTrendPointDto;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.OnboardingDraftRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.SubscriptionPlanCountProjection;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserActivityDailyCountProjection;
import com.grun.calorietracker.repository.UserDailyActivityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminGrowthAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminGrowthAnalyticsServiceImpl implements AdminGrowthAnalyticsService {

    private static final int MAX_RANGE_DAYS = 90;
    private static final int INACTIVE_AFTER_DAYS = 30;

    private final UserRepository userRepository;
    private final UserDailyActivityRepository activityRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OnboardingDraftRepository onboardingDraftRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final ProductAnalyticsEventRepository productAnalyticsEventRepository;
    private final Clock analyticsClock;

    @Value("${grun.analytics.time-zone:Europe/Dublin}")
    private String configuredTimeZone;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardGrowthDto getGrowth(LocalDate from, LocalDate to, String timeZone) {
        ZoneId zoneId = validateAndResolveZone(from, to, timeZone);
        int rangeDays = Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1);
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(rangeDays - 1L);
        PeriodSnapshot current = snapshot(from, to, zoneId);
        PeriodSnapshot previous = snapshot(previousFrom, previousTo, zoneId);

        long totalUsers = userRepository.count();
        long legacyUsers = userRepository.countByCreatedAtIsNull();
        double coveragePercent = percent(Math.max(0, totalUsers - legacyUsers), totalUsers);
        Instant reportingCutoff = to.plusDays(1).atStartOfDay(zoneId).toInstant();
        long inactiveUsers = userRepository.countKnownInactiveUsers(
                reportingCutoff,
                to.minusDays(INACTIVE_AFTER_DAYS - 1L).atStartOfDay(zoneId).toInstant()
        );

        List<AdminGrowthKpiDto> kpis = List.of(
                withoutComparison(
                        "TOTAL_USERS",
                        "Total users",
                        totalUsers,
                        "COUNT",
                        legacyUsers > 0 ? "PARTIAL" : "COMPLETE",
                        legacyUsers > 0
                                ? legacyUsers + " legacy account(s) have an unknown registration date."
                                : "All users have a reliable registration timestamp.",
                        "users"
                ),
                compared("NEW_USERS", "New users", current.registrations, previous.registrations, "COUNT",
                        "COMPLETE", "Registrations inside the selected period.", "users"),
                compared("VERIFIED_RATE", "Verified rate", current.verifiedRate(), previous.verifiedRate(), "PERCENT",
                        current.missingVerificationTimestamps > 0 ? "PARTIAL" : "COMPLETE",
                        current.missingVerificationTimestamps > 0
                                ? current.missingVerificationTimestamps + " verified registration(s) lack a historical verification timestamp."
                                : "Verified registrations divided by registrations in this cohort.",
                        "userVerification"),
                compared("ONBOARDING_COMPLETION", "Onboarding completion", current.onboardingRate(), previous.onboardingRate(),
                        "PERCENT", "COMPLETE", "Completed onboarding drafts in the registration cohort.", "users"),
                compared("DAU", "Daily active users", current.dau, previous.dau, "COUNT",
                        "COMPLETE", "Distinct users active on the period end date.", "tracking"),
                compared("WAU", "Weekly active users", current.wau, previous.wau, "COUNT",
                        "COMPLETE", "Distinct users active in the trailing 7-day window.", "tracking"),
                compared("MAU", "Monthly active users", current.mau, previous.mau, "COUNT",
                        "COMPLETE", "Distinct users active in the trailing 30-day window.", "tracking"),
                compared("PAID_CONVERSION", "Paid conversion", current.paidConversionRate(), previous.paidConversionRate(),
                        "PERCENT", "COMPLETE", "Currently active PLUS/PRO users in the registration cohort.", "subscriptionAccess"),
                withoutComparison("INACTIVE_USERS", "Inactive users", inactiveUsers, "COUNT", "COMPLETE",
                        "Known non-admin accounts with no activity in the last 30 days.", "users")
        );

        return new AdminDashboardGrowthDto(
                from,
                to,
                previousFrom,
                previousTo,
                zoneId.getId(),
                analyticsClock.instant(),
                rangeDays,
                legacyUsers,
                coveragePercent,
                kpis,
                current.daily,
                funnel(current),
                planDistribution(current.fromInclusive, current.toExclusive, current.registrations),
                dimensionDistribution(userRepository.countRegistrationsByRegion(current.fromInclusive, current.toExclusive)),
                dimensionDistribution(userRepository.countRegistrationsByLanguage(current.fromInclusive, current.toExclusive))
        );
    }

    private PeriodSnapshot snapshot(LocalDate from, LocalDate to, ZoneId zoneId) {
        Instant fromInclusive = from.atStartOfDay(zoneId).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(zoneId).toInstant();
        LocalDateTime localCutoff = to.plusDays(1).atStartOfDay();
        List<Instant> registrationTimestamps = userRepository.findRegistrationTimestamps(fromInclusive, toExclusive);
        long registrations = registrationTimestamps.size();
        long verified = userRepository.countVerifiedRegistrations(fromInclusive, toExclusive, toExclusive);
        long missingVerificationTimestamps = userRepository.countVerifiedRegistrationsWithoutTimestamp(fromInclusive, toExclusive);
        long onboardingCompleted = onboardingDraftRepository.countCompletedForRegistrationCohort(
                fromInclusive,
                toExclusive,
                localCutoff
        );
        long firstLog = foodLogsRepository.countUsersWithLogForRegistrationCohort(fromInclusive, toExclusive, localCutoff);
        long paywall = productAnalyticsEventRepository.countUsersForRegistrationCohort(
                ProductAnalyticsEventType.PAYWALL_VIEWED,
                fromInclusive,
                toExclusive,
                localCutoff
        );
        long subscriptionStarted = productAnalyticsEventRepository.countUsersForRegistrationCohort(
                ProductAnalyticsEventType.SUBSCRIPTION_STARTED,
                fromInclusive,
                toExclusive,
                localCutoff
        );
        long paidUsers = subscriptionRepository.countPaidUsersForRegistrationCohort(
                fromInclusive,
                toExclusive,
                to.plusDays(1)
        );
        long dau = activityRepository.countByActivityDate(to);
        long wau = activityRepository.countDistinctUsersBetween(to.minusDays(6), to);
        long mau = activityRepository.countDistinctUsersBetween(to.minusDays(29), to);

        Map<LocalDate, Long> registrationsByDay = registrationCounts(registrationTimestamps, zoneId);
        Map<LocalDate, Long> activeByDay = activityCounts(activityRepository.countDailyActiveUsers(from, to));
        List<AdminGrowthTrendPointDto> daily = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            daily.add(new AdminGrowthTrendPointDto(
                    date,
                    registrationsByDay.getOrDefault(date, 0L),
                    activeByDay.getOrDefault(date, 0L)
            ));
        }

        return new PeriodSnapshot(
                fromInclusive,
                toExclusive,
                registrations,
                verified,
                missingVerificationTimestamps,
                onboardingCompleted,
                firstLog,
                paywall,
                subscriptionStarted,
                paidUsers,
                dau,
                wau,
                mau,
                List.copyOf(daily)
        );
    }

    private List<AdminGrowthFunnelStepDto> funnel(PeriodSnapshot snapshot) {
        String verificationStatus = snapshot.missingVerificationTimestamps > 0 ? "PARTIAL" : "COMPLETE";
        return List.of(
                funnelStep("REGISTERED", "Registered", snapshot.registrations, snapshot.registrations, "COMPLETE", "users"),
                funnelStep("VERIFIED", "Email verified", snapshot.verified, snapshot.registrations, verificationStatus, "userVerification"),
                funnelStep("ONBOARDING", "Onboarding completed", snapshot.onboardingCompleted, snapshot.registrations, "COMPLETE", "users"),
                funnelStep("FIRST_LOG", "First food log", snapshot.firstLog, snapshot.registrations, "COMPLETE", "tracking"),
                funnelStep("PAYWALL", "Paywall viewed", snapshot.paywall, snapshot.registrations, "COMPLETE", "subscriptions"),
                funnelStep("SUBSCRIBED", "Subscription started", snapshot.subscriptionStarted, snapshot.registrations, "COMPLETE", "subscriptionAccess")
        );
    }

    private AdminGrowthFunnelStepDto funnelStep(String key,
                                                String label,
                                                long users,
                                                long registrations,
                                                String dataStatus,
                                                String targetSection) {
        return new AdminGrowthFunnelStepDto(
                key,
                label,
                users,
                percent(users, registrations),
                dataStatus,
                targetSection
        );
    }

    private Map<String, Long> planDistribution(Instant fromInclusive, Instant toExclusive, long registrations) {
        Map<SubscriptionPlan, Long> counts = new EnumMap<>(SubscriptionPlan.class);
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            counts.put(plan, 0L);
        }
        for (SubscriptionPlanCountProjection row :
                subscriptionRepository.countCurrentUsersByPlanForRegistrationCohort(fromInclusive, toExclusive)) {
            counts.put(row.getPlanType(), row.getSubscriptionCount());
        }
        long paid = counts.entrySet().stream()
                .filter(entry -> entry.getKey() != SubscriptionPlan.FREE)
                .mapToLong(Map.Entry::getValue)
                .sum();
        counts.put(SubscriptionPlan.FREE, Math.max(0, registrations - paid));

        Map<String, Long> result = new LinkedHashMap<>();
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            result.put(plan.name(), counts.getOrDefault(plan, 0L));
        }
        return Map.copyOf(result);
    }

    private Map<String, Long> dimensionDistribution(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || !(row[1] instanceof Number number)) {
                continue;
            }
            result.put(String.valueOf(row[0]), number.longValue());
        }
        return Map.copyOf(result);
    }

    private Map<LocalDate, Long> registrationCounts(List<Instant> timestamps, ZoneId zoneId) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (Instant timestamp : timestamps) {
            result.merge(timestamp.atZone(zoneId).toLocalDate(), 1L, Long::sum);
        }
        return result;
    }

    private Map<LocalDate, Long> activityCounts(List<UserActivityDailyCountProjection> rows) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (UserActivityDailyCountProjection row : rows) {
            result.put(row.getActivityDate(), row.getActiveUsers());
        }
        return result;
    }

    private AdminGrowthKpiDto compared(String key,
                                       String label,
                                       double value,
                                       double previousValue,
                                       String unit,
                                       String dataStatus,
                                       String detail,
                                       String targetSection) {
        return new AdminGrowthKpiDto(
                key,
                label,
                round(value),
                unit,
                round(previousValue),
                changePercent(value, previousValue),
                true,
                dataStatus,
                detail,
                targetSection
        );
    }

    private AdminGrowthKpiDto withoutComparison(String key,
                                                String label,
                                                double value,
                                                String unit,
                                                String dataStatus,
                                                String detail,
                                                String targetSection) {
        return new AdminGrowthKpiDto(
                key,
                label,
                round(value),
                unit,
                null,
                null,
                false,
                dataStatus,
                detail,
                targetSection
        );
    }

    private Double changePercent(double current, double previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : null;
        }
        return round(((current - previous) / previous) * 100.0);
    }

    private double percent(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : round((numerator * 100.0) / denominator);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private ZoneId validateAndResolveZone(LocalDate from, LocalDate to, String timeZone) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Growth analytics from and to dates are required.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Growth analytics from date must not be after to date.");
        }
        long rangeDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (rangeDays < 1 || rangeDays > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Growth analytics range must be between 1 and 90 days.");
        }
        ZoneId configured = parseZone(configuredTimeZone);
        String requested = timeZone == null || timeZone.isBlank() ? configuredTimeZone : timeZone.trim();
        ZoneId requestedZone = parseZone(requested);
        if (!configured.equals(requestedZone)) {
            throw new IllegalArgumentException(
                    "Growth analytics timeZone must match the configured analytics time zone: " + configured.getId()
            );
        }
        return configured;
    }

    private ZoneId parseZone(String value) {
        try {
            return ZoneId.of(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Growth analytics timeZone must be a valid IANA time zone.", exception);
        }
    }

    private record PeriodSnapshot(
            Instant fromInclusive,
            Instant toExclusive,
            long registrations,
            long verified,
            long missingVerificationTimestamps,
            long onboardingCompleted,
            long firstLog,
            long paywall,
            long subscriptionStarted,
            long paidUsers,
            long dau,
            long wau,
            long mau,
            List<AdminGrowthTrendPointDto> daily
    ) {
        double verifiedRate() {
            return registrations == 0 ? 0 : (verified * 100.0) / registrations;
        }

        double onboardingRate() {
            return registrations == 0 ? 0 : (onboardingCompleted * 100.0) / registrations;
        }

        double paidConversionRate() {
            return registrations == 0 ? 0 : (paidUsers * 100.0) / registrations;
        }
    }
}

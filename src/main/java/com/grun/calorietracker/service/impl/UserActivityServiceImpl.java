package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.UserDailyActivityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserActivitySource;
import com.grun.calorietracker.repository.UserDailyActivityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.UserActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivityServiceImpl implements UserActivityService {

    private final UserRepository userRepository;
    private final UserDailyActivityRepository activityRepository;
    private final Clock analyticsClock;

    @Value("${grun.analytics.time-zone:Europe/Dublin}")
    private String analyticsTimeZone;

    @Value("${grun.analytics.activity-throttle-minutes:15}")
    private long activityThrottleMinutes;

    @Value("${grun.analytics.activity-retention-days:400}")
    private long activityRetentionDays;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(String email, UserActivitySource source) {
        if (source != UserActivitySource.PASSWORD_LOGIN
                && source != UserActivitySource.FEDERATED_LOGIN) {
            throw new IllegalArgumentException("Login activity source is invalid.");
        }
        record(email, source, true);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordActivity(String email, UserActivitySource source) {
        record(email, source, false);
    }

    @Scheduled(cron = "${grun.analytics.activity-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void cleanupExpiredActivity() {
        LocalDate today = LocalDate.now(analyticsClock.withZone(resolveAnalyticsZone()));
        LocalDate cutoff = today.minusDays(Math.max(30, activityRetentionDays));
        long deleted = activityRepository.deleteByActivityDateBefore(cutoff);
        if (deleted > 0) {
            log.info("Expired user activity rows deleted count={} cutoff={}", deleted, cutoff);
        }
    }

    private void record(String email, UserActivitySource source, boolean login) {
        UserEntity user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found for activity recording."));
        Instant now = analyticsClock.instant();

        if (!login && isInsideThrottleWindow(user.getLastActiveAt(), now)) {
            return;
        }

        user.setLastActiveAt(now);
        if (login) {
            user.setLastLoginAt(now);
        }

        LocalDate activityDate = now.atZone(resolveAnalyticsZone()).toLocalDate();
        UserDailyActivityEntity activity = activityRepository
                .findByUserIdAndActivityDate(user.getId(), activityDate)
                .orElseGet(() -> newActivity(user, activityDate, now, source));
        activity.setLastSeenAt(now);
        activity.setLastSource(source);
        activityRepository.save(activity);
    }

    private boolean isInsideThrottleWindow(Instant previousActivity, Instant now) {
        if (previousActivity == null) {
            return false;
        }
        long throttleMinutes = Math.max(1, activityThrottleMinutes);
        return previousActivity.isAfter(now.minus(Duration.ofMinutes(throttleMinutes)));
    }

    private UserDailyActivityEntity newActivity(UserEntity user,
                                                LocalDate activityDate,
                                                Instant now,
                                                UserActivitySource source) {
        UserDailyActivityEntity activity = new UserDailyActivityEntity();
        activity.setUser(user);
        activity.setActivityDate(activityDate);
        activity.setFirstSeenAt(now);
        activity.setLastSeenAt(now);
        activity.setFirstSource(source);
        activity.setLastSource(source);
        return activity;
    }

    private ZoneId resolveAnalyticsZone() {
        try {
            return ZoneId.of(analyticsTimeZone);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid grun.analytics.time-zone configuration.", exception);
        }
    }
}

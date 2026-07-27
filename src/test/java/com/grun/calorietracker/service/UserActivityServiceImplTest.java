package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserDailyActivityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserActivitySource;
import com.grun.calorietracker.repository.UserDailyActivityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.UserActivityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceImplTest {

    private static final Instant DUBLIN_DST_INSTANT = Instant.parse("2026-03-29T23:30:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDailyActivityRepository activityRepository;

    private UserActivityServiceImpl activityService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(DUBLIN_DST_INSTANT, ZoneOffset.UTC);
        activityService = new UserActivityServiceImpl(userRepository, activityRepository, clock);
        ReflectionTestUtils.setField(activityService, "analyticsTimeZone", "Europe/Dublin");
        ReflectionTestUtils.setField(activityService, "activityThrottleMinutes", 15L);
        ReflectionTestUtils.setField(activityService, "activityRetentionDays", 400L);
    }

    @Test
    void recordActivity_usesDublinDayBoundaryAndCreatesOneDailyRow() {
        UserEntity user = user();
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(activityRepository.findByUserIdAndActivityDate(7L, LocalDate.of(2026, 3, 30)))
                .thenReturn(Optional.empty());

        activityService.recordActivity(user.getEmail(), UserActivitySource.APP_STARTUP);

        ArgumentCaptor<UserDailyActivityEntity> captor = ArgumentCaptor.forClass(UserDailyActivityEntity.class);
        verify(activityRepository).save(captor.capture());
        assertEquals(LocalDate.of(2026, 3, 30), captor.getValue().getActivityDate());
        assertEquals(DUBLIN_DST_INSTANT, captor.getValue().getFirstSeenAt());
        assertEquals(UserActivitySource.APP_STARTUP, captor.getValue().getFirstSource());
        assertEquals(DUBLIN_DST_INSTANT, user.getLastActiveAt());
        assertNull(user.getLastLoginAt());
    }

    @Test
    void recordActivity_insideThrottleWindow_skipsAdditionalDatabaseWrite() {
        UserEntity user = user();
        user.setLastActiveAt(DUBLIN_DST_INSTANT.minusSeconds(60));
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        activityService.recordActivity(user.getEmail(), UserActivitySource.TOKEN_REFRESH);

        verify(activityRepository, never()).findByUserIdAndActivityDate(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(activityRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertEquals(DUBLIN_DST_INSTANT.minusSeconds(60), user.getLastActiveAt());
    }

    @Test
    void recordLogin_alwaysUpdatesLoginAndExistingDailyRow() {
        UserEntity user = user();
        user.setLastActiveAt(DUBLIN_DST_INSTANT.minusSeconds(60));
        UserDailyActivityEntity existing = new UserDailyActivityEntity();
        existing.setUser(user);
        existing.setActivityDate(LocalDate.of(2026, 3, 30));
        existing.setFirstSeenAt(DUBLIN_DST_INSTANT.minusSeconds(3600));
        existing.setFirstSource(UserActivitySource.APP_STARTUP);
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(activityRepository.findByUserIdAndActivityDate(7L, LocalDate.of(2026, 3, 30)))
                .thenReturn(Optional.of(existing));

        activityService.recordLogin(user.getEmail(), UserActivitySource.PASSWORD_LOGIN);

        assertEquals(DUBLIN_DST_INSTANT, user.getLastLoginAt());
        assertEquals(DUBLIN_DST_INSTANT, user.getLastActiveAt());
        assertEquals(DUBLIN_DST_INSTANT, existing.getLastSeenAt());
        assertEquals(UserActivitySource.PASSWORD_LOGIN, existing.getLastSource());
        verify(activityRepository).save(existing);
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@grun.app");
        return user;
    }
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.DevicePlatform;
import com.grun.calorietracker.enums.PushProvider;
import com.grun.calorietracker.repository.PushDeliveryLogRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import com.grun.calorietracker.service.impl.PushDeliveryServiceImpl;
import com.grun.calorietracker.service.push.LogPushProviderClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushDeliveryServiceImplTest {

    private final UserPushTokenRepository userPushTokenRepository = mock(UserPushTokenRepository.class);
    private final PushDeliveryLogRepository pushDeliveryLogRepository = mock(PushDeliveryLogRepository.class);

    @Test
    void deliver_whenPushDisabled_skipsWithoutTokenLookup() {
        PushProperties properties = new PushProperties();
        properties.setEnabled(false);
        PushDeliveryServiceImpl service = new PushDeliveryServiceImpl(
                properties,
                userPushTokenRepository,
                pushDeliveryLogRepository,
                List.of(new LogPushProviderClient())
        );

        var result = service.deliver(notification(user()));

        assertEquals(1, result.getSkipped());
        verify(userPushTokenRepository, never()).findByUserAndEnabledTrue(any());
    }

    @Test
    void deliver_withLogProvider_sendsAndWritesDeliveryLog() {
        PushProperties properties = new PushProperties();
        properties.setEnabled(true);
        properties.setProvider(PushProvider.LOG);
        UserEntity user = user();
        UserPushTokenEntity token = new UserPushTokenEntity();
        token.setId(20L);
        token.setUser(user);
        token.setProvider(PushProvider.LOG);
        token.setPlatform(DevicePlatform.IOS);
        token.setTokenValue("token");
        token.setEnabled(true);
        when(userPushTokenRepository.findByUserAndEnabledTrue(user)).thenReturn(List.of(token));

        PushDeliveryServiceImpl service = new PushDeliveryServiceImpl(
                properties,
                userPushTokenRepository,
                pushDeliveryLogRepository,
                List.of(new LogPushProviderClient())
        );

        var result = service.deliver(notification(user));

        assertEquals(1, result.getAttempted());
        assertEquals(1, result.getSent());
        verify(pushDeliveryLogRepository).save(any());
    }

    private NotificationEntity notification(UserEntity user) {
        NotificationEntity notification = new NotificationEntity();
        notification.setId(5L);
        notification.setUser(user);
        notification.setType("step_reminder");
        notification.setMessage("Step reminder");
        return notification;
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@grun.app");
        user.setPushNotificationsEnabled(true);
        return user;
    }
}

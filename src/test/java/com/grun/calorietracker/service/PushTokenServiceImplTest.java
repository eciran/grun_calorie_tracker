package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.PushTokenRegisterRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.DevicePlatform;
import com.grun.calorietracker.enums.PushProvider;
import com.grun.calorietracker.repository.PushDeliveryLogRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.PushTokenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushTokenServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserPushTokenRepository userPushTokenRepository = mock(UserPushTokenRepository.class);
    private final PushDeliveryLogRepository pushDeliveryLogRepository = mock(PushDeliveryLogRepository.class);
    private final PushTokenServiceImpl service = new PushTokenServiceImpl(
            userRepository,
            userPushTokenRepository,
            pushDeliveryLogRepository
    );

    @Test
    void register_upsertsTokenAndDoesNotReturnRawToken() {
        UserEntity user = user();
        PushTokenRegisterRequestDto request = new PushTokenRegisterRequestDto();
        request.setProvider(PushProvider.EXPO);
        request.setPlatform(DevicePlatform.IOS);
        request.setDeviceId("ios-1");
        request.setToken("ExponentPushToken[abcdef123456]");

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(userPushTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        when(userPushTokenRepository.save(any(UserPushTokenEntity.class))).thenAnswer(invocation -> {
            UserPushTokenEntity entity = invocation.getArgument(0);
            entity.setId(77L);
            return entity;
        });

        var result = service.register("user@grun.app", request);

        assertEquals(77L, result.getId());
        assertEquals(PushProvider.EXPO, result.getProvider());
        assertEquals(DevicePlatform.IOS, result.getPlatform());
        assertEquals("ios-1", result.getDeviceId());
        assertFalse(result.getTokenPreview().contains("abcdef123456"));
    }

    @Test
    void revoke_disablesTokenAndDeletesDeliveryLogsForToken() {
        UserEntity user = user();
        UserPushTokenEntity token = new UserPushTokenEntity();
        token.setId(10L);
        token.setUser(user);
        token.setProvider(PushProvider.LOG);
        token.setPlatform(DevicePlatform.ANDROID);
        token.setTokenValue("token-value");
        token.setEnabled(true);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(userPushTokenRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(token));
        when(userPushTokenRepository.save(token)).thenReturn(token);

        var result = service.revoke("user@grun.app", 10L);

        assertEquals(false, result.getEnabled());
        verify(pushDeliveryLogRepository).deleteByPushToken(token);
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@grun.app");
        return user;
    }
}

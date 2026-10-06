package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.InMemoryRateLimiter;
import com.grun.calorietracker.service.impl.AdminSecurityAlertServiceImpl;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminSecurityAlertRateLimitTest {
    @Test void repeatedFailuresKeepAuditButOnlySendOneAlert() {
        var notifications = mock(NotificationRepository.class);
        var audit = mock(AdminAuditService.class);
        var service = new AdminSecurityAlertServiceImpl(notifications, mock(UserRepository.class), audit, new InMemoryRateLimiter());
        var user = user();
        service.mfaFailure(user);
        service.mfaFailure(user);
        verify(notifications, times(1)).save(any());
        verify(audit, times(2)).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test void recoveryCodeAlertsAreNeverSuppressedByFailureDeduplication() {
        var notifications = mock(NotificationRepository.class);
        var service = new AdminSecurityAlertServiceImpl(notifications, mock(UserRepository.class), mock(AdminAuditService.class), new InMemoryRateLimiter());
        var user = user();
        service.mfaFailure(user);
        service.recoveryCodeUsed(user);
        service.recoveryCodeUsed(user);
        verify(notifications, times(3)).save(any());
    }

    private UserEntity user() {
        var user = new UserEntity();
        user.setId(1L);
        user.setEmail("owner@example.com");
        return user;
    }
}

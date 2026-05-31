package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.MailFailureAlertServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailFailureAlertServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void notifyAdminForProviderFailure_whenAdminsExist_createsNotificationForEachAdmin() {
        UserEntity admin1 = new UserEntity();
        admin1.setId(1L);
        admin1.setEmail("admin1@grun.local");
        admin1.setRole(UserRole.ADMIN);

        UserEntity admin2 = new UserEntity();
        admin2.setId(2L);
        admin2.setEmail("admin2@grun.local");
        admin2.setRole(UserRole.ADMIN);

        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of(admin1, admin2));

        MailFailureAlertServiceImpl service = new MailFailureAlertServiceImpl(userRepository, notificationRepository);
        service.notifyAdminForProviderFailure("EMAIL_VERIFICATION", "user@example.com", "Brevo timeout");

        ArgumentCaptor<List<NotificationEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<NotificationEntity> notifications = captor.getValue();
        assertThat(notifications).hasSize(2);
        assertThat(notifications.get(0).getType()).isEqualTo("system_alert");
        assertThat(notifications.get(0).getIsRead()).isFalse();
        assertThat(notifications.get(0).getMessage()).contains("flow=EMAIL_VERIFICATION");
        assertThat(notifications.get(0).getMessage()).contains("recipient=user@example.com");
    }

    @Test
    void notifyAdminForProviderFailure_whenNoAdminExists_doesNotPersistNotification() {
        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of());

        MailFailureAlertServiceImpl service = new MailFailureAlertServiceImpl(userRepository, notificationRepository);
        service.notifyAdminForProviderFailure("PASSWORD_RESET", "user@example.com", "Brevo 401");

        verify(notificationRepository, never()).saveAll(anyList());
    }
}

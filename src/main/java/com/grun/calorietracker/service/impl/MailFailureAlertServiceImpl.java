package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.MailFailureAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailFailureAlertServiceImpl implements MailFailureAlertService {

    private static final String ADMIN_ALERT_TYPE = "system_alert";

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Override
    public void notifyAdminForProviderFailure(String flowType, String recipientEmail, String errorMessage) {
        List<UserEntity> admins = userRepository.findByRole(UserRole.ADMIN);
        if (admins.isEmpty()) {
            log.warn(
                    "mail_provider_failure flowType={} recipient={} reason={} adminNotificationSkipped=true",
                    flowType,
                    recipientEmail,
                    errorMessage
            );
            return;
        }

        String message = "Mail provider failure detected. flow=" + flowType
                + ", recipient=" + recipientEmail
                + ", reason=" + errorMessage;
        LocalDateTime now = LocalDateTime.now();
        List<NotificationEntity> notifications = admins.stream().map(admin -> {
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(admin);
            notification.setType(ADMIN_ALERT_TYPE);
            notification.setIsRead(false);
            notification.setMessage(message);
            notification.setCreatedAt(now);
            return notification;
        }).toList();

        notificationRepository.saveAll(notifications);
    }
}

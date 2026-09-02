package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationEngagementEntity;
import com.grun.calorietracker.enums.NotificationEngagementType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEngagementRepository extends JpaRepository<NotificationEngagementEntity, Long> {
    boolean existsByNotificationIdAndUserIdAndEngagementType(Long notificationId, Long userId,
            NotificationEngagementType engagementType);
    long countByEngagementType(NotificationEngagementType engagementType);
    long countByEngagementTypeAndNotification_TypeIn(NotificationEngagementType engagementType,
            java.util.Collection<String> notificationTypes);
}

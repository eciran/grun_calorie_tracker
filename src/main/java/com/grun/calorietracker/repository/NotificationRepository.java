package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    Page<NotificationEntity> findByUser(UserEntity user, Pageable pageable);
    Page<NotificationEntity> findByUserAndIsRead(UserEntity user, Boolean isRead, Pageable pageable);
    Page<NotificationEntity> findByUserAndType(UserEntity user, String type, Pageable pageable);
    Page<NotificationEntity> findByUserAndTypeAndIsRead(UserEntity user, String type, Boolean isRead, Pageable pageable);
    Optional<NotificationEntity> findByIdAndUser(Long id, UserEntity user);
    List<NotificationEntity> findByUserAndIsRead(UserEntity user, Boolean isRead);
}

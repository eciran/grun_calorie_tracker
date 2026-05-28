package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.NotificationDto;
import com.grun.calorietracker.dto.NotificationPageDto;
import com.grun.calorietracker.dto.NotificationReadAllResponseDto;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    public NotificationPageDto listNotifications(String email, Boolean unreadOnly, String type, int page, int size) {
        UserEntity user = getUser(email);
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        String normalizedType = trimToNull(type);
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);
        Page<NotificationEntity> notifications;
        if (normalizedType != null && onlyUnread) {
            notifications = notificationRepository.findByUserAndTypeAndIsRead(user, normalizedType, false, pageable);
        } else if (normalizedType != null) {
            notifications = notificationRepository.findByUserAndType(user, normalizedType, pageable);
        } else if (onlyUnread) {
            notifications = notificationRepository.findByUserAndIsRead(user, false, pageable);
        } else {
            notifications = notificationRepository.findByUser(user, pageable);
        }
        NotificationPageDto dto = new NotificationPageDto();
        dto.setContent(notifications.getContent().stream().map(this::toDto).toList());
        dto.setPage(notifications.getNumber());
        dto.setSize(notifications.getSize());
        dto.setTotalElements(notifications.getTotalElements());
        dto.setTotalPages(notifications.getTotalPages());
        dto.setFirst(notifications.isFirst());
        dto.setLast(notifications.isLast());
        return dto;
    }

    @Override
    @Transactional
    public NotificationDto markAsRead(String email, Long notificationId) {
        UserEntity user = getUser(email);
        NotificationEntity notification = notificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setIsRead(true);
        return toDto(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public NotificationReadAllResponseDto markAllAsRead(String email) {
        UserEntity user = getUser(email);
        List<NotificationEntity> unread = notificationRepository.findByUserAndIsRead(user, false);
        unread.forEach(notification -> notification.setIsRead(true));
        notificationRepository.saveAll(unread);
        return new NotificationReadAllResponseDto(unread.size());
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private NotificationDto toDto(NotificationEntity entity) {
        NotificationDto dto = new NotificationDto();
        dto.setId(entity.getId());
        dto.setMessage(entity.getMessage());
        dto.setType(entity.getType());
        dto.setRead(Boolean.TRUE.equals(entity.getIsRead()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

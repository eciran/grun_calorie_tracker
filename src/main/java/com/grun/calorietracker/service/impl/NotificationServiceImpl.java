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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public NotificationPageDto listNotifications(String email, Boolean unreadOnly, String type, String severity, int page, int size) {
        UserEntity user = getUser(email);
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Specification<NotificationEntity> specification = notificationSpecification(
                user,
                Boolean.TRUE.equals(unreadOnly),
                trimToNull(type),
                trimToNull(severity)
        );
        Page<NotificationEntity> notifications = notificationRepository.findAll(specification, pageable);
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
        dto.setSeverity(entity.getSeverity());
        dto.setSource(entity.getSource());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetId(entity.getTargetId());
        dto.setTargetRoute(entity.getTargetRoute());
        dto.setRead(Boolean.TRUE.equals(entity.getIsRead()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private Specification<NotificationEntity> notificationSpecification(UserEntity user, boolean unreadOnly, String type, String severity) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("user"), user);
            if (unreadOnly) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("isRead"), false));
            }
            if (type != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("type"), type));
            }
            if (severity != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("severity"), severity));
            }
            return predicate;
        };
    }
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

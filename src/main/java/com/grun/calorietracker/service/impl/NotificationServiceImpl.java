package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.NotificationDto;
import com.grun.calorietracker.dto.NotificationPageDto;
import com.grun.calorietracker.dto.NotificationReadAllResponseDto;
import com.grun.calorietracker.entity.NotificationCampaignRecipientEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.NotificationEngagementEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NotificationEngagementType;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationCampaignRecipientRepository;
import com.grun.calorietracker.repository.NotificationCampaignRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.NotificationEngagementRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.NotificationService;
import com.grun.calorietracker.service.NotificationDefinitionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationCampaignRecipientRepository recipientRepository;
    private final NotificationCampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final NotificationDefinitionPolicy definitionPolicy;
    private final NotificationEngagementRepository engagementRepository;

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
                trimToNull(severity),
                definitionPolicy.hiddenInAppTypes()
        );
        Page<NotificationEntity> notifications = notificationRepository.findAll(specification, pageable);
        Map<String, com.grun.calorietracker.entity.NotificationDefinitionEntity> definitions = definitionPolicy.findAll(
                notifications.getContent().stream().map(NotificationEntity::getType).toList());
        NotificationPageDto dto = new NotificationPageDto();
        dto.setContent(notifications.getContent().stream().map(entity -> toDto(entity, definitions)).toList());
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
        notification = notificationRepository.save(notification);
        recordCampaignEngagement(notification, user, NotificationEngagementType.OPENED);
        return toDto(notification, definitionsFor(notification));
    }

    @Override
    @Transactional
    public NotificationDto recordEngagement(String email, Long notificationId, NotificationEngagementType engagementType) {
        return recordEngagement(email, notificationId, engagementType, "IN_APP");
    }

    @Override
    @Transactional
    public NotificationDto recordEngagement(String email, Long notificationId,
            NotificationEngagementType engagementType, String source) {
        UserEntity user = getUser(email);
        NotificationEntity notification = notificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (engagementType == NotificationEngagementType.OPENED || engagementType == NotificationEngagementType.CLICKED) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        }
        recordPlatformEngagement(notification, user, engagementType, source);
        recordCampaignEngagement(notification, user, engagementType);
        return toDto(notification, definitionsFor(notification));
    }

    private void recordPlatformEngagement(NotificationEntity notification, UserEntity user,
            NotificationEngagementType engagementType, String source) {
        String normalizedSource = source == null ? "IN_APP" : source.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalizedSource.equals("IN_APP") && !normalizedSource.equals("PUSH")) {
            throw new IllegalArgumentException("Notification engagement source must be IN_APP or PUSH.");
        }
        if (engagementRepository.existsByNotificationIdAndUserIdAndEngagementType(
                notification.getId(), user.getId(), engagementType)) return;
        NotificationEngagementEntity engagement = new NotificationEngagementEntity();
        engagement.setNotification(notification); engagement.setUser(user);
        engagement.setEngagementType(engagementType); engagement.setSource(normalizedSource);
        engagement.setCreatedAt(Instant.now());
        engagementRepository.save(engagement);
    }

    @Override
    @Transactional
    public NotificationReadAllResponseDto markAllAsRead(String email) {
        UserEntity user = getUser(email);
        List<String> hiddenTypes = definitionPolicy.hiddenInAppTypes();
        List<NotificationEntity> unread = notificationRepository.findByUserAndIsRead(user, false).stream()
                .filter(notification -> Boolean.TRUE.equals(notification.getVisibleInApp()))
                .filter(notification -> notification.getCampaign() != null
                        || !hiddenTypes.contains(definitionPolicy.normalize(notification.getType())))
                .toList();
        unread.forEach(notification -> {
            notification.setIsRead(true);
            recordCampaignEngagement(notification, user, NotificationEngagementType.OPENED);
        });
        notificationRepository.saveAll(unread);
        return new NotificationReadAllResponseDto(unread.size());
    }

    private void recordCampaignEngagement(
            NotificationEntity notification, UserEntity user, NotificationEngagementType engagementType) {
        if (notification.getCampaign() == null) {
            return;
        }
        NotificationCampaignRecipientEntity recipient = recipientRepository
                .findByNotificationIdAndUserId(notification.getId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign delivery record not found"));
        LocalDateTime now = LocalDateTime.now();
        switch (engagementType) {
            case OPENED -> {
                if (recipient.getOpenedAt() == null) {
                    recipient.setOpenedAt(now);
                    campaignRepository.incrementOpened(recipient.getCampaign().getId());
                }
            }
            case CLICKED -> {
                if (recipient.getOpenedAt() == null) {
                    recipient.setOpenedAt(now);
                    campaignRepository.incrementOpened(recipient.getCampaign().getId());
                }
                if (recipient.getClickedAt() == null) {
                    recipient.setClickedAt(now);
                    campaignRepository.incrementClicked(recipient.getCampaign().getId());
                }
            }
            case DISMISSED -> {
                if (recipient.getDismissedAt() == null) {
                    recipient.setDismissedAt(now);
                    campaignRepository.incrementDismissed(recipient.getCampaign().getId());
                }
            }
            case CONVERTED -> {
                if (recipient.getConvertedAt() == null) {
                    recipient.setConvertedAt(now);
                    campaignRepository.incrementConverted(recipient.getCampaign().getId());
                }
            }
        }
        recipientRepository.save(recipient);
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private NotificationDto toDto(NotificationEntity entity, Map<String, com.grun.calorietracker.entity.NotificationDefinitionEntity> definitions) {
        var definition = definitions.get(definitionPolicy.normalize(entity.getType()));
        var presentation = definitionPolicy.presentation(entity, definition);
        NotificationDto dto = new NotificationDto();
        dto.setId(entity.getId());
        dto.setTitle(presentation.title());
        dto.setMessage(presentation.message());
        dto.setNote(entity.getNote());
        dto.setPrimaryAction(entity.getPrimaryAction());
        dto.setActionAmountMl(entity.getActionAmountMl());
        dto.setType(entity.getType());
        dto.setSeverity(presentation.severity());
        dto.setSource(entity.getSource());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetId(entity.getTargetId());
        dto.setTargetRoute(presentation.targetRoute());
        dto.setRead(Boolean.TRUE.equals(entity.getIsRead()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private Map<String, com.grun.calorietracker.entity.NotificationDefinitionEntity> definitionsFor(NotificationEntity notification) {
        if (notification.getType() == null || notification.getType().isBlank()) {
            return Map.of();
        }
        return definitionPolicy.findAll(List.of(notification.getType()));
    }

    private Specification<NotificationEntity> notificationSpecification(UserEntity user, boolean unreadOnly, String type, String severity, List<String> hiddenTypes) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("user"), user),
                    criteriaBuilder.isTrue(root.get("visibleInApp"))
            );
            if (unreadOnly) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("isRead"), false));
            }
            if (type != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("type"), type));
            }
            if (severity != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("severity"), severity));
            }
            if (hiddenTypes != null && !hiddenTypes.isEmpty()) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.or(
                                criteriaBuilder.isNotNull(root.get("campaign")),
                                criteriaBuilder.lower(root.get("type")).in(hiddenTypes).not()
                        ));
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

package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.NotificationCampaignEntity;
import com.grun.calorietracker.entity.NotificationCampaignRecipientEntity;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationCampaignRecipientRepository;
import com.grun.calorietracker.repository.NotificationCampaignRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminNotificationCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminNotificationCampaignServiceImpl implements AdminNotificationCampaignService {
    private final NotificationCampaignRepository campaignRepository;
    private final NotificationCampaignRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationCampaignPageDto list(NotificationCampaignStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationCampaignEntity> result = status == null
                ? campaignRepository.findAll(pageable)
                : campaignRepository.findByStatus(status, pageable);
        AdminNotificationCampaignPageDto dto = new AdminNotificationCampaignPageDto();
        dto.setContent(result.getContent().stream().map(this::toDto).toList());
        dto.setPage(result.getNumber());
        dto.setSize(result.getSize());
        dto.setTotalElements(result.getTotalElements());
        dto.setTotalPages(result.getTotalPages());
        dto.setFirst(result.isFirst());
        dto.setLast(result.isLast());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationCampaignDto get(Long id) {
        return toDto(requireCampaign(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationCampaignRecipientPageDto recipients(
            Long id, NotificationCampaignRecipientStatus status, int page, int size) {
        requireCampaign(id);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "processedAt"));
        var result = status == null
                ? recipientRepository.findByCampaignId(id, pageable)
                : recipientRepository.findByCampaignIdAndStatus(id, status, pageable);
        var rows = result.getContent().stream().map(recipient ->
                new AdminNotificationCampaignRecipientDto(
                        recipient.getId(),
                        maskEmail(recipient.getUser().getEmail()),
                        recipient.getStatus(),
                        recipient.getPushSent(),
                        recipient.getPushFailed(),
                        recipient.getSuppressionReason(),
                        recipient.getProcessedAt(),
                        recipient.getOpenedAt(),
                        recipient.getClickedAt(),
                        recipient.getDismissedAt(),
                        recipient.getConvertedAt()
                )).toList();
        return new AdminNotificationCampaignRecipientPageDto(rows, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    @Transactional
    public AdminNotificationCampaignDto create(AdminNotificationCampaignRequestDto request, String adminEmail, String correlationId) {
        NotificationCampaignEntity entity = new NotificationCampaignEntity();
        applyRequest(entity, request);
        entity.setStatus(NotificationCampaignStatus.DRAFT);
        entity.setCreatedBy(adminEmail);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        entity = campaignRepository.save(entity);
        adminAuditService.record(adminEmail, AdminAuditActionType.NOTIFICATION_CAMPAIGN_CREATE,
                AdminAuditTargetType.NOTIFICATION_CAMPAIGN, entity.getId().toString(), null, auditValue(entity), correlationId);
        return toDto(entity);
    }

    @Override
    @Transactional
    public AdminNotificationCampaignDto update(Long id, AdminNotificationCampaignRequestDto request, String adminEmail, String correlationId) {
        NotificationCampaignEntity entity = requireCampaign(id);
        requireDraft(entity);
        Map<String, Object> oldValue = auditValue(entity);
        applyRequest(entity, request);
        entity.setUpdatedAt(LocalDateTime.now());
        entity = campaignRepository.save(entity);
        adminAuditService.record(adminEmail, AdminAuditActionType.NOTIFICATION_CAMPAIGN_UPDATE,
                AdminAuditTargetType.NOTIFICATION_CAMPAIGN, entity.getId().toString(), oldValue, auditValue(entity), correlationId);
        return toDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationCampaignPreviewDto preview(Long id) {
        NotificationCampaignEntity entity = requireCampaign(id);
        return new AdminNotificationCampaignPreviewDto(entity.getId(), estimateAudience(entity),
                entity.getCategory() == NotificationCampaignCategory.MARKETING);
    }

    @Override
    @Transactional
    public AdminNotificationCampaignDto schedule(Long id, LocalDateTime scheduledAt, String adminEmail, String correlationId) {
        NotificationCampaignEntity entity = campaignRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification campaign not found"));
        requireDraft(entity);
        long audience = estimateAudience(entity);
        entity.setEstimatedAudience(audience);
        entity.setScheduledAt(scheduledAt == null || scheduledAt.isBefore(LocalDateTime.now())
                ? LocalDateTime.now() : scheduledAt);
        entity.setStatus(NotificationCampaignStatus.SCHEDULED);
        entity.setUpdatedAt(LocalDateTime.now());
        entity = campaignRepository.save(entity);
        adminAuditService.record(adminEmail, AdminAuditActionType.NOTIFICATION_CAMPAIGN_SCHEDULE,
                AdminAuditTargetType.NOTIFICATION_CAMPAIGN, entity.getId().toString(), null, auditValue(entity), correlationId);
        return toDto(entity);
    }

    @Override
    @Transactional
    public AdminNotificationCampaignDto cancel(Long id, String adminEmail, String correlationId) {
        NotificationCampaignEntity entity = campaignRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification campaign not found"));
        if (entity.getStatus() != NotificationCampaignStatus.SCHEDULED
                && entity.getStatus() != NotificationCampaignStatus.PROCESSING) {
            throw new IllegalArgumentException("Only scheduled or processing campaigns can be cancelled.");
        }
        NotificationCampaignStatus oldStatus = entity.getStatus();
        entity.setStatus(NotificationCampaignStatus.CANCELLED);
        entity.setUpdatedAt(LocalDateTime.now());
        entity = campaignRepository.save(entity);
        adminAuditService.record(adminEmail, AdminAuditActionType.NOTIFICATION_CAMPAIGN_CANCEL,
                AdminAuditTargetType.NOTIFICATION_CAMPAIGN, entity.getId().toString(),
                Map.of("status", oldStatus), Map.of("status", entity.getStatus()), correlationId);
        return toDto(entity);
    }

    public Specification<UserEntity> audienceSpecification(NotificationCampaignEntity campaign, long afterUserId) {
        return (root, query, cb) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("accountEnabled")));
            predicates.add(cb.isFalse(root.get("accountLocked")));
            predicates.add(cb.notEqual(root.get("role"), UserRole.ADMIN));
            predicates.add(cb.greaterThan(root.get("id"), afterUserId));
            if (campaign.getCategory() == NotificationCampaignCategory.MARKETING) {
                predicates.add(cb.isTrue(root.get("marketingNotificationsEnabled")));
            }
            if (campaign.getTargetRegion() != null) {
                predicates.add(cb.equal(root.get("marketRegion"), campaign.getTargetRegion()));
            }
            if (campaign.getTargetLanguage() != null) {
                predicates.add(cb.equal(root.get("preferredLanguage"), campaign.getTargetLanguage()));
            }
            if (campaign.getCategory() == NotificationCampaignCategory.MARKETING && query != null) {
                Subquery<Long> recentDeliveries = query.subquery(Long.class);
                Root<NotificationCampaignRecipientEntity> recipient = recentDeliveries.from(NotificationCampaignRecipientEntity.class);
                recentDeliveries.select(cb.count(recipient));
                recentDeliveries.where(
                        cb.equal(recipient.get("user").get("id"), root.get("id")),
                        cb.equal(recipient.get("campaign").get("category"), NotificationCampaignCategory.MARKETING),
                        cb.notEqual(recipient.get("status"), NotificationCampaignRecipientStatus.SUPPRESSED),
                        cb.greaterThanOrEqualTo(recipient.get("createdAt"),
                                LocalDateTime.now().minusHours(campaign.getFrequencyCapHours()))
                );
                predicates.add(cb.lt(recentDeliveries, campaign.getFrequencyCapMax().longValue()));
            }
            if (campaign.getTargetPlan() != null && query != null) {
                Subquery<Long> matchingPlan = query.subquery(Long.class);
                Root<SubscriptionEntity> subscription = matchingPlan.from(SubscriptionEntity.class);
                matchingPlan.select(subscription.get("user").get("id"));
                matchingPlan.where(
                        cb.equal(subscription.get("user").get("id"), root.get("id")),
                        cb.equal(subscription.get("planType"), campaign.getTargetPlan())
                );
                Predicate planPredicate = cb.exists(matchingPlan);
                if (campaign.getTargetPlan() == SubscriptionPlan.FREE) {
                    Subquery<Long> anySubscription = query.subquery(Long.class);
                    Root<SubscriptionEntity> any = anySubscription.from(SubscriptionEntity.class);
                    anySubscription.select(any.get("user").get("id"));
                    anySubscription.where(cb.equal(any.get("user").get("id"), root.get("id")));
                    planPredicate = cb.or(planPredicate, cb.not(cb.exists(anySubscription)));
                }
                predicates.add(planPredicate);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private long estimateAudience(NotificationCampaignEntity entity) {
        return userRepository.count(audienceSpecification(entity, 0L));
    }

    private void applyRequest(NotificationCampaignEntity entity, AdminNotificationCampaignRequestDto request) {
        entity.setName(request.getName().trim());
        entity.setTitle(request.getTitle().trim());
        entity.setMessage(request.getMessage().trim());
        entity.setCategory(request.getCategory());
        entity.setChannel(request.getChannel());
        entity.setTargetRoute(trimToNull(request.getTargetRoute()));
        entity.setTargetPlan(request.getTargetPlan());
        entity.setTargetRegion(request.getTargetRegion());
        entity.setTargetLanguage(request.getTargetLanguage());
        entity.setFrequencyCapHours(request.getFrequencyCapHours() == null ? 24 : request.getFrequencyCapHours());
        entity.setFrequencyCapMax(request.getFrequencyCapMax() == null ? 3 : request.getFrequencyCapMax());
    }

    private void requireDraft(NotificationCampaignEntity entity) {
        if (entity.getStatus() != NotificationCampaignStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft notification campaigns can be edited or scheduled.");
        }
    }

    private NotificationCampaignEntity requireCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification campaign not found"));
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "user";
        }
        int separator = email.indexOf('@');
        String local = email.substring(0, separator);
        String masked = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
        return masked + email.substring(separator);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> auditValue(NotificationCampaignEntity entity) {
        return Map.of(
                "name", entity.getName(),
                "category", entity.getCategory(),
                "channel", entity.getChannel(),
                "status", entity.getStatus()
        );
    }

    private AdminNotificationCampaignDto toDto(NotificationCampaignEntity entity) {
        AdminNotificationCampaignDto dto = new AdminNotificationCampaignDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTitle(entity.getTitle());
        dto.setMessage(entity.getMessage());
        dto.setCategory(entity.getCategory());
        dto.setChannel(entity.getChannel());
        dto.setStatus(entity.getStatus());
        dto.setTargetRoute(entity.getTargetRoute());
        dto.setTargetPlan(entity.getTargetPlan());
        dto.setTargetRegion(entity.getTargetRegion());
        dto.setTargetLanguage(entity.getTargetLanguage());
        dto.setScheduledAt(entity.getScheduledAt());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setEstimatedAudience(entity.getEstimatedAudience());
        dto.setProcessedCount(entity.getProcessedCount());
        dto.setInAppCount(entity.getInAppCount());
        dto.setPushSentCount(entity.getPushSentCount());
        dto.setPushSkippedCount(entity.getPushSkippedCount());
        dto.setPushFailedCount(entity.getPushFailedCount());
        dto.setOpenedCount(entity.getOpenedCount());
        dto.setClickedCount(entity.getClickedCount());
        dto.setDismissedCount(entity.getDismissedCount());
        dto.setConvertedCount(entity.getConvertedCount());
        dto.setSuppressedCount(entity.getSuppressedCount());
        dto.setFrequencyCapHours(entity.getFrequencyCapHours());
        dto.setFrequencyCapMax(entity.getFrequencyCapMax());
        dto.setFailureMessage(entity.getFailureMessage());
        return dto;
    }
}
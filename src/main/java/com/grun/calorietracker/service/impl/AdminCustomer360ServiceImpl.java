package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminCustomer360Dto;
import com.grun.calorietracker.dto.AdminUserDto;
import com.grun.calorietracker.dto.AdminUserSupportNoteDto;
import com.grun.calorietracker.dto.AdminUserSupportNoteRequestDto;
import com.grun.calorietracker.entity.AdminUserSupportNoteEntity;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserSubscriptionEntitlementEntity;
import com.grun.calorietracker.repository.AccountSecurityAuditEventRepository;
import com.grun.calorietracker.repository.AdminUserSupportNoteRepository;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserConsentRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.UserSubscriptionEntitlementRepository;
import com.grun.calorietracker.service.AdminCustomer360Service;
import com.grun.calorietracker.service.RefreshTokenService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class AdminCustomer360ServiceImpl implements AdminCustomer360Service {

    private static final int RECENT_LIMIT = 10;
    private static final int AI_SAMPLE_LIMIT = 25;
    private static final int SUPPORT_NOTE_LIMIT = 50;

    private final UserRepository userRepository;
    private final UserService userService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserSubscriptionEntitlementRepository entitlementRepository;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final AccountSecurityAuditEventRepository securityAuditRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserConsentRepository userConsentRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final ProductAnalyticsEventRepository productAnalyticsEventRepository;
    private final AdminUserSupportNoteRepository supportNoteRepository;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public AdminCustomer360Dto getCustomer(Long userId) {
        UserEntity user = requireUser(userId);
        AdminUserDto profile = userService.getById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new AdminCustomer360Dto(
                profileSummary(profile),
                subscriptionSummary(user),
                aiSummary(user),
                notificationSummary(user),
                securitySummary(user),
                consentSummary(user),
                activitySummary(user),
                supportNotes(user)
        );
    }

    private AdminCustomer360Dto.ProfileSummary profileSummary(AdminUserDto profile) {
        return new AdminCustomer360Dto.ProfileSummary(
                profile.getId(),
                profile.getEmail(),
                profile.getName(),
                enumName(profile.getRole()),
                Boolean.TRUE.equals(profile.getEmailVerified()),
                Boolean.TRUE.equals(profile.getPasswordSet()),
                enumName(profile.getMarketRegion()),
                enumName(profile.getPreferredLanguage()),
                !Boolean.FALSE.equals(profile.getAccountEnabled()),
                Boolean.TRUE.equals(profile.getAccountLocked()),
                profile.getCreatedAt(),
                profile.getEmailVerifiedAt(),
                profile.getLastLoginAt(),
                profile.getLastActiveAt()
        );
    }

    @Override
    @Transactional
    public AdminUserSupportNoteDto addSupportNote(Long userId,
                                                  AdminUserSupportNoteRequestDto request,
                                                  String adminEmail) {
        UserEntity user = requireUser(userId);
        AdminUserSupportNoteEntity entity = new AdminUserSupportNoteEntity();
        entity.setUser(user);
        entity.setNote(request.note().trim());
        entity.setTags(String.join(",", normalizeTags(request.tags())));
        entity.setCreatedBy(adminEmail);
        entity.setCreatedAt(LocalDateTime.now());
        return toSupportNote(supportNoteRepository.save(entity));
    }

    @Override
    @Transactional
    public int revokeActiveSessions(Long userId) {
        UserEntity user = requireUser(userId);
        int activeBefore = refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user).size();
        refreshTokenService.revokeAllForUser(user);
        return activeBefore;
    }

    private AdminCustomer360Dto.SubscriptionSummary subscriptionSummary(UserEntity user) {
        SubscriptionEntity subscription = subscriptionRepository.findByUser(user).orElse(null);
        if (subscription == null) {
            return new AdminCustomer360Dto.SubscriptionSummary(
                    "FREE", "NONE", "NONE", null, null, false,
                    0, 0, 0, null, List.of()
            );
        }
        LocalDate today = LocalDate.now();
        List<String> activeFeatures = entitlementRepository.findBySubscription(subscription).stream()
                .filter(UserSubscriptionEntitlementEntity::getEnabled)
                .filter(item -> item.getValidFrom() == null || !item.getValidFrom().isAfter(today))
                .filter(item -> item.getValidUntil() == null || !item.getValidUntil().isBefore(today))
                .map(item -> item.getFeature().name())
                .sorted()
                .toList();
        int addonRemaining = intValue(subscription.getAiAddonQuota()) - intValue(subscription.getAiAddonUsed());
        if (subscription.getAiAddonQuotaExpiresAt() != null
                && subscription.getAiAddonQuotaExpiresAt().isBefore(today)) {
            addonRemaining = 0;
        }
        return new AdminCustomer360Dto.SubscriptionSummary(
                enumName(subscription.getPlanType()),
                enumName(subscription.getStatus()),
                enumName(subscription.getBillingPeriod()),
                subscription.getStartDate(),
                subscription.getEndDate(),
                Boolean.TRUE.equals(subscription.getAutoRenew()),
                intValue(subscription.getAiMonthlyQuota()),
                intValue(subscription.getAiUsedThisPeriod()),
                Math.max(0, addonRemaining),
                subscription.getAiAddonQuotaExpiresAt(),
                activeFeatures
        );
    }

    private AdminCustomer360Dto.AiSummary aiSummary(UserEntity user) {
        List<AiRequestHistoryEntity> recent = aiRequestHistoryRepository.findByUserOrderByCreatedAtDesc(
                user,
                PageRequest.of(0, AI_SAMPLE_LIMIT)
        );
        Map<String, Long> statuses = countBy(recent, item -> enumName(item.getStatus()));
        Map<String, Long> requestTypes = countBy(recent, item -> enumName(item.getRequestType()));
        LocalDateTime lastRequestAt = recent.isEmpty() ? null : recent.get(0).getCreatedAt();
        return new AdminCustomer360Dto.AiSummary(
                aiRequestHistoryRepository.countByUser(user),
                lastRequestAt,
                statuses,
                requestTypes,
                recent.size()
        );
    }

    private AdminCustomer360Dto.NotificationSummary notificationSummary(UserEntity user) {
        var recent = notificationRepository.findByUser(
                user,
                PageRequest.of(0, RECENT_LIMIT)
        ).getContent().stream()
                .map(item -> new AdminCustomer360Dto.NotificationItem(
                        item.getId(),
                        item.getType(),
                        item.getSeverity(),
                        item.getSource(),
                        Boolean.TRUE.equals(item.getIsRead()),
                        item.getCreatedAt()
                ))
                .toList();
        return new AdminCustomer360Dto.NotificationSummary(
                notificationRepository.countByUser(user),
                notificationRepository.countByUserAndIsRead(user, false),
                recent
        );
    }

    private AdminCustomer360Dto.SecuritySummary securitySummary(UserEntity user) {
        var recent = securityAuditRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(),
                PageRequest.of(0, RECENT_LIMIT)
        ).stream()
                .map(item -> new AdminCustomer360Dto.SecurityEvent(
                        item.getId(),
                        enumName(item.getEventType()),
                        enumName(item.getProvider()),
                        item.getResultCode(),
                        item.getCreatedAt()
                ))
                .toList();
        int activeSessions = refreshTokenRepository.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user).size();
        return new AdminCustomer360Dto.SecuritySummary(activeSessions, recent);
    }

    private AdminCustomer360Dto.ConsentSummary consentSummary(UserEntity user) {
        var recent = userConsentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .limit(RECENT_LIMIT)
                .map(item -> new AdminCustomer360Dto.ConsentItem(
                        item.getId(),
                        enumName(item.getConsentType()),
                        item.getVersion(),
                        enumName(item.getStatus()),
                        item.getSource(),
                        item.getCreatedAt()
                ))
                .toList();
        return new AdminCustomer360Dto.ConsentSummary(userConsentRepository.countByUser(user), recent);
    }

    private AdminCustomer360Dto.ActivitySummary activitySummary(UserEntity user) {
        LocalDateTime lastFoodLogAt = foodLogsRepository.findTopByUserOrderByLogDateDesc(user)
                .map(item -> item.getLogDate())
                .orElse(null);
        var recentEvents = productAnalyticsEventRepository.findByUserOrderByCreatedAtDesc(
                user,
                PageRequest.of(0, RECENT_LIMIT)
        ).stream()
                .map(item -> new AdminCustomer360Dto.ActivityEvent(
                        item.getId(),
                        enumName(item.getEventType()),
                        item.getSurface(),
                        item.getCreatedAt()
                ))
                .toList();
        return new AdminCustomer360Dto.ActivitySummary(
                foodLogsRepository.countByUser(user),
                lastFoodLogAt,
                productAnalyticsEventRepository.countByUser(user),
                recentEvents
        );
    }

    private List<AdminUserSupportNoteDto> supportNotes(UserEntity user) {
        return supportNoteRepository.findByUserOrderByCreatedAtDesc(
                user,
                PageRequest.of(0, SUPPORT_NOTE_LIMIT)
        ).stream().map(this::toSupportNote).toList();
    }

    private AdminUserSupportNoteDto toSupportNote(AdminUserSupportNoteEntity entity) {
        List<String> tags = entity.getTags() == null || entity.getTags().isBlank()
                ? List.of()
                : List.of(entity.getTags().split(","));
        return new AdminUserSupportNoteDto(
                entity.getId(),
                entity.getNote(),
                tags,
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    private Set<String> normalizeTags(Set<String> tags) {
        Set<String> normalized = new TreeSet<>();
        if (tags == null) {
            return normalized;
        }
        tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.trim().toUpperCase(Locale.ROOT))
                .forEach(normalized::add);
        return normalized;
    }

    private <T> Map<String, Long> countBy(List<T> items,
                                          java.util.function.Function<T, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (T item : items) {
            counts.merge(classifier.apply(item), 1L, Long::sum);
        }
        return Map.copyOf(counts);
    }

    private UserEntity requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}

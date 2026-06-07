package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionFeatureAccessDto;
import com.grun.calorietracker.dto.SubscriptionPlanFeatureDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventCommand;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.SubscriptionPlanFeatureEntity;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserSubscriptionEntitlementEntity;
import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.SubscriptionPlanFeatureRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.UserSubscriptionEntitlementRepository;
import com.grun.calorietracker.service.MailDeliveryService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanFeatureRepository subscriptionPlanFeatureRepository;
    private final UserSubscriptionEntitlementRepository userSubscriptionEntitlementRepository;
    private final NotificationRepository notificationRepository;
    private final MailDeliveryService mailDeliveryService;

    @Override
    public SubscriptionDto getCurrentSubscription(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        return subscriptionRepository.findByUser(user)
                .map(this::toDto)
                .orElseGet(this::freeSubscription);
    }

    @Override
    public SubscriptionDto getUserSubscriptionForAdmin(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return subscriptionRepository.findByUser(user)
                .map(this::toDto)
                .orElseGet(this::freeSubscription);
    }

    @Override
    public SubscriptionFeatureAccessDto getFeatureAccess(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        return subscriptionRepository.findByUser(user)
                .map(entity -> toFeatureAccess(toDto(entity), entity))
                .orElseGet(() -> toFeatureAccess(freeSubscription(), null));
    }

    @Override
    public boolean hasFeatureAccess(String email, SubscriptionFeature feature) {
        SubscriptionFeatureAccessDto access = getFeatureAccess(email);
        return switch (feature) {
            case AI_WORKOUT_PLANNER -> Boolean.TRUE.equals(access.getAiWorkoutPlanner());
            case AI_RECIPE_GENERATION -> Boolean.TRUE.equals(access.getAiRecipeGeneration());
            case HEALTH_INTEGRATION -> Boolean.TRUE.equals(access.getHealthIntegration());
            case ADVANCED_ANALYTICS -> Boolean.TRUE.equals(access.getAdvancedAnalytics());
            case AD_FREE -> Boolean.TRUE.equals(access.getAdFree());
            case CUSTOM_FOOD_LIBRARY -> Boolean.TRUE.equals(access.getCustomFoodLibrary());
        };
    }

    @Override
    public void assertFeatureAccess(String email, SubscriptionFeature feature) {
        if (!hasFeatureAccess(email, feature)) {
            throw new IllegalArgumentException("Subscription does not allow access to feature: " + feature);
        }
    }

    @Override
    public List<SubscriptionPlanFeatureDto> listPlanFeatures() {
        return subscriptionPlanFeatureRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(SubscriptionPlanFeatureEntity::getPlanType)
                        .thenComparing(SubscriptionPlanFeatureEntity::getFeature))
                .map(this::toPlanFeatureDto)
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionPlanFeatureDto updatePlanFeature(SubscriptionPlan planType,
                                                        SubscriptionFeature feature,
                                                        boolean enabled,
                                                        LocalDate effectiveFrom) {
        SubscriptionPlanFeatureEntity entity = subscriptionPlanFeatureRepository
                .findByPlanTypeAndFeature(planType, feature)
                .orElseGet(SubscriptionPlanFeatureEntity::new);
        boolean wasEnabled = entity.getEnabled() == null
                ? defaultPlanFeatureEnabled(planType, feature)
                : Boolean.TRUE.equals(entity.getEnabled());
        entity.setPlanType(planType);
        entity.setFeature(feature);
        entity.setEnabled(enabled);
        entity.setEffectiveFrom(effectiveFrom == null ? LocalDate.now() : effectiveFrom);
        entity.setUpdatedAt(LocalDateTime.now());
        SubscriptionPlanFeatureDto dto = toPlanFeatureDto(subscriptionPlanFeatureRepository.save(entity));
        if (wasEnabled && !enabled) {
            notifyUsersAboutFutureFeatureRemoval(planType, feature, dto.getEffectiveFrom());
        }
        return dto;
    }

    @Override
    @Transactional
    public SubscriptionDto consumeAiQuota(String email) {
        UserEntity user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        SubscriptionEntity entity = subscriptionRepository.findByUser(user)
                .orElseGet(() -> defaultEntity(user));
        resetAiQuotaIfPeriodExpired(entity);
        SubscriptionDto current = toDto(entity);
        if (!Boolean.TRUE.equals(current.getAiAccessAllowed())) {
            throw new IllegalArgumentException("AI quota is not available for the current subscription.");
        }
        entity.setAiMonthlyQuota(current.getAiMonthlyQuota());
        entity.setAiUsedThisPeriod(current.getAiUsedThisPeriod() + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        SubscriptionEntity saved = subscriptionRepository.save(entity);
        return toDto(saved);
    }

    @Override
    @Transactional
    public SubscriptionDto resetUserAiQuota(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SubscriptionEntity entity = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> defaultEntity(user));
        entity.setAiMonthlyQuota(resolveQuota(entity.getPlanType(), entity.getAiMonthlyQuota()));
        entity.setAiUsedThisPeriod(0);
        ensureQuotaPeriod(entity);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(subscriptionRepository.save(entity));
    }

    @Override
    @Transactional
    public SubscriptionDto grantAiAddonQuota(Long userId, int amount, int validityDays) {
        if (amount <= 0) {
            throw new IllegalArgumentException("AI add-on quota amount must be greater than zero.");
        }
        if (validityDays <= 0) {
            throw new IllegalArgumentException("AI add-on quota validity period must be greater than zero.");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SubscriptionEntity entity = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> defaultEntity(user));
        ensureQuotaPeriod(entity);
        clearExpiredAiAddonQuota(entity);
        LocalDate newExpiresAt = LocalDate.now().plusDays(validityDays - 1L);
        entity.setAiAddonQuota(safeInt(entity.getAiAddonQuota()) + amount);
        entity.setAiAddonQuotaExpiresAt(maxDate(entity.getAiAddonQuotaExpiresAt(), newExpiresAt));
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(subscriptionRepository.save(entity));
    }

    @Override
    @Transactional
    public SubscriptionDto refundConsumedAiQuota(Long userId, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("AI quota refund amount must be greater than zero.");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SubscriptionEntity entity = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> defaultEntity(user));
        ensureQuotaPeriod(entity);
        int used = safeInt(entity.getAiUsedThisPeriod());
        if (amount > used) {
            throw new IllegalArgumentException("AI quota refund amount must not exceed used quota.");
        }
        entity.setAiUsedThisPeriod(used - amount);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(subscriptionRepository.save(entity));
    }

    @Override
    @Transactional
    public SubscriptionDto applyProviderEvent(Long userId, SubscriptionProviderEventCommand command) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SubscriptionEntity entity = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> defaultEntity(user));
        entity.setUser(user);
        clearExpiredAiAddonQuota(entity);

        boolean refreshEntitlements = false;
        if (Boolean.TRUE.equals(command.getRefund())) {
            if (command.getAiAddonQuotaAmount() != null && command.getAiAddonQuotaAmount() > 0) {
                entity.setAiAddonQuota(Math.max(0, safeInt(entity.getAiAddonQuota()) - command.getAiAddonQuotaAmount()));
                if (safeInt(entity.getAiAddonQuota()) == 0) {
                    entity.setAiAddonQuotaExpiresAt(null);
                }
            } else {
                entity.setStatus(SubscriptionStatus.REFUNDED);
                entity.setEndDate(command.getEndDate() == null ? LocalDate.now() : command.getEndDate());
                entity.setAutoRenew(false);
            }
        } else if (command.getAiAddonQuotaAmount() != null && command.getAiAddonQuotaAmount() > 0) {
            ensureQuotaPeriod(entity);
            entity.setAiAddonQuota(safeInt(entity.getAiAddonQuota()) + command.getAiAddonQuotaAmount());
            LocalDate expiresAt = LocalDate.now().plusDays(resolveAddonValidityDays(command) - 1L);
            entity.setAiAddonQuotaExpiresAt(maxDate(entity.getAiAddonQuotaExpiresAt(), expiresAt));
        } else if (command.getPlanType() != null && command.getStatus() != null) {
            refreshEntitlements = true;
            entity.setPlanType(command.getPlanType());
            entity.setStatus(command.getStatus());
            entity.setBillingPeriod(resolveBillingPeriod(command.getStartDate(), command.getEndDate()));
            entity.setStartDate(command.getStartDate() == null ? LocalDate.now() : command.getStartDate());
            entity.setEndDate(command.getEndDate());
            entity.setAiMonthlyQuota(resolveQuota(command.getPlanType(), null));
            entity.setAiQuotaPeriodStartDate(entity.getStartDate());
            entity.setAiQuotaPeriodEndDate(resolvePeriodEnd(entity.getBillingPeriod(), entity.getAiQuotaPeriodStartDate(), entity.getEndDate()));
            entity.setAutoRenew(Boolean.TRUE.equals(command.getAutoRenew()));
        } else if (command.getStatus() != null) {
            entity.setStatus(command.getStatus());
            if (command.getEndDate() != null) {
                entity.setEndDate(command.getEndDate());
            }
            if (command.getAutoRenew() != null) {
                entity.setAutoRenew(command.getAutoRenew());
            }
        }

        entity.setProvider(command.getProvider() == null ? PaymentProvider.REVENUECAT : command.getProvider());
        entity.setProviderCustomerId(trimToNull(command.getProviderCustomerId()));
        entity.setProviderProductId(trimToNull(command.getProviderProductId()));
        entity.setProviderSubscriptionId(trimToNull(command.getProviderSubscriptionId()));
        entity.setProviderTransactionId(trimToNull(command.getProviderTransactionId()));
        entity.setProviderOriginalTransactionId(trimToNull(command.getProviderOriginalTransactionId()));
        entity.setLastProviderEventId(trimToNull(command.getProviderEventId()));
        entity.setUpdatedAt(LocalDateTime.now());
        SubscriptionEntity saved = subscriptionRepository.save(entity);
        if (refreshEntitlements) {
            syncEntitlementsForCurrentPeriod(saved);
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public SubscriptionDto updateUserSubscription(Long userId, AdminSubscriptionUpdateRequestDto request) {
        validateDateRange(request.getStartDate(), request.getEndDate());

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SubscriptionEntity entity = subscriptionRepository.findByUserId(userId).orElseGet(SubscriptionEntity::new);
        clearExpiredAiAddonQuota(entity);
        int quota = resolveQuota(request.getPlanType(), request.getAiMonthlyQuota());
        int used = request.getAiUsedThisPeriod() == null ? 0 : request.getAiUsedThisPeriod();
        validateQuotaUsage(quota + safeInt(entity.getAiAddonQuota()), used);

        entity.setUser(user);
        entity.setPlanType(request.getPlanType());
        entity.setStatus(request.getStatus());
        entity.setBillingPeriod(request.getBillingPeriod());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setAiMonthlyQuota(quota);
        entity.setAiUsedThisPeriod(used);
        entity.setAiQuotaPeriodStartDate(resolvePeriodStart(request.getStartDate()));
        entity.setAiQuotaPeriodEndDate(resolvePeriodEnd(request.getBillingPeriod(), entity.getAiQuotaPeriodStartDate(), request.getEndDate()));
        entity.setAutoRenew(Boolean.TRUE.equals(request.getAutoRenew()));
        entity.setProvider(resolvePaymentProvider(request.getProvider()));
        entity.setProviderSubscriptionId(trimToNull(request.getProviderSubscriptionId()));
        entity.setUpdatedAt(LocalDateTime.now());
        SubscriptionEntity saved = subscriptionRepository.save(entity);
        syncEntitlementsForCurrentPeriod(saved);
        return toDto(saved);
    }

    private SubscriptionDto freeSubscription() {
        SubscriptionDto dto = new SubscriptionDto();
        dto.setPlanType(SubscriptionPlan.FREE);
        dto.setStatus(SubscriptionStatus.ACTIVE);
        dto.setBillingPeriod(BillingPeriod.NONE);
        dto.setQuotaResetDate(null);
        dto.setAiAddonQuotaExpiresAt(null);
        dto.setAiMonthlyQuota(resolveQuota(SubscriptionPlan.FREE, null));
        dto.setAiAddonQuota(0);
        dto.setAiTotalQuotaThisPeriod(dto.getAiMonthlyQuota());
        dto.setAiUsedThisPeriod(0);
        dto.setAiBaseRemainingThisPeriod(dto.getAiMonthlyQuota());
        dto.setAiAddonRemainingThisPeriod(0);
        dto.setAiRemainingThisPeriod(dto.getAiMonthlyQuota());
        dto.setActiveEntitlement(true);
        dto.setAiAccessAllowed(true);
        dto.setUpgradeRecommended(false);
        dto.setAutoRenew(false);
        dto.setProvider(null);
        dto.setProviderProductId(null);
        return dto;
    }

    private SubscriptionDto toDto(SubscriptionEntity entity) {
        SubscriptionDto dto = new SubscriptionDto();
        dto.setPlanType(entity.getPlanType() == null ? SubscriptionPlan.FREE : entity.getPlanType());
        dto.setStatus(entity.getStatus() == null ? SubscriptionStatus.ACTIVE : entity.getStatus());
        dto.setBillingPeriod(entity.getBillingPeriod() == null ? BillingPeriod.NONE : entity.getBillingPeriod());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setQuotaResetDate(resolveQuotaResetDate(entity));
        clearExpiredAiAddonQuota(entity);
        dto.setAiAddonQuotaExpiresAt(entity.getAiAddonQuotaExpiresAt());
        dto.setAiMonthlyQuota(resolveQuota(dto.getPlanType(), entity.getAiMonthlyQuota()));
        dto.setAiAddonQuota(safeInt(entity.getAiAddonQuota()));
        dto.setAiTotalQuotaThisPeriod(dto.getAiMonthlyQuota() + dto.getAiAddonQuota());
        dto.setAiUsedThisPeriod(safeInt(entity.getAiUsedThisPeriod()));
        dto.setAiBaseRemainingThisPeriod(Math.max(0, dto.getAiMonthlyQuota() - dto.getAiUsedThisPeriod()));
        dto.setAiAddonRemainingThisPeriod(Math.max(0, dto.getAiTotalQuotaThisPeriod() - dto.getAiUsedThisPeriod() - dto.getAiBaseRemainingThisPeriod()));
        dto.setAiRemainingThisPeriod(dto.getAiBaseRemainingThisPeriod() + dto.getAiAddonRemainingThisPeriod());
        dto.setActiveEntitlement(isActiveEntitlement(dto.getStatus(), entity.getEndDate()));
        dto.setAiAccessAllowed(Boolean.TRUE.equals(dto.getActiveEntitlement()) && dto.getAiRemainingThisPeriod() > 0);
        dto.setUpgradeRecommended(Boolean.TRUE.equals(dto.getActiveEntitlement()) && dto.getAiRemainingThisPeriod() == 0);
        dto.setAutoRenew(Boolean.TRUE.equals(entity.getAutoRenew()));
        dto.setProvider(entity.getProvider());
        dto.setProviderProductId(entity.getProviderProductId());
        return dto;
    }

    private SubscriptionFeatureAccessDto toFeatureAccess(SubscriptionDto subscription, SubscriptionEntity entity) {
        boolean active = Boolean.TRUE.equals(subscription.getActiveEntitlement());
        SubscriptionFeatureAccessDto dto = new SubscriptionFeatureAccessDto();
        dto.setPlanType(subscription.getPlanType());
        dto.setActiveEntitlement(active);
        dto.setAiWorkoutPlanner(featureAllowed(subscription, entity, SubscriptionFeature.AI_WORKOUT_PLANNER)
                && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setAiRecipeGeneration(featureAllowed(subscription, entity, SubscriptionFeature.AI_RECIPE_GENERATION)
                && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setHealthIntegration(featureAllowed(subscription, entity, SubscriptionFeature.HEALTH_INTEGRATION));
        dto.setAdvancedAnalytics(featureAllowed(subscription, entity, SubscriptionFeature.ADVANCED_ANALYTICS));
        dto.setAdFree(featureAllowed(subscription, entity, SubscriptionFeature.AD_FREE));
        dto.setCustomFoodLibrary(featureAllowed(subscription, entity, SubscriptionFeature.CUSTOM_FOOD_LIBRARY));
        dto.setAiMonthlyQuota(subscription.getAiMonthlyQuota());
        dto.setAiAddonQuota(subscription.getAiAddonQuota());
        dto.setAiRemainingThisPeriod(subscription.getAiRemainingThisPeriod());
        return dto;
    }

    private boolean featureAllowed(SubscriptionDto subscription, SubscriptionEntity entity, SubscriptionFeature feature) {
        if (!Boolean.TRUE.equals(subscription.getActiveEntitlement())) {
            return false;
        }
        if (entity != null
                && entity.getId() != null
                && userSubscriptionEntitlementRepository.countBySubscription(entity) > 0) {
            return userSubscriptionEntitlementRepository.existsActiveFeature(entity.getId(), feature, LocalDate.now());
        }
        return isPlanFeatureEnabled(subscription.getPlanType(), feature);
    }

    private boolean isPlanFeatureEnabled(SubscriptionPlan planType, SubscriptionFeature feature) {
        return subscriptionPlanFeatureRepository.findByPlanTypeAndFeature(planType, feature)
                .map(SubscriptionPlanFeatureEntity::getEnabled)
                .orElseGet(() -> defaultPlanFeatureEnabled(planType, feature));
    }

    private boolean defaultPlanFeatureEnabled(SubscriptionPlan planType, SubscriptionFeature feature) {
        return switch (feature) {
            case AI_WORKOUT_PLANNER, AI_RECIPE_GENERATION, CUSTOM_FOOD_LIBRARY -> true;
            case HEALTH_INTEGRATION, ADVANCED_ANALYTICS -> planType == SubscriptionPlan.PLUS || planType == SubscriptionPlan.PRO;
            case AD_FREE -> planType == SubscriptionPlan.PRO;
        };
    }

    private void syncEntitlementsForCurrentPeriod(SubscriptionEntity entity) {
        if (entity.getUser() == null || entity.getPlanType() == null || !isActiveEntitlement(entity.getStatus(), entity.getEndDate())) {
            return;
        }
        LocalDate validFrom = entity.getStartDate() == null ? LocalDate.now() : entity.getStartDate();
        LocalDate validUntil = entity.getEndDate();
        LocalDateTime now = LocalDateTime.now();
        for (UserSubscriptionEntitlementEntity existing : userSubscriptionEntitlementRepository.findBySubscription(entity)) {
            if (existing.getValidUntil() == null || !existing.getValidUntil().isBefore(validFrom)) {
                existing.setEnabled(false);
                existing.setValidUntil(validFrom.minusDays(1));
                existing.setUpdatedAt(now);
                userSubscriptionEntitlementRepository.save(existing);
            }
        }
        for (SubscriptionFeature feature : SubscriptionFeature.values()) {
            if (isPlanFeatureEnabled(entity.getPlanType(), feature)) {
                UserSubscriptionEntitlementEntity entitlement = new UserSubscriptionEntitlementEntity();
                entitlement.setSubscription(entity);
                entitlement.setUser(entity.getUser());
                entitlement.setFeature(feature);
                entitlement.setEnabled(true);
                entitlement.setSourcePlan(entity.getPlanType());
                entitlement.setValidFrom(validFrom);
                entitlement.setValidUntil(validUntil);
                entitlement.setCreatedAt(now);
                entitlement.setUpdatedAt(now);
                userSubscriptionEntitlementRepository.save(entitlement);
            }
        }
    }

    private SubscriptionPlanFeatureDto toPlanFeatureDto(SubscriptionPlanFeatureEntity entity) {
        SubscriptionPlanFeatureDto dto = new SubscriptionPlanFeatureDto();
        dto.setPlanType(entity.getPlanType());
        dto.setFeature(entity.getFeature());
        dto.setEnabled(entity.getEnabled());
        dto.setEffectiveFrom(entity.getEffectiveFrom());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private void notifyUsersAboutFutureFeatureRemoval(SubscriptionPlan planType,
                                                       SubscriptionFeature feature,
                                                       LocalDate effectiveFrom) {
        List<UserSubscriptionEntitlementEntity> activeEntitlements =
                userSubscriptionEntitlementRepository.findActiveEntitlementsForPlanFeature(planType, feature, LocalDate.now());
        Set<Long> notifiedUserIds = new HashSet<>();
        for (UserSubscriptionEntitlementEntity entitlement : activeEntitlements) {
            UserEntity user = entitlement.getUser();
            if (user == null || user.getId() == null || !notifiedUserIds.add(user.getId())) {
                continue;
            }
            String message = "%s is changing for %s. Your current access remains active until your current subscription period ends."
                    .formatted(feature.name(), planType.name());
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(user);
            notification.setMessage(message);
            notification.setType("subscription");
            notification.setIsRead(false);
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            sendFeatureRemovalEmail(user, planType, feature, effectiveFrom, entitlement.getValidUntil());
        }
    }

    private void sendFeatureRemovalEmail(UserEntity user,
                                         SubscriptionPlan planType,
                                         SubscriptionFeature feature,
                                         LocalDate effectiveFrom,
                                         LocalDate validUntil) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        String subject = "Your GRun plan feature is changing";
        String textBody = """
                Hi,

                We are changing %s availability for the %s plan from %s.
                Your current access remains available until your current subscription period ends%s.

                GRun
                """.formatted(
                feature.name(),
                planType.name(),
                effectiveFrom,
                validUntil == null ? "" : " on " + validUntil
        );
        try {
            mailDeliveryService.sendTransactionalEmail(user.getEmail(), subject, textBody);
        } catch (RuntimeException ex) {
            log.warn("Subscription feature change email could not be sent to userId={}", user.getId(), ex);
        }
    }

    private SubscriptionEntity defaultEntity(UserEntity user) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setUser(user);
        entity.setPlanType(SubscriptionPlan.FREE);
        entity.setStatus(SubscriptionStatus.ACTIVE);
        entity.setBillingPeriod(BillingPeriod.NONE);
        entity.setAiMonthlyQuota(resolveQuota(SubscriptionPlan.FREE, null));
        entity.setAiAddonQuota(0);
        entity.setAiAddonQuotaExpiresAt(null);
        entity.setAiUsedThisPeriod(0);
        entity.setAiQuotaPeriodStartDate(LocalDate.now());
        entity.setAiQuotaPeriodEndDate(null);
        entity.setAutoRenew(false);
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private boolean isActiveEntitlement(SubscriptionStatus status, LocalDate endDate) {
        boolean activeStatus = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING || status == SubscriptionStatus.CANCELED;
        boolean dateValid = endDate == null || !endDate.isBefore(LocalDate.now());
        return activeStatus && dateValid;
    }

    private int resolveAddonValidityDays(SubscriptionProviderEventCommand command) {
        return command.getAiAddonValidityDays() == null || command.getAiAddonValidityDays() <= 0
                ? 30
                : command.getAiAddonValidityDays();
    }

    private BillingPeriod resolveBillingPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return BillingPeriod.MONTHLY;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return days >= 300 ? BillingPeriod.YEARLY : BillingPeriod.MONTHLY;
    }

    private PaymentProvider resolvePaymentProvider(String value) {
        if (value == null || value.isBlank()) {
            return PaymentProvider.MANUAL_ADMIN;
        }
        try {
            return PaymentProvider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported payment provider: " + value);
        }
    }

    private LocalDate resolveQuotaResetDate(SubscriptionEntity entity) {
        if (entity.getAiQuotaPeriodEndDate() != null) {
            return entity.getAiQuotaPeriodEndDate().plusDays(1);
        }
        LocalDate periodEnd = resolvePeriodEnd(
                entity.getBillingPeriod(),
                entity.getAiQuotaPeriodStartDate() == null ? entity.getStartDate() : entity.getAiQuotaPeriodStartDate(),
                entity.getEndDate()
        );
        return periodEnd == null ? null : periodEnd.plusDays(1);
    }

    private LocalDate resolvePeriodStart(LocalDate requestedStartDate) {
        return requestedStartDate == null ? LocalDate.now() : requestedStartDate;
    }

    private LocalDate resolvePeriodEnd(BillingPeriod billingPeriod, LocalDate periodStart, LocalDate subscriptionEndDate) {
        if (periodStart == null) {
            return subscriptionEndDate;
        }
        LocalDate periodEnd = switch (billingPeriod == null ? BillingPeriod.NONE : billingPeriod) {
            case MONTHLY -> periodStart.plusMonths(1).minusDays(1);
            case YEARLY -> periodStart.plusYears(1).minusDays(1);
            case NONE -> subscriptionEndDate;
        };
        if (subscriptionEndDate != null && (periodEnd == null || subscriptionEndDate.isBefore(periodEnd))) {
            return subscriptionEndDate;
        }
        return periodEnd;
    }

    private void resetAiQuotaIfPeriodExpired(SubscriptionEntity entity) {
        if (entity.getAiQuotaPeriodEndDate() == null || !entity.getAiQuotaPeriodEndDate().isBefore(LocalDate.now())) {
            ensureQuotaPeriod(entity);
            return;
        }
        entity.setAiUsedThisPeriod(0);
        entity.setAiQuotaPeriodStartDate(LocalDate.now());
        entity.setAiQuotaPeriodEndDate(resolvePeriodEnd(entity.getBillingPeriod(), entity.getAiQuotaPeriodStartDate(), entity.getEndDate()));
    }

    private void ensureQuotaPeriod(SubscriptionEntity entity) {
        if (entity.getAiQuotaPeriodStartDate() == null) {
            entity.setAiQuotaPeriodStartDate(resolvePeriodStart(entity.getStartDate()));
        }
        if (entity.getAiQuotaPeriodEndDate() == null) {
            entity.setAiQuotaPeriodEndDate(resolvePeriodEnd(entity.getBillingPeriod(), entity.getAiQuotaPeriodStartDate(), entity.getEndDate()));
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Subscription end date must not be before start date.");
        }
    }

    private void validateQuotaUsage(int quota, int used) {
        if (used > quota) {
            throw new IllegalArgumentException("AI usage must not exceed AI monthly quota.");
        }
    }

    private int resolveQuota(SubscriptionPlan plan, Integer explicitQuota) {
        if (explicitQuota != null) {
            return explicitQuota;
        }
        if (plan == SubscriptionPlan.PRO) {
            return 100;
        }
        if (plan == SubscriptionPlan.PLUS) {
            return 15;
        }
        return 3;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void clearExpiredAiAddonQuota(SubscriptionEntity entity) {
        if (entity.getAiAddonQuotaExpiresAt() != null && entity.getAiAddonQuotaExpiresAt().isBefore(LocalDate.now())) {
            entity.setAiAddonQuota(0);
            entity.setAiAddonQuotaExpiresAt(null);
        }
    }

    private LocalDate maxDate(LocalDate current, LocalDate candidate) {
        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

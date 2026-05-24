package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionFeatureAccessDto;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Override
    public SubscriptionDto getCurrentSubscription(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        return subscriptionRepository.findByUser(user)
                .map(this::toDto)
                .orElseGet(this::freeSubscription);
    }

    @Override
    public SubscriptionFeatureAccessDto getFeatureAccess(String email) {
        return toFeatureAccess(getCurrentSubscription(email));
    }

    @Override
    public boolean hasFeatureAccess(String email, SubscriptionFeature feature) {
        SubscriptionFeatureAccessDto access = getFeatureAccess(email);
        return switch (feature) {
            case AI_WORKOUT_PLANNER -> Boolean.TRUE.equals(access.getAiWorkoutPlanner());
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
    @Transactional
    public SubscriptionDto consumeAiQuota(String email) {
        UserEntity user = userRepository.findByEmail(email)
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
        return toDto(subscriptionRepository.save(entity));
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
        entity.setProvider(trimToNull(request.getProvider()));
        entity.setProviderSubscriptionId(trimToNull(request.getProviderSubscriptionId()));
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(subscriptionRepository.save(entity));
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
        return dto;
    }

    private SubscriptionFeatureAccessDto toFeatureAccess(SubscriptionDto subscription) {
        boolean active = Boolean.TRUE.equals(subscription.getActiveEntitlement());
        SubscriptionFeatureAccessDto dto = new SubscriptionFeatureAccessDto();
        dto.setPlanType(subscription.getPlanType());
        dto.setActiveEntitlement(active);
        dto.setAiWorkoutPlanner(active && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setAdvancedAnalytics(active && (subscription.getPlanType() == SubscriptionPlan.PLUS || subscription.getPlanType() == SubscriptionPlan.PRO));
        dto.setAdFree(active && subscription.getPlanType() == SubscriptionPlan.PRO);
        dto.setCustomFoodLibrary(active);
        dto.setAiMonthlyQuota(subscription.getAiMonthlyQuota());
        dto.setAiAddonQuota(subscription.getAiAddonQuota());
        dto.setAiRemainingThisPeriod(subscription.getAiRemainingThisPeriod());
        return dto;
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
        boolean activeStatus = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING;
        boolean dateValid = endDate == null || !endDate.isBefore(LocalDate.now());
        return activeStatus && dateValid;
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

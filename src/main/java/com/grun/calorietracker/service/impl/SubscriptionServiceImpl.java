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
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.SubscriptionFeatureAccessDeniedException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.SubscriptionPlanFeatureRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.UserSubscriptionEntitlementRepository;
import com.grun.calorietracker.service.MailDeliveryService;
import com.grun.calorietracker.service.AiCreditPricingService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final AiCreditPricingService aiCreditPricingService;

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
    public SubscriptionFeatureAccessDto getUserFeatureAccessForAdmin(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return subscriptionRepository.findByUser(user)
                .map(entity -> toFeatureAccess(toDto(entity), entity))
                .orElseGet(() -> toFeatureAccess(freeSubscription(), null));
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
            case AI_MEAL_DRAFTS -> Boolean.TRUE.equals(access.getAiMealDrafts());
            case AI_WORKOUT_PLANNER -> Boolean.TRUE.equals(access.getAiWorkoutPlanner());
            case AI_RECIPE_GENERATION -> Boolean.TRUE.equals(access.getAiRecipeGeneration());
            case AI_MEAL_PREPARATION_GUIDE -> Boolean.TRUE.equals(access.getAiMealPreparationGuide());
            case AI_NUTRITION_PLAN -> Boolean.TRUE.equals(access.getAiNutritionPlan());
            case AI_INSIGHTS -> Boolean.TRUE.equals(access.getAiInsights());
            case HEALTH_INTEGRATION -> Boolean.TRUE.equals(access.getHealthIntegration());
            case ADVANCED_ANALYTICS -> Boolean.TRUE.equals(access.getAdvancedAnalytics());
            case AD_FREE -> Boolean.TRUE.equals(access.getAdFree());
            case BARCODE_SCANNER -> Boolean.TRUE.equals(access.getBarcodeScanner());
            case MANUAL_FOOD_LOGGING -> Boolean.TRUE.equals(access.getManualFoodLogging());
            case FOOD_DIARY -> Boolean.TRUE.equals(access.getFoodDiary());
            case WEIGHT_PROGRESS -> Boolean.TRUE.equals(access.getWeightProgress());
            case WATER_TRACKING -> Boolean.TRUE.equals(access.getWaterTracking());
            case WORKOUT_LOGGING -> Boolean.TRUE.equals(access.getWorkoutLogging());
            case SAVED_MEAL_TEMPLATES -> Boolean.TRUE.equals(access.getSavedMealTemplates());
            case GROCERY_LIST -> Boolean.TRUE.equals(access.getGroceryList());
            case RECIPE_BUILDER -> Boolean.TRUE.equals(access.getRecipeBuilder());
            case PUBLIC_RECIPE_LIBRARY -> Boolean.TRUE.equals(access.getPublicRecipeLibrary());
            case NEXT_MEAL_SUGGESTIONS -> Boolean.TRUE.equals(access.getNextMealSuggestions());
            case ADVANCED_MACRO_TARGETS -> Boolean.TRUE.equals(access.getAdvancedMacroTargets());
            case MICRONUTRIENT_DETAILS -> Boolean.TRUE.equals(access.getMicronutrientDetails());
            case MICRONUTRIENT_ANALYTICS -> Boolean.TRUE.equals(access.getMicronutrientAnalytics());
            case DATA_EXPORT -> Boolean.TRUE.equals(access.getDataExport());
            case FASTING_BASIC -> Boolean.TRUE.equals(access.getFastingBasic());
            case FASTING_ADVANCED -> Boolean.TRUE.equals(access.getFastingAdvanced());
            case CUSTOM_FOOD_LIBRARY -> Boolean.TRUE.equals(access.getCustomFoodLibrary());
        };
    }

    @Override
    public void assertFeatureAccess(String email, SubscriptionFeature feature) {
        if (!hasFeatureAccess(email, feature)) {
            throw new SubscriptionFeatureAccessDeniedException(feature);
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

    public SubscriptionPlanFeatureDto updatePlanFeature(SubscriptionPlan planType,
                                                        SubscriptionFeature feature,
                                                        boolean enabled,
                                                        LocalDate effectiveFrom) {
        return updatePlanFeature(planType, feature, enabled, effectiveFrom, null);
    }

    @Override
    @Transactional
    public SubscriptionPlanFeatureDto updatePlanFeature(SubscriptionPlan planType,
                                                        SubscriptionFeature feature,
                                                        boolean enabled,
                                                        LocalDate effectiveFrom,
                                                        Integer aiCreditCost) {
        if ((feature == SubscriptionFeature.AD_FREE || feature == SubscriptionFeature.BARCODE_SCANNER) && !enabled) {
            throw new IllegalArgumentException(feature + " is enabled for every plan and cannot be disabled.");
        }
        if (planType == SubscriptionPlan.FREE && isAiFeature(feature) && enabled) {
            throw new IllegalArgumentException("FREE plan cannot enable AI features.");
        }
        SubscriptionPlanFeatureEntity entity = subscriptionPlanFeatureRepository
                .findByPlanTypeAndFeature(planType, feature)
                .orElseGet(SubscriptionPlanFeatureEntity::new);
        boolean wasEnabled = entity.getEnabled() == null
                ? defaultPlanFeatureEnabled(planType, feature)
                : Boolean.TRUE.equals(entity.getEnabled());
        entity.setPlanType(planType);
        entity.setFeature(feature);
        entity.setEnabled(enabled);
        int resolvedCreditCost = aiCreditCost == null ? (entity.getAiCreditCost() == null ? 1 : entity.getAiCreditCost()) : aiCreditCost;
        if (resolvedCreditCost < 1 || resolvedCreditCost > 50) {
            throw new IllegalArgumentException("AI credit cost must be between 1 and 50.");
        }
        entity.setAiCreditCost(resolvedCreditCost);
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
        return consumeAiQuota(email, 1);
    }

    @Override
    @Transactional
    public SubscriptionDto consumeAiQuota(String email, int amount) {
        if (amount < 1 || amount > 50) {
            throw new IllegalArgumentException("AI quota amount must be between 1 and 50.");
        }
        UserEntity user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        SubscriptionEntity entity = subscriptionRepository.findByUser(user)
                .orElseGet(() -> defaultEntity(user));
        resetAiQuotaIfPeriodExpired(entity);
        SubscriptionDto current = toDto(entity);
        if (!Boolean.TRUE.equals(current.getAiAccessAllowed())
                || current.getAiRemainingThisPeriod() == null
                || current.getAiRemainingThisPeriod() < amount) {
            throw new IllegalArgumentException("AI quota is not available for the requested operation.");
        }
        entity.setAiMonthlyQuota(current.getAiMonthlyQuota());
        int addonConsumed = Math.min(amount, safeInt(current.getAiAddonRemainingThisPeriod()));
        entity.setAiAddonUsed(safeInt(entity.getAiAddonUsed()) + addonConsumed);
        entity.setAiUsedThisPeriod(current.getAiUsedThisPeriod() + amount);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(subscriptionRepository.save(entity));
    }

    @Override
    public int resolveAiCreditCost(String email, SubscriptionFeature feature) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        return aiCreditPricingService.fixedCost(feature);
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
        entity.setAiAddonUsed(0);
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
        if (entity.getPlanType() == SubscriptionPlan.FREE || !isActiveEntitlement(entity.getStatus(), entity.getEndDate())) {
            throw new IllegalArgumentException("AI add-on quota is available only for an active PLUS or PRO subscription.");
        }
        ensureQuotaPeriod(entity);
        clearExpiredAiAddonQuota(entity);
        LocalDate newExpiresAt = LocalDate.now().plusDays(validityDays);
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
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SubscriptionEntity entity = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> defaultEntity(user));
        ensureQuotaPeriod(entity);
        int used = safeInt(entity.getAiUsedThisPeriod());
        if (amount > used) {
            throw new IllegalArgumentException("AI quota refund amount must not exceed used quota.");
        }
        int addonRefund = Math.min(amount, safeInt(entity.getAiAddonUsed()));
        entity.setAiAddonUsed(safeInt(entity.getAiAddonUsed()) - addonRefund);
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
                int adjustedAddonUsed = Math.min(safeInt(entity.getAiAddonUsed()), safeInt(entity.getAiAddonQuota()));
                int removedConsumedAddon = safeInt(entity.getAiAddonUsed()) - adjustedAddonUsed;
                entity.setAiAddonUsed(adjustedAddonUsed);
                entity.setAiUsedThisPeriod(Math.max(0,
                        safeInt(entity.getAiUsedThisPeriod()) - removedConsumedAddon));
                if (safeInt(entity.getAiAddonQuota()) == 0) {
                    entity.setAiAddonQuotaExpiresAt(null);
                }
            } else {
                entity.setStatus(SubscriptionStatus.REFUNDED);
                entity.setEndDate(command.getEndDate() == null ? LocalDate.now() : command.getEndDate());
                entity.setAutoRenew(false);
            }
        } else if (command.getAiAddonQuotaAmount() != null && command.getAiAddonQuotaAmount() > 0) {
            if (entity.getPlanType() == SubscriptionPlan.FREE || !isActiveEntitlement(entity.getStatus(), entity.getEndDate())) {
                throw new IllegalArgumentException("AI add-on quota is available only for an active PLUS or PRO subscription.");
            }
            ensureQuotaPeriod(entity);
            entity.setAiAddonQuota(safeInt(entity.getAiAddonQuota()) + command.getAiAddonQuotaAmount());
            LocalDate expiresAt = LocalDate.now().plusDays(resolveAddonValidityDays(command));
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
    public SubscriptionFeatureAccessDto applyCurrentFeatureMatrixToUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Optional<SubscriptionEntity> subscription = subscriptionRepository.findByUser(user);
        if (subscription.isEmpty()) {
            return toFeatureAccess(freeSubscription(), null);
        }
        SubscriptionEntity entity = subscription.get();
        clearExpiredAiAddonQuota(entity);
        syncEntitlementsForCurrentPeriod(entity);
        return toFeatureAccess(toDto(entity), entity);
    }
    @Override
    @Transactional
    public SubscriptionDto updateUserSubscription(Long userId, AdminSubscriptionUpdateRequestDto request) {
        validateDateRange(request.getStartDate(), request.getEndDate());
        validateActivePaidSubscriptionDates(request);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SubscriptionEntity entity = subscriptionRepository.findByUserId(userId).orElseGet(SubscriptionEntity::new);
        clearExpiredAiAddonQuota(entity);
        int quota = resolveQuota(request.getPlanType(), request.getAiMonthlyQuota());
        boolean downgradingToFree = request.getPlanType() == SubscriptionPlan.FREE;
        int used = downgradingToFree || request.getAiUsedThisPeriod() == null ? 0 : request.getAiUsedThisPeriod();
        if (downgradingToFree) {
            entity.setAiAddonQuota(0);
            entity.setAiAddonUsed(0);
            entity.setAiAddonQuotaExpiresAt(null);
        }
        validateQuotaUsage(quota + safeInt(entity.getAiAddonQuota()), used);

        entity.setUser(user);
        entity.setPlanType(request.getPlanType());
        entity.setStatus(request.getStatus());
        entity.setBillingPeriod(request.getBillingPeriod());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setAiMonthlyQuota(quota);
        entity.setAiUsedThisPeriod(used);
        entity.setAiAddonUsed(Math.min(safeInt(entity.getAiAddonUsed()), Math.min(safeInt(entity.getAiAddonQuota()), used)));
        entity.setAiQuotaPeriodStartDate(resolvePeriodStart(request.getStartDate()));
        entity.setAiQuotaPeriodEndDate(resolvePeriodEnd(request.getBillingPeriod(), entity.getAiQuotaPeriodStartDate(), request.getEndDate()));
        entity.setAutoRenew(!downgradingToFree && (request.getAutoRenew() == null || Boolean.TRUE.equals(request.getAutoRenew())));
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
        dto.setAiAddonUsed(0);
        dto.setAiTotalQuotaThisPeriod(dto.getAiMonthlyQuota());
        dto.setAiUsedThisPeriod(0);
        dto.setAiBaseRemainingThisPeriod(dto.getAiMonthlyQuota());
        dto.setAiAddonRemainingThisPeriod(0);
        dto.setAiRemainingThisPeriod(dto.getAiMonthlyQuota());
        dto.setActiveEntitlement(true);
        dto.setAiAccessAllowed(false);
        dto.setUpgradeRecommended(true);
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
        dto.setAiAddonUsed(Math.min(dto.getAiAddonQuota(), safeInt(entity.getAiAddonUsed())));
        dto.setAiTotalQuotaThisPeriod(dto.getAiMonthlyQuota() + dto.getAiAddonQuota());
        dto.setAiUsedThisPeriod(safeInt(entity.getAiUsedThisPeriod()));
        int baseUsed = Math.max(0, dto.getAiUsedThisPeriod() - dto.getAiAddonUsed());
        dto.setAiBaseRemainingThisPeriod(Math.max(0, dto.getAiMonthlyQuota() - baseUsed));
        dto.setAiAddonRemainingThisPeriod(Math.max(0, dto.getAiAddonQuota() - dto.getAiAddonUsed()));
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
        FeatureAccessResolution resolution = resolveFeatureAccess(subscription, entity);
        SubscriptionFeatureAccessDto dto = new SubscriptionFeatureAccessDto();
        dto.setPlanType(subscription.getPlanType());
        dto.setActiveEntitlement(active);
        EnumMap<SubscriptionFeature, Integer> aiCreditCosts = new EnumMap<>(SubscriptionFeature.class);
        for (SubscriptionFeature feature : SubscriptionFeature.values()) {
            if (isAiFeature(feature)) {
                aiCreditCosts.put(feature, resolvePlanCreditCost(subscription.getPlanType(), feature));
            }
        }
        dto.setAiCreditCosts(aiCreditCosts);
        dto.setBarcodeScanner(featureAllowed(subscription, resolution, SubscriptionFeature.BARCODE_SCANNER));
        dto.setManualFoodLogging(featureAllowed(subscription, resolution, SubscriptionFeature.MANUAL_FOOD_LOGGING));
        dto.setFoodDiary(featureAllowed(subscription, resolution, SubscriptionFeature.FOOD_DIARY));
        dto.setWeightProgress(featureAllowed(subscription, resolution, SubscriptionFeature.WEIGHT_PROGRESS));
        dto.setWaterTracking(featureAllowed(subscription, resolution, SubscriptionFeature.WATER_TRACKING));
        dto.setWorkoutLogging(featureAllowed(subscription, resolution, SubscriptionFeature.WORKOUT_LOGGING));
        dto.setSavedMealTemplates(featureAllowed(subscription, resolution, SubscriptionFeature.SAVED_MEAL_TEMPLATES));
        dto.setGroceryList(featureAllowed(subscription, resolution, SubscriptionFeature.GROCERY_LIST));
        dto.setRecipeBuilder(featureAllowed(subscription, resolution, SubscriptionFeature.RECIPE_BUILDER));
        dto.setPublicRecipeLibrary(featureAllowed(subscription, resolution, SubscriptionFeature.PUBLIC_RECIPE_LIBRARY));
        dto.setNextMealSuggestions(featureAllowed(subscription, resolution, SubscriptionFeature.NEXT_MEAL_SUGGESTIONS));
        dto.setAdvancedMacroTargets(featureAllowed(subscription, resolution, SubscriptionFeature.ADVANCED_MACRO_TARGETS));
        dto.setMicronutrientDetails(featureAllowed(subscription, resolution, SubscriptionFeature.MICRONUTRIENT_DETAILS));
        dto.setMicronutrientAnalytics(featureAllowed(subscription, resolution, SubscriptionFeature.MICRONUTRIENT_ANALYTICS));
        dto.setDataExport(featureAllowed(subscription, resolution, SubscriptionFeature.DATA_EXPORT));
        dto.setFastingBasic(featureAllowed(subscription, resolution, SubscriptionFeature.FASTING_BASIC));
        dto.setFastingAdvanced(featureAllowed(subscription, resolution, SubscriptionFeature.FASTING_ADVANCED));
        dto.setAiMealDrafts(featureAllowed(subscription, resolution, SubscriptionFeature.AI_MEAL_DRAFTS)
                && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setAiMealDraftsCreditCost(aiCreditCosts.get(SubscriptionFeature.AI_MEAL_DRAFTS));
        dto.setAiWorkoutPlanner(featureAllowed(subscription, resolution, SubscriptionFeature.AI_WORKOUT_PLANNER)
                && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setAiWorkoutPlannerCreditCost(aiCreditCosts.get(SubscriptionFeature.AI_WORKOUT_PLANNER));
        dto.setAiRecipeGeneration(featureAllowed(subscription, resolution, SubscriptionFeature.AI_RECIPE_GENERATION)
                && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setAiRecipeGenerationCreditCost(aiCreditCosts.get(SubscriptionFeature.AI_RECIPE_GENERATION));
        dto.setAiMealPreparationGuide(featureAllowed(subscription, resolution, SubscriptionFeature.AI_MEAL_PREPARATION_GUIDE)
                && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setAiMealPreparationGuideCreditCost(aiCreditCosts.get(SubscriptionFeature.AI_MEAL_PREPARATION_GUIDE));
        dto.setAiNutritionPlan(featureAllowed(subscription, resolution, SubscriptionFeature.AI_NUTRITION_PLAN)
                && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setAiNutritionPlanBaseCreditCost(aiCreditCosts.get(SubscriptionFeature.AI_NUTRITION_PLAN));
        dto.setAiInsights(featureAllowed(subscription, resolution, SubscriptionFeature.AI_INSIGHTS)
                && Boolean.TRUE.equals(subscription.getAiAccessAllowed()));
        dto.setAiInsightsCreditCost(aiCreditCosts.get(SubscriptionFeature.AI_INSIGHTS));
        dto.setHealthIntegration(featureAllowed(subscription, resolution, SubscriptionFeature.HEALTH_INTEGRATION));
        dto.setAdvancedAnalytics(featureAllowed(subscription, resolution, SubscriptionFeature.ADVANCED_ANALYTICS));
        dto.setAdFree(featureAllowed(subscription, resolution, SubscriptionFeature.AD_FREE));
        dto.setCustomFoodLibrary(featureAllowed(subscription, resolution, SubscriptionFeature.CUSTOM_FOOD_LIBRARY));
        dto.setAiMonthlyQuota(subscription.getAiMonthlyQuota());
        dto.setAiAddonQuota(subscription.getAiAddonQuota());
        dto.setAiUsedThisPeriod(subscription.getAiUsedThisPeriod());
        dto.setAiBaseRemainingThisPeriod(subscription.getAiBaseRemainingThisPeriod());
        dto.setAiAddonRemainingThisPeriod(subscription.getAiAddonRemainingThisPeriod());
        dto.setAiAddonQuotaExpiresAt(subscription.getAiAddonQuotaExpiresAt());
        dto.setAiRemainingThisPeriod(subscription.getAiRemainingThisPeriod());
        return dto;
    }

    private FeatureAccessResolution resolveFeatureAccess(SubscriptionDto subscription, SubscriptionEntity entity) {
        if (entity != null && entity.getId() != null) {
            Set<SubscriptionFeature> snapshotFeatures = EnumSet.noneOf(SubscriptionFeature.class);
            snapshotFeatures.addAll(userSubscriptionEntitlementRepository.findActiveFeaturesForSubscription(
                    entity.getId(), subscription.getPlanType(), LocalDate.now()));
            if (!snapshotFeatures.isEmpty()) {
                return new FeatureAccessResolution(true, snapshotFeatures, Map.of());
            }
        }

        EnumMap<SubscriptionFeature, Boolean> planFeatures = new EnumMap<>(SubscriptionFeature.class);
        subscriptionPlanFeatureRepository.findByPlanTypeOrderByFeatureAsc(subscription.getPlanType())
                .forEach(item -> planFeatures.put(item.getFeature(), Boolean.TRUE.equals(item.getEnabled())));
        return new FeatureAccessResolution(false, Set.of(), planFeatures);
    }

    private boolean featureAllowed(SubscriptionDto subscription,
                                   FeatureAccessResolution resolution,
                                   SubscriptionFeature feature) {
        if (!Boolean.TRUE.equals(subscription.getActiveEntitlement())) {
            return false;
        }
        if (feature == SubscriptionFeature.AD_FREE || feature == SubscriptionFeature.BARCODE_SCANNER) {
            return true;
        }
        if (subscription.getPlanType() == SubscriptionPlan.FREE && isAiFeature(feature)) {
            return false;
        }
        if (resolution.snapshotAvailable()) {
            return resolution.snapshotFeatures().contains(feature);
        }
        return resolution.planFeatures().getOrDefault(
                feature,
                defaultPlanFeatureEnabled(subscription.getPlanType(), feature)
        );
    }

    private record FeatureAccessResolution(boolean snapshotAvailable,
                                           Set<SubscriptionFeature> snapshotFeatures,
                                           Map<SubscriptionFeature, Boolean> planFeatures) {
    }

    private int resolvePlanCreditCost(SubscriptionPlan planType, SubscriptionFeature feature) {
        return aiCreditPricingService.fixedCost(feature);
    }

    private boolean isPlanFeatureEnabled(SubscriptionPlan planType, SubscriptionFeature feature) {
        return subscriptionPlanFeatureRepository.findByPlanTypeAndFeature(planType, feature)
                .map(SubscriptionPlanFeatureEntity::getEnabled)
                .orElseGet(() -> defaultPlanFeatureEnabled(planType, feature));
    }

    private boolean defaultPlanFeatureEnabled(SubscriptionPlan planType, SubscriptionFeature feature) {
        if (feature == SubscriptionFeature.ADVANCED_ANALYTICS
                || feature == SubscriptionFeature.MICRONUTRIENT_ANALYTICS) {
            return planType == SubscriptionPlan.PRO;
        }
        if (feature == SubscriptionFeature.NEXT_MEAL_SUGGESTIONS) {
            return planType == SubscriptionPlan.PLUS || planType == SubscriptionPlan.PRO;
        }
        if (feature == SubscriptionFeature.GROCERY_LIST) {
            return planType == SubscriptionPlan.PLUS || planType == SubscriptionPlan.PRO;
        }
        if (feature == SubscriptionFeature.PUBLIC_RECIPE_LIBRARY) {
            return planType == SubscriptionPlan.PLUS || planType == SubscriptionPlan.PRO;
        }
        if (feature == SubscriptionFeature.AD_FREE
                || feature == SubscriptionFeature.BARCODE_SCANNER
                || feature == SubscriptionFeature.MANUAL_FOOD_LOGGING
                || feature == SubscriptionFeature.FOOD_DIARY
                || feature == SubscriptionFeature.WEIGHT_PROGRESS
                || feature == SubscriptionFeature.WATER_TRACKING
                || feature == SubscriptionFeature.WORKOUT_LOGGING
                || feature == SubscriptionFeature.SAVED_MEAL_TEMPLATES
                || feature == SubscriptionFeature.RECIPE_BUILDER
                || feature == SubscriptionFeature.FASTING_BASIC
                || feature == SubscriptionFeature.CUSTOM_FOOD_LIBRARY) {
            return true;
        }
        if (planType == SubscriptionPlan.FREE && isAiFeature(feature)) {
            return false;
        }
        return planType == SubscriptionPlan.PLUS || planType == SubscriptionPlan.PRO;
    }

    private boolean isAiFeature(SubscriptionFeature feature) {
        return switch (feature) {
            case AI_MEAL_DRAFTS, AI_WORKOUT_PLANNER, AI_RECIPE_GENERATION,
                    AI_MEAL_PREPARATION_GUIDE, AI_NUTRITION_PLAN, AI_INSIGHTS -> true;
            default -> false;
        };
    }

    private void syncEntitlementsForCurrentPeriod(SubscriptionEntity entity) {
        if (entity.getUser() == null || entity.getPlanType() == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        for (UserSubscriptionEntitlementEntity existing : userSubscriptionEntitlementRepository.findBySubscription(entity)) {
            if (Boolean.TRUE.equals(existing.getEnabled())
                    && existing.getValidFrom() != null
                    && !existing.getValidFrom().isAfter(today)
                    && (existing.getValidUntil() == null || !existing.getValidUntil().isBefore(today))) {
                existing.setEnabled(false);
                existing.setValidUntil(today.minusDays(1));
                existing.setUpdatedAt(now);
                userSubscriptionEntitlementRepository.save(existing);
            }
        }
        if (entity.getPlanType() == SubscriptionPlan.FREE || !isActiveEntitlement(entity.getStatus(), entity.getEndDate())) {
            return;
        }
        LocalDate validFrom = entity.getStartDate() == null || entity.getStartDate().isBefore(today)
                ? today
                : entity.getStartDate();
        LocalDate validUntil = entity.getEndDate();
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
        dto.setAiCreditCost(entity.getAiCreditCost() == null ? 1 : entity.getAiCreditCost());
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
            boolean turkish = user.getPreferredLanguage() == PreferredLanguage.TR;
            String message = turkish
                    ? "%s planÃ„Â±ndaki bir ÃƒÂ¶zellik deÃ„Å¸iÃ…Å¸iyor. Mevcut eriÃ…Å¸imin, ÃƒÂ¼yelik dÃƒÂ¶nemin sona erene kadar devam edecek."
                        .formatted(planType.name())
                    : "%s is changing for %s. Your current access remains active until your current subscription period ends."
                        .formatted(feature.name(), planType.name());
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(user);
            notification.setMessage(message);
            notification.setType("subscription");
            notification.setTitle(turkish ? "PlanÃ„Â±nla ilgili bir bilgilendirme" : "A heads-up about your plan");
            notification.setSeverity("INFO");
            notification.setSource("SUBSCRIPTION_UPDATE");
            notification.setTargetType("SUBSCRIPTION_FEATURE");
            notification.setTargetId(feature.name());
            notification.setTargetRoute("manage-subscription");
            notification.setPrimaryAction("MANAGE_SUBSCRIPTION");
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
            sendEmailAfterCommit(user, subject, textBody);
        } catch (RuntimeException ex) {
            log.warn("Subscription feature change email could not be sent to userId={}", user.getId(), ex);
        }
    }

    private void sendEmailAfterCommit(UserEntity user, String subject, String textBody) {
        String email = user.getEmail();
        Long userId = user.getId();
        Runnable mailTask = () -> {
            try {
                mailDeliveryService.sendTransactionalEmail(email, subject, textBody);
            } catch (RuntimeException ex) {
                log.warn("Subscription feature change email could not be sent to userId={}", userId, ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mailTask.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailTask.run();
            }
        });
    }

    private SubscriptionEntity defaultEntity(UserEntity user) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setUser(user);
        entity.setPlanType(SubscriptionPlan.FREE);
        entity.setStatus(SubscriptionStatus.ACTIVE);
        entity.setBillingPeriod(BillingPeriod.NONE);
        entity.setAiMonthlyQuota(resolveQuota(SubscriptionPlan.FREE, null));
        entity.setAiAddonQuota(0);
        entity.setAiAddonUsed(0);
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
        entity.setAiUsedThisPeriod(safeInt(entity.getAiAddonUsed()));
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

    private void validateActivePaidSubscriptionDates(AdminSubscriptionUpdateRequestDto request) {
        boolean activePaidPlan = request.getPlanType() != SubscriptionPlan.FREE
                && (request.getStatus() == SubscriptionStatus.ACTIVE
                || request.getStatus() == SubscriptionStatus.TRIALING);
        if (activePaidPlan && request.getEndDate() != null && request.getEndDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("An active paid subscription end date must be today or later.");
        }
    }
    private void validateQuotaUsage(int quota, int used) {
        if (used > quota) {
            throw new IllegalArgumentException("AI usage must not exceed AI monthly quota.");
        }
    }

    private int resolveQuota(SubscriptionPlan plan, Integer explicitQuota) {
        if (plan == SubscriptionPlan.FREE) {
            return 0;
        }
        if (explicitQuota != null) {
            return explicitQuota;
        }
        if (plan == SubscriptionPlan.PRO) {
            return 150;
        }
        if (plan == SubscriptionPlan.PLUS) {
            return 50;
        }
        return 0;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void clearExpiredAiAddonQuota(SubscriptionEntity entity) {
        if (entity.getAiAddonQuotaExpiresAt() != null && entity.getAiAddonQuotaExpiresAt().isBefore(LocalDate.now())) {
            entity.setAiUsedThisPeriod(Math.max(0, safeInt(entity.getAiUsedThisPeriod()) - safeInt(entity.getAiAddonUsed())));
            entity.setAiAddonQuota(0);
            entity.setAiAddonUsed(0);
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

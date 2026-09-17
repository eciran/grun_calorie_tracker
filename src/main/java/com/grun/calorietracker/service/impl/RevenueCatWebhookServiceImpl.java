package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.RevenueCatWebhookEventDto;
import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.dto.PromoProviderRedemptionCommand;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventCommand;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserConsentEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.LegalConsentType;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.RevenueCatEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.UserConsentRepository;
import com.grun.calorietracker.service.RevenueCatWebhookService;
import com.grun.calorietracker.service.PromoProviderRedemptionService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.notification.RevenueCatLifecycleNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RevenueCatWebhookServiceImpl implements RevenueCatWebhookService {

    private static final String REFUND_CANCEL_REASON = "CUSTOMER_SUPPORT";
    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");
    private static final Pattern APP_USER_ID = Pattern.compile("^user:(\\d+)$");

    private final RevenueCatProperties properties;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionProviderEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final PromoProviderRedemptionService promoProviderRedemptionService;
    private final RevenueCatLifecycleNotificationService lifecycleNotificationService;
    private final com.grun.calorietracker.service.StoreSubscriptionOwnershipService ownershipService;
    @Autowired(required = false) private PushDeliveryService pushDeliveryService;
    @Autowired(required = false) private UserConsentRepository userConsentRepository;

    @Override
    @Transactional
    public RevenueCatWebhookResponseDto processWebhook(String authorizationHeader, JsonNode payload) {
        validateAuthorization(authorizationHeader);
        return processPayload(payload);
    }

    @Override
    @Transactional
    public RevenueCatWebhookResponseDto retryStoredEvent(Long eventId) {
        SubscriptionProviderEventEntity storedEvent = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription provider event not found"));
        if (storedEvent.getStatus() != SubscriptionProviderEventStatus.FAILED) {
            return new RevenueCatWebhookResponseDto(true, true, storedEvent.getProviderEventId(), "IGNORED", "Only failed events can be retried.");
        }
        try {
            return processPayload(objectMapper.readTree(storedEvent.getRawPayload()));
        } catch (JsonProcessingException ex) {
            storedEvent.setProcessingError("Stored raw payload is not valid JSON.");
            storedEvent.setProcessedAt(LocalDateTime.now());
            eventRepository.save(storedEvent);
            return new RevenueCatWebhookResponseDto(true, false, storedEvent.getProviderEventId(), "FAILED", storedEvent.getProcessingError());
        }
    }

    private RevenueCatWebhookResponseDto processPayload(JsonNode payload) {
        RevenueCatWebhookEventDto webhook = toWebhook(payload);
        RevenueCatWebhookEventDto.Event event = requireEvent(webhook);
        String providerEventId = resolveProviderEventId(event);

        Optional<SubscriptionProviderEventEntity> existingEvent =
                eventRepository.findByProviderAndProviderEventId(PaymentProvider.REVENUECAT, providerEventId);
        if (existingEvent.isPresent() && existingEvent.get().getStatus() != SubscriptionProviderEventStatus.FAILED) {
            return new RevenueCatWebhookResponseDto(true, true, providerEventId, "IGNORED", "Duplicate event ignored.");
        }

        SubscriptionProviderEventEntity audit = existingEvent.orElseGet(() -> buildAuditEvent(event, providerEventId, payload));
        audit.setProcessingError(null);
        try {
            RevenueCatEventType eventType = RevenueCatEventType.from(event.getType());
            if (eventType == RevenueCatEventType.TRANSFER) {
                Optional<Long> destinationUserId = resolveUniqueBackendUserId(event.getTransferredTo());
                destinationUserId.flatMap(userRepository::findById).ifPresent(audit::setUser);
                destinationUserId.map(id -> "user:" + id).ifPresent(audit::setProviderAppUserId);
                Optional<Long> sourceUserId = resolveUniqueBackendUserId(event.getTransferredFrom());
                if (sourceUserId.isPresent() && destinationUserId.isPresent()
                        && !sourceUserId.get().equals(destinationUserId.get())) {
                    throw new IllegalArgumentException("SUBSCRIPTION_OWNERSHIP_CONFLICT: transfer between different GRUN accounts requires review; no entitlement was changed.");
                }
                audit.setStatus(SubscriptionProviderEventStatus.IGNORED);
                audit.setProcessedAt(LocalDateTime.now());
                eventRepository.save(audit);
                return new RevenueCatWebhookResponseDto(true, false, providerEventId, "IGNORED",
                        "RevenueCat transfer event recorded; entitlement changes are applied by lifecycle purchase events.");
            }
            Optional<UserEntity> user = resolveUser(event);
            if (user.isEmpty()) {
                audit.setStatus(SubscriptionProviderEventStatus.FAILED);
                audit.setProcessingError("RevenueCat app_user_id does not match a known user.");
                audit.setProcessedAt(LocalDateTime.now());
                SubscriptionProviderEventEntity savedAudit = eventRepository.save(audit);
                notifyAdminsAboutFailedProviderEvent(savedAudit);
                return new RevenueCatWebhookResponseDto(true, false, providerEventId, "FAILED", audit.getProcessingError());
            }
            audit.setUser(user.get());
            assertRevenueCatCustomerBinding(user.get(), event);
            if (resolvePlan(event) != null && resolveAddonQuota(event.getProductId()) == null) {
                ownershipService.assertOwner(user.get().getId(), event);
            }
            SubscriptionEntity previousSubscription = subscriptionRepository.findByUser(user.get()).orElse(null);
            captureRefundEvidence(audit, user.get(), previousSubscription, eventType);
            SubscriptionPlan previousPlan = previousSubscription == null ? SubscriptionPlan.FREE : previousSubscription.getPlanType();
            SubscriptionStatus previousStatus = previousSubscription == null ? null : previousSubscription.getStatus();
            SubscriptionProviderEventCommand command = toCommand(event, providerEventId);
            if (command == null) {
                RevenueCatEventType providerType = RevenueCatEventType.from(event.getType());
                if (isNotificationOnlyLifecycleEvent(providerType)) {
                    SubscriptionPlan notificationPlan = providerType == RevenueCatEventType.PRODUCT_CHANGE
                            ? resolvePlanForProduct(event.getNewProductId()) : previousPlan;
                    if (notificationPlan == null || notificationPlan == SubscriptionPlan.FREE) {
                        throw new IllegalArgumentException("RevenueCat lifecycle notification could not resolve a paid plan.");
                    }
                    if (providerType == RevenueCatEventType.PRODUCT_CHANGE) {
                        subscriptionService.recordScheduledChange(user.get().getId(), event);
                    }
                    lifecycleNotificationService.enqueue(user.get(), providerType, providerEventId,
                            notificationPlan,
                            toLocalDate(firstNonNull(event.getExpirationAtMs(), event.getEventTimestampMs())),
                            null, null, false);
                    audit.setStatus(SubscriptionProviderEventStatus.PROCESSED);
                    audit.setProcessedAt(LocalDateTime.now());
                    eventRepository.save(audit);
                    return new RevenueCatWebhookResponseDto(true, false, providerEventId, "PROCESSED",
                            "RevenueCat lifecycle event recorded without changing current entitlement state.");
                }
                audit.setStatus(SubscriptionProviderEventStatus.IGNORED);
                audit.setProcessedAt(LocalDateTime.now());
                eventRepository.save(audit);
                return new RevenueCatWebhookResponseDto(true, false, providerEventId, "IGNORED", "Event type does not change backend entitlement state.");
            }
            SubscriptionDto appliedSubscription = subscriptionService.applyProviderEvent(user.get().getId(), command);
            if (isPromoAttributionEvent(event)) {
                promoProviderRedemptionService.recordVerifiedPurchase(new PromoProviderRedemptionCommand(
                        user.get().getId(), providerEventId, event.getProductId(), event.getPresentedOfferingId(),
                        event.getOfferCode(), event.getStore(), toMinorUnits(event), event.getCurrency(), previousPlan, previousStatus));
            }
            enqueueLifecycleNotification(user.get(), event, providerEventId, command, appliedSubscription, previousPlan);
            audit.setStatus(SubscriptionProviderEventStatus.PROCESSED);
            audit.setProcessedAt(LocalDateTime.now());
            eventRepository.save(audit);
            return new RevenueCatWebhookResponseDto(true, false, providerEventId, "PROCESSED", "RevenueCat event processed.");
        } catch (RuntimeException ex) {
            audit.setStatus(SubscriptionProviderEventStatus.FAILED);
            audit.setProcessingError(limit(ex.getMessage()));
            audit.setProcessedAt(LocalDateTime.now());
            SubscriptionProviderEventEntity savedAudit = eventRepository.save(audit);
            notifyAdminsAboutFailedProviderEvent(savedAudit);
            return new RevenueCatWebhookResponseDto(true, false, providerEventId, "FAILED", audit.getProcessingError());
        }
    }

    private boolean isPromoAttributionEvent(RevenueCatWebhookEventDto.Event event) {
        RevenueCatEventType type = RevenueCatEventType.from(event.getType());
        return type == RevenueCatEventType.INITIAL_PURCHASE || type == RevenueCatEventType.NON_RENEWING_PURCHASE;
    }

    private Long toMinorUnits(RevenueCatWebhookEventDto.Event event) {
        if (event.getPriceInPurchasedCurrency() == null) return null;
        return event.getPriceInPurchasedCurrency().movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
    private void notifyAdminsAboutFailedProviderEvent(SubscriptionProviderEventEntity event) {
        List<UserEntity> admins = userRepository.findByRoleIn(List.of(UserRole.OWNER, UserRole.ADMIN_FINANCE));
        if (admins == null || admins.isEmpty()) {
            return;
        }
        String eventId = event.getId() == null ? event.getProviderEventId() : String.valueOf(event.getId());
        String message = "RevenueCat provider event failed. eventId=" + eventId
                + ", providerEventId=" + event.getProviderEventId()
                + ", type=" + event.getEventType()
                + ", reason=" + event.getProcessingError();
        LocalDateTime now = LocalDateTime.now();
        List<NotificationEntity> notifications = admins.stream().map(admin -> {
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(admin);
            notification.setType("subscription_provider_alert");
            notification.setSeverity("CRITICAL");
            notification.setSource("REVENUECAT");
            notification.setTargetType("SUBSCRIPTION_PROVIDER_EVENT");
            notification.setTargetId(eventId);
            notification.setTargetRoute("subscriptionEvents");
            notification.setMessage(message);
            notification.setIsRead(false);
            notification.setCreatedAt(now);
            return notification;
        }).toList();
        List<NotificationEntity> saved = notificationRepository.saveAll(notifications);
        if (pushDeliveryService != null) saved.forEach(pushDeliveryService::deliver);
    }
    private void validateAuthorization(String authorizationHeader) {
        String expected = properties.getWebhookAuthorization();
        if (expected == null || expected.isBlank()) {
            throw new AccessDeniedException("RevenueCat webhook authorization is not configured.");
        }
        if (authorizationHeader == null || !constantTimeEquals(expected, authorizationHeader)) {
            throw new AccessDeniedException("Invalid RevenueCat webhook authorization header.");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private RevenueCatWebhookEventDto toWebhook(JsonNode payload) {
        try {
            return objectMapper.treeToValue(payload, RevenueCatWebhookEventDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid RevenueCat webhook payload.");
        }
    }

    private RevenueCatWebhookEventDto.Event requireEvent(RevenueCatWebhookEventDto webhook) {
        if (webhook == null || webhook.getEvent() == null) {
            throw new IllegalArgumentException("RevenueCat webhook event is required.");
        }
        return webhook.getEvent();
    }

    private SubscriptionProviderEventEntity buildAuditEvent(RevenueCatWebhookEventDto.Event event, String providerEventId, JsonNode payload) {
        SubscriptionProviderEventEntity audit = new SubscriptionProviderEventEntity();
        audit.setProvider(PaymentProvider.REVENUECAT);
        audit.setProviderEventId(providerEventId);
        audit.setProviderAppUserId(firstNonBlank(event.getAppUserId(), event.getOriginalAppUserId()));
        audit.setEventType(event.getType());
        audit.setProductId(event.getProductId());
        audit.setNewProductId(event.getNewProductId());
        audit.setStore(event.getStore());
        audit.setPresentedOfferingId(event.getPresentedOfferingId());
        audit.setStoreOfferCode(event.getOfferCode());
        audit.setPurchaseCurrency(event.getCurrency() == null ? null : event.getCurrency().trim().toUpperCase(Locale.ROOT));
        audit.setPriceAmountMinor(toMinorUnits(event));
        audit.setEntitlementIds(String.join(",", entitlementIds(event)));
        audit.setTransactionId(event.getTransactionId());
        audit.setOriginalTransactionId(event.getOriginalTransactionId());
        audit.setPeriodType(event.getPeriodType());
        audit.setEnvironment(event.getEnvironment());
        audit.setCancelReason(event.getCancelReason());
        audit.setExpirationReason(event.getExpirationReason());
        audit.setProviderEventAt(toInstant(event.getEventTimestampMs()));
        audit.setPurchasedAt(toInstant(event.getPurchasedAtMs()));
        audit.setExpirationAt(toInstant(event.getExpirationAtMs()));
        audit.setStatus(SubscriptionProviderEventStatus.FAILED);
        audit.setRawPayload(payload.toString());
        audit.setReceivedAt(LocalDateTime.now());
        return audit;
    }

    private Optional<UserEntity> resolveUser(RevenueCatWebhookEventDto.Event event) {
        Long currentUserId = parseBackendUserId(event.getAppUserId());
        if (currentUserId != null) {
            return userRepository.findById(currentUserId);
        }

        List<String> fallbackIds = Stream.concat(
                        Stream.of(event.getOriginalAppUserId()),
                        event.getAliases() == null ? Stream.empty() : event.getAliases().stream())
                .toList();
        return resolveUniqueBackendUserId(fallbackIds).flatMap(userRepository::findById);
    }

    private void captureRefundEvidence(SubscriptionProviderEventEntity audit,
                                       UserEntity user,
                                       SubscriptionEntity subscription,
                                       RevenueCatEventType eventType) {
        boolean isRefundEvent = eventType == RevenueCatEventType.REFUND_REVERSED
                || (eventType == RevenueCatEventType.CANCELLATION
                && REFUND_CANCEL_REASON.equalsIgnoreCase(audit.getCancelReason()));
        if (!isRefundEvent) {
            return;
        }

        if (userConsentRepository != null) {
            Optional<UserConsentEntity> latestConsent = userConsentRepository
                    .findByUserAndConsentTypeOrderByCreatedAtDesc(user, LegalConsentType.APPLE_REFUND_CONSUMPTION_SHARING)
                    .stream()
                    .findFirst();
            latestConsent.ifPresent(consent -> {
                audit.setAppleRefundConsentStatus(consent.getStatus() == null ? null : consent.getStatus().name());
                audit.setAppleRefundConsentVersion(consent.getVersion());
            });
        }

        boolean delivered = subscription != null
                && subscription.getPlanType() != null
                && subscription.getPlanType() != SubscriptionPlan.FREE;
        boolean active = delivered
                && (subscription.getStatus() == SubscriptionStatus.ACTIVE
                || subscription.getStatus() == SubscriptionStatus.TRIALING)
                && (subscription.getEndDate() == null || !subscription.getEndDate().isBefore(LocalDate.now()));
        audit.setEntitlementDeliveredSnapshot(delivered);
        audit.setEntitlementActiveSnapshot(active);
        if (subscription != null) {
            audit.setPlanQuotaSnapshot(subscription.getAiMonthlyQuota());
            audit.setPlanUsedSnapshot(subscription.getAiPlanUsedThisPeriod());
            audit.setAddonQuotaSnapshot(subscription.getAiAddonQuota());
            audit.setAddonUsedSnapshot(subscription.getAiAddonUsed());
        }
    }

    private Optional<Long> resolveUniqueBackendUserId(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        List<Long> userIds = values.stream()
                .map(this::parseBackendUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.size() > 1) {
            throw new IllegalArgumentException("RevenueCat customer references multiple backend users.");
        }
        return userIds.stream().findFirst();
    }

    private Long parseBackendUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = APP_USER_ID.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    private void assertRevenueCatCustomerBinding(UserEntity user, RevenueCatWebhookEventDto.Event event) {
        String providerCustomerId = firstNonBlank(event.getAppUserId(), event.getOriginalAppUserId());
        subscriptionRepository.findByUser(user)
                .filter(subscription -> subscription.getProvider() == PaymentProvider.REVENUECAT)
                .map(SubscriptionEntity::getProviderCustomerId)
                .filter(existingCustomerId -> existingCustomerId != null && !existingCustomerId.isBlank())
                .filter(existingCustomerId -> !existingCustomerId.equals(providerCustomerId))
                .ifPresent(existingCustomerId -> {
                    throw new IllegalArgumentException("RevenueCat customer id is already bound to this user.");
                });
    }

    private SubscriptionProviderEventCommand toCommand(RevenueCatWebhookEventDto.Event event, String providerEventId) {
        RevenueCatEventType type = RevenueCatEventType.from(event.getType());
        Integer addonQuota = resolveAddonQuota(event.getProductId());
        SubscriptionProviderEventCommand command = baseCommand(event, providerEventId);

        switch (type) {
            case INITIAL_PURCHASE, RENEWAL, UNCANCELLATION -> {
                SubscriptionPlan plan = resolvePlan(event);
                if (plan == null) {
                    failStrictProductMapping(event, "Subscription product or entitlement is not mapped to PLUS or PRO.");
                    return null;
                }
                command.setPlanType(plan);
                command.setStatus("TRIAL".equalsIgnoreCase(event.getPeriodType()) ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE);
                command.setStartDate(toLocalDate(firstNonNull(event.getPurchasedAtMs(), event.getEventTimestampMs())));
                command.setEndDate(toLocalDate(event.getExpirationAtMs()));
                command.setAutoRenew(true);
                if (type == RevenueCatEventType.INITIAL_PURCHASE || type == RevenueCatEventType.RENEWAL) {
                    command.setGrantPlanCreditAllocation(true);
                    command.setCreditAllocationKey(resolveCreditAllocationKey(event, plan));
                }
                return command;
            }
            case CANCELLATION -> {
                command.setRefund(REFUND_CANCEL_REASON.equalsIgnoreCase(event.getCancelReason()));
                if (Boolean.TRUE.equals(command.getRefund()) && addonQuota != null) {
                    command.setAiAddonQuotaAmount(addonQuota);
                } else {
                    command.setStatus(Boolean.TRUE.equals(command.getRefund()) ? SubscriptionStatus.REFUNDED : SubscriptionStatus.CANCELED);
                    command.setEndDate(toLocalDate(firstNonNull(event.getExpirationAtMs(), event.getEventTimestampMs())));
                    command.setAutoRenew(false);
                }
                return command;
            }
            case EXPIRATION -> {
                command.setStatus(SubscriptionStatus.EXPIRED);
                command.setEndDate(toLocalDate(firstNonNull(event.getExpirationAtMs(), event.getEventTimestampMs())));
                command.setAutoRenew(false);
                return command;
            }
            case REFUND_REVERSED -> {
                SubscriptionPlan plan = resolvePlan(event);
                if (plan == null) {
                    failStrictProductMapping(event, "Refund-reversed product or entitlement is not mapped to PLUS or PRO.");
                    return null;
                }
                command.setPlanType(plan);
                command.setStatus(SubscriptionStatus.ACTIVE);
                command.setStartDate(toLocalDate(firstNonNull(event.getPurchasedAtMs(), event.getEventTimestampMs())));
                command.setEndDate(toLocalDate(event.getExpirationAtMs()));
                command.setAutoRenew(true);
                command.setGrantPlanCreditAllocation(false);
                return command;
            }
            case BILLING_ISSUE -> {
                command.setStatus(SubscriptionStatus.PAST_DUE);
                command.setEndDate(toLocalDate(event.getExpirationAtMs()));
                command.setAutoRenew(true);
                return command;
            }
            case NON_RENEWING_PURCHASE -> {
                if (addonQuota == null) {
                    failStrictProductMapping(event, "AI add-on product is not mapped to a quota amount.");
                    return null;
                }
                command.setAiAddonQuotaAmount(addonQuota);
                command.setAiAddonValidityDays(resolveAddonValidityDays(event.getProductId()));
                return command;
            }
            default -> {
                return null;
            }
        }
    }

    private void failStrictProductMapping(RevenueCatWebhookEventDto.Event event, String message) {
        if (properties.isStrictProductMapping()) {
            throw new IllegalArgumentException(message + " productId=" + event.getProductId() + ", entitlements=" + entitlementIds(event));
        }
    }

    private SubscriptionProviderEventCommand baseCommand(RevenueCatWebhookEventDto.Event event, String providerEventId) {
        SubscriptionProviderEventCommand command = new SubscriptionProviderEventCommand();
        command.setProvider(PaymentProvider.REVENUECAT);
        command.setProviderCustomerId(firstNonBlank(event.getAppUserId(), event.getOriginalAppUserId()));
        command.setProviderProductId(event.getProductId());
        if (event.getProductId() != null && event.getProductId().endsWith("_yearly")) {
            command.setBillingPeriod(com.grun.calorietracker.enums.BillingPeriod.YEARLY);
        } else if (event.getProductId() != null && event.getProductId().endsWith("_monthly")) {
            command.setBillingPeriod(com.grun.calorietracker.enums.BillingPeriod.MONTHLY);
        }
        command.setProviderEventId(providerEventId);
        command.setProviderSubscriptionId(firstNonBlank(event.getOriginalTransactionId(), event.getTransactionId()));
        command.setProviderTransactionId(event.getTransactionId());
        command.setProviderOriginalTransactionId(event.getOriginalTransactionId());
        command.setEventType(RevenueCatEventType.from(event.getType()));
        command.setProviderEventAt(toInstant(event.getEventTimestampMs()));
        command.setPurchasedAt(toInstant(event.getPurchasedAtMs()));
        command.setExpirationAt(toInstant(event.getExpirationAtMs()));
        return command;
    }

    private boolean isNotificationOnlyLifecycleEvent(RevenueCatEventType type) {
        return type == RevenueCatEventType.PRODUCT_CHANGE || type == RevenueCatEventType.SUBSCRIPTION_PAUSED;
    }

    private void enqueueLifecycleNotification(UserEntity user, RevenueCatWebhookEventDto.Event event,
            String providerEventId, SubscriptionProviderEventCommand command,
            SubscriptionDto appliedSubscription, SubscriptionPlan previousPlan) {
        SubscriptionPlan notificationPlan = command.getPlanType();
        if ((notificationPlan == null || notificationPlan == SubscriptionPlan.FREE) && appliedSubscription != null) {
            notificationPlan = appliedSubscription.getPlanType();
        }
        if (notificationPlan == null || notificationPlan == SubscriptionPlan.FREE) notificationPlan = previousPlan;

        LocalDate relevantDate = command.getEndDate();
        if (relevantDate == null && appliedSubscription != null) relevantDate = appliedSubscription.getEndDate();
        if (relevantDate == null) {
            relevantDate = toLocalDate(firstNonNull(event.getExpirationAtMs(), event.getEventTimestampMs()));
        }
        LocalDate addOnValidUntil = appliedSubscription == null ? null : appliedSubscription.getAiAddonQuotaExpiresAt();
        lifecycleNotificationService.enqueue(user, RevenueCatEventType.from(event.getType()), providerEventId,
                notificationPlan, relevantDate, command.getAiAddonQuotaAmount(), addOnValidUntil,
                Boolean.TRUE.equals(command.getRefund()) && command.getAiAddonQuotaAmount() == null);
    }

    private String resolveCreditAllocationKey(RevenueCatWebhookEventDto.Event event, SubscriptionPlan plan) {
        if (event.getPurchasedAtMs() == null) {
            throw new IllegalArgumentException("RevenueCat plan credit allocation requires purchased_at_ms.");
        }
        String transaction = firstNonBlank(event.getTransactionId(), event.getOriginalTransactionId());
        if (transaction == null) {
            throw new IllegalArgumentException("RevenueCat plan credit allocation requires a transaction id.");
        }
        String canonical = String.join("|",
                normalize(event.getEnvironment()), normalize(event.getStore()), plan.name(),
                normalize(event.getProductId()), normalize(transaction), String.valueOf(event.getPurchasedAtMs()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private SubscriptionPlan resolvePlan(RevenueCatWebhookEventDto.Event event) {
        List<String> entitlements = entitlementIds(event);
        if (matchesAny(entitlements, properties.getEntitlements().getPro()) || matchesProduct(event.getProductId(), properties.getProducts().getPro(), "pro")) {
            return SubscriptionPlan.PRO;
        }
        if (matchesAny(entitlements, properties.getEntitlements().getPlus()) || matchesProduct(event.getProductId(), properties.getProducts().getPlus(), "plus")) {
            return SubscriptionPlan.PLUS;
        }
        return null;
    }

    private SubscriptionPlan resolvePlanForProduct(String productId) {
        if (matchesProduct(productId, properties.getProducts().getPro(), "pro")) return SubscriptionPlan.PRO;
        if (matchesProduct(productId, properties.getProducts().getPlus(), "plus")) return SubscriptionPlan.PLUS;
        return null;
    }

    private Integer resolveAddonQuota(String productId) {
        if (productId == null) {
            return null;
        }
        Integer configured = properties.getProducts().getAiAddonQuotas().get(productId);
        if (configured != null) {
            return configured;
        }
        if (properties.isStrictProductMapping()) {
            return null;
        }
        String lower = productId.toLowerCase(Locale.ROOT);
        if (!lower.contains("ai") || !lower.contains("credit")) {
            return null;
        }
        Matcher matcher = FIRST_NUMBER.matcher(lower);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private int resolveAddonValidityDays(String productId) {
        if (productId != null && properties.getProducts().getAiAddonValidityDays().containsKey(productId)) {
            return properties.getProducts().getAiAddonValidityDays().get(productId);
        }
        return properties.getProducts().getDefaultAiAddonValidityDays();
    }

    private boolean matchesProduct(String productId, List<String> configuredProducts, String fallbackToken) {
        if (productId == null || productId.isBlank()) {
            return false;
        }
        if (configuredProducts.contains(productId)) {
            return true;
        }
        if (properties.isStrictProductMapping()) {
            return false;
        }
        return hasProductToken(productId, fallbackToken);
    }

    private boolean hasProductToken(String productId, String token) {
        String[] parts = productId.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String part : parts) {
            if (part.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAny(List<String> incoming, List<String> configured) {
        return incoming.stream().anyMatch(value -> configured.stream().anyMatch(config -> config.equalsIgnoreCase(value)));
    }

    private List<String> entitlementIds(RevenueCatWebhookEventDto.Event event) {
        List<String> values = new ArrayList<>();
        if (event.getEntitlementIds() != null) {
            values.addAll(event.getEntitlementIds());
        }
        if (event.getEntitlementId() != null && !event.getEntitlementId().isBlank()) {
            values.add(event.getEntitlementId());
        }
        return values;
    }

    private String resolveProviderEventId(RevenueCatWebhookEventDto.Event event) {
        String id = firstNonBlank(event.getId(), event.getTransactionId());
        if (id != null) {
            return id + ":" + firstNonBlank(event.getType(), "UNKNOWN") + ":" + firstNonNull(event.getEventTimestampMs(), 0L);
        }
        return firstNonBlank(event.getType(), "UNKNOWN") + ":" + firstNonBlank(event.getProductId(), "unknown-product") + ":" + firstNonNull(event.getEventTimestampMs(), System.currentTimeMillis());
    }

    private LocalDate toLocalDate(Long epochMs) {
        if (epochMs == null) {
            return null;
        }
        return Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private Instant toInstant(Long epochMs) {
        return epochMs == null ? null : Instant.ofEpochMilli(epochMs);
    }

    private Long firstNonNull(Long first, Long second) {
        return first == null ? second : first;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String limit(String message) {
        if (message == null) {
            return "Unknown processing error.";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}

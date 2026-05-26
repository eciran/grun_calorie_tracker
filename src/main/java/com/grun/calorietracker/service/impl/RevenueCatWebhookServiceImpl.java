package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.RevenueCatWebhookEventDto;
import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventCommand;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.RevenueCatEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.RevenueCatWebhookService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RevenueCatWebhookServiceImpl implements RevenueCatWebhookService {

    private static final String REFUND_CANCEL_REASON = "CUSTOMER_SUPPORT";
    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");

    private final RevenueCatProperties properties;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionProviderEventRepository eventRepository;
    private final SubscriptionService subscriptionService;

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
            Optional<UserEntity> user = resolveUser(event);
            if (user.isEmpty()) {
                audit.setStatus(SubscriptionProviderEventStatus.FAILED);
                audit.setProcessingError("RevenueCat app_user_id does not match a known user.");
                audit.setProcessedAt(LocalDateTime.now());
                eventRepository.save(audit);
                return new RevenueCatWebhookResponseDto(true, false, providerEventId, "FAILED", audit.getProcessingError());
            }
            audit.setUser(user.get());
            SubscriptionProviderEventCommand command = toCommand(event, providerEventId);
            if (command == null) {
                audit.setStatus(SubscriptionProviderEventStatus.IGNORED);
                audit.setProcessedAt(LocalDateTime.now());
                eventRepository.save(audit);
                return new RevenueCatWebhookResponseDto(true, false, providerEventId, "IGNORED", "Event type does not change backend entitlement state.");
            }
            subscriptionService.applyProviderEvent(user.get().getId(), command);
            audit.setStatus(SubscriptionProviderEventStatus.PROCESSED);
            audit.setProcessedAt(LocalDateTime.now());
            eventRepository.save(audit);
            return new RevenueCatWebhookResponseDto(true, false, providerEventId, "PROCESSED", "RevenueCat event processed.");
        } catch (RuntimeException ex) {
            audit.setStatus(SubscriptionProviderEventStatus.FAILED);
            audit.setProcessingError(limit(ex.getMessage()));
            audit.setProcessedAt(LocalDateTime.now());
            eventRepository.save(audit);
            return new RevenueCatWebhookResponseDto(true, false, providerEventId, "FAILED", audit.getProcessingError());
        }
    }

    private void validateAuthorization(String authorizationHeader) {
        String expected = properties.getWebhookAuthorization();
        if (expected != null && !expected.isBlank() && !expected.equals(authorizationHeader)) {
            throw new AccessDeniedException("Invalid RevenueCat webhook authorization header.");
        }
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
        audit.setEntitlementIds(String.join(",", entitlementIds(event)));
        audit.setTransactionId(event.getTransactionId());
        audit.setOriginalTransactionId(event.getOriginalTransactionId());
        audit.setStatus(SubscriptionProviderEventStatus.FAILED);
        audit.setRawPayload(payload.toString());
        audit.setReceivedAt(LocalDateTime.now());
        return audit;
    }

    private Optional<UserEntity> resolveUser(RevenueCatWebhookEventDto.Event event) {
        String appUserId = firstNonBlank(event.getAppUserId(), event.getOriginalAppUserId());
        if (appUserId == null) {
            return Optional.empty();
        }
        String normalized = appUserId.trim();
        if (normalized.startsWith("user:")) {
            normalized = normalized.substring("user:".length());
        }
        try {
            return userRepository.findById(Long.parseLong(normalized));
        } catch (NumberFormatException ignored) {
            return userRepository.findByEmail(appUserId.trim());
        }
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
        command.setProviderEventId(providerEventId);
        command.setProviderSubscriptionId(firstNonBlank(event.getOriginalTransactionId(), event.getTransactionId()));
        command.setProviderTransactionId(event.getTransactionId());
        command.setProviderOriginalTransactionId(event.getOriginalTransactionId());
        return command;
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

    private Integer resolveAddonQuota(String productId) {
        if (productId == null) {
            return null;
        }
        Integer configured = properties.getProducts().getAiAddonQuotas().get(productId);
        if (configured != null) {
            return configured;
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

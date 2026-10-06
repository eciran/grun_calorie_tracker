package com.grun.calorietracker.service;

import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.SubscriptionVerificationEntity;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.SubscriptionVerificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Slf4j
@Service
public class SubscriptionPurchaseVerificationService {
    public static final String STORE_OFFER_PRODUCT = "__store_offer__";
    public enum Status { PREPARED, PENDING, VERIFIED, RECONCILED, SCHEDULED, CANCELED, REQUIRES_REVIEW, PROVIDER_UNAVAILABLE }
    public record Result(Status status, SubscriptionDto subscription, int retryAfterSeconds,
                         String reasonCode, String resolvedProductId, String providerTransactionId) {
        public Result(Status status, SubscriptionDto subscription, int retryAfterSeconds) {
            this(status, subscription, retryAfterSeconds, null, null, null);
        }
    }
    public record PendingAttempt(String productId, String attemptId, Instant createdAt) { }
    private record Claim(Long userId, String email, String product, int attempt, Instant lease,
                         Instant createdAt, String expectedTransactionId) { }
    private record Outcome(Status status, String allocationReference, String reasonCode,
                           String productId, String transactionId) {
        Outcome(Status status, String reference) {
            this(status, reference, status == Status.PENDING ? "AWAITING_PROVIDER_EVIDENCE" : null, null, null);
        }
    }

    private final UserRepository users;
    private final SubscriptionVerificationRepository verifications;
    private final RevenueCatPurchaseEvidenceClient provider;
    private final RevenueCatProperties properties;
    private final SubscriptionService subscriptions;
    private final RevenueCatWebhookService webhooks;
    private final com.grun.calorietracker.repository.SubscriptionProviderEventRepository events;
    private final TransactionTemplate transaction;

    public SubscriptionPurchaseVerificationService(UserRepository users, SubscriptionVerificationRepository verifications,
            RevenueCatPurchaseEvidenceClient provider, RevenueCatProperties properties, SubscriptionService subscriptions,
            RevenueCatWebhookService webhooks, PlatformTransactionManager manager,
            com.grun.calorietracker.repository.SubscriptionProviderEventRepository events) {
        this.users = users;
        this.verifications = verifications;
        this.provider = provider;
        this.properties = properties;
        this.subscriptions = subscriptions;
        this.webhooks = webhooks;
        this.events = events;
        transaction = new TransactionTemplate(manager);
    }

    public Result request(String email, String productId, String attemptId) {
        return request(email, productId, attemptId, null);
    }

    public Result request(String email, String productId, String attemptId, String transactionId) {
        return request(email, productId, attemptId, transactionId, false);
    }

    public Result prepare(String email, String productId, String attemptId) {
        return request(email, productId, attemptId, null, true);
    }

    public PendingAttempt pending(String email) {
        var user = users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        var state = verifications.findById(user.getId()).orElse(null);
        if (state == null || java.util.List.of("CANCELED", "VERIFIED", "RECONCILED", "SCHEDULED").contains(state.getStatus())) return null;
        return new PendingAttempt(state.getProductId(), state.getAttemptId(), state.getCreatedAt());
    }

    private Result request(String email, String productId, String attemptId, String transactionId, boolean prepare) {
        if (!isAddon(productId) && !STORE_OFFER_PRODUCT.equals(productId)) plan(productId);
        if (prepare) {
            var api = properties.getApi();
            if (!api.isEnabled() || api.getSecretKey() == null || api.getSecretKey().isBlank()
                    || api.getProjectId() == null || api.getProjectId().isBlank()
                    || !java.util.List.of("sandbox", "production").contains(api.getVerificationEnvironment()))
                throw new IllegalStateException("RevenueCat verification is not configured");
            var current = subscriptions.getCurrentSubscription(email);
            if (isAddon(productId) && (!Boolean.TRUE.equals(current.getActiveEntitlement())
                    || current.getPlanType() == SubscriptionPlan.FREE))
                throw new IllegalArgumentException("An active paid subscription is required for AI credits.");
        }
        var state = transaction.execute(tx -> {
            var user = users.findByEmailForUpdate(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
            var existing = verifications.findById(user.getId()).orElse(null);
            if (existing != null && !existing.getAttemptId().equals(attemptId)
                    && !Status.CANCELED.name().equals(existing.getStatus())
                    && !java.util.List.of("VERIFIED", "RECONCILED", "SCHEDULED").contains(existing.getStatus())
                    && (Status.PREPARED.name().equals(existing.getStatus()) || existing.getNextAttemptAt() != null
                        || Status.REQUIRES_REVIEW.name().equals(existing.getStatus()))) {
                throw new com.grun.calorietracker.exception.RequestConflictException("Another purchase is being verified.");
            }
            if (existing != null && existing.getAttemptId().equals(attemptId)) {
                if (!existing.getProductId().equals(productId)) throw new IllegalArgumentException("Purchase attempt product mismatch");
                if (transactionId != null && !transactionId.isBlank()) {
                    if (existing.getExpectedTransactionId() != null && !transactionId.equals(existing.getExpectedTransactionId()))
                        throw new IllegalArgumentException("Purchase transaction mismatch");
                    existing.setExpectedTransactionId(transactionId);
                }
                if (!prepare && Status.PREPARED.name().equals(existing.getStatus())) {
                    existing.setStatus(Status.PENDING.name());
                    existing.setNextAttemptAt(Instant.now());
                    existing.setUpdatedAt(Instant.now());
                    return verifications.saveAndFlush(existing);
                }
                if (Status.REQUIRES_REVIEW.name().equals(existing.getStatus())
                        && existing.getNextAttemptAt() == null
                        && existing.getUpdatedAt().isBefore(Instant.now().minusSeconds(60))) {
                    existing.setStatus(Status.PENDING.name());
                    existing.setAttempts(0);
                    existing.setRecoveryStartedAt(Instant.now());
                    existing.setNextAttemptAt(Instant.now());
                    existing.setReasonCode("RECHECK_REQUESTED");
                    existing.setUpdatedAt(Instant.now());
                    return verifications.saveAndFlush(existing);
                }
                if (transactionId != null) return verifications.saveAndFlush(existing);
                return existing;
            }
            var created = new SubscriptionVerificationEntity();
            created.setUserId(user.getId());
            created.setProductId(productId);
            created.setAttemptId(attemptId);
            created.setStatus(prepare ? Status.PREPARED.name() : Status.PENDING.name());
            created.setUpdatedAt(Instant.now());
            created.setCreatedAt(Instant.now());
            created.setRecoveryStartedAt(Instant.now());
            created.setExpectedTransactionId(transactionId);
            created.setReasonCode(prepare ? "CHECKOUT_PREPARED" : "AWAITING_PROVIDER_EVIDENCE");
            created.setNextAttemptAt(prepare ? null : Instant.now());
            return verifications.saveAndFlush(created);
        });
        SubscriptionDto current = subscriptions.getCurrentSubscription(email);
        Status status = Status.valueOf(state.getStatus());
        String resolvedProduct = state.getResolvedProductId() == null ? productId : state.getResolvedProductId();
        if (!isAddon(productId) && (status == Status.VERIFIED || status == Status.RECONCILED)
                && !matches(current, plan(resolvedProduct), resolvedProduct, state.getAllocationReference())) {
            transaction.executeWithoutResult(tx -> {
                if (users.findByEmailForUpdate(email).isEmpty()) return;
                var latest = verifications.findById(state.getUserId()).orElse(null);
                if (latest != null && latest.getAttemptId().equals(attemptId)
                        && latest.getNextAttemptAt() == null && latest.getLeaseUntil() == null) {
                    latest.setStatus(Status.PENDING.name());
                    latest.setAttempts(0);
                    latest.setNextAttemptAt(Instant.now());
                    latest.setRecoveryStartedAt(Instant.now());
                    latest.setReasonCode("ACTIVE_PERIOD_CHANGED");
                    verifications.saveAndFlush(latest);
                }
            });
            status = Status.PENDING;
        }
        if (status == Status.SCHEDULED && (!productId.equals(current.getScheduledProductId())
                || current.getScheduledChangeAt() == null || !current.getScheduledChangeAt().isAfter(Instant.now()))) {
            transaction.executeWithoutResult(tx -> {
                users.findByEmailForUpdate(email).orElseThrow();
                var latest = verifications.findById(state.getUserId()).orElseThrow();
                if (latest.getAttemptId().equals(attemptId) && Status.SCHEDULED.name().equals(latest.getStatus())) {
                    latest.setStatus(Status.PENDING.name());
                    latest.setAttempts(0);
                    latest.setRecoveryStartedAt(Instant.now());
                    latest.setNextAttemptAt(Instant.now());
                    latest.setReasonCode("SCHEDULED_CHANGE_RECHECK");
                    verifications.saveAndFlush(latest);
                }
            });
            status = Status.PENDING;
        }
        return new Result(status, current, 5, state.getReasonCode(), state.getResolvedProductId(), state.getProviderTransactionId());
    }

    public void cancelUnconfirmedAttempt(String email, String attemptId) {
        transaction.executeWithoutResult(tx -> {
            var user = users.findByEmailForUpdate(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
            var state = verifications.findById(user.getId()).orElse(null);
            if (state == null || !attemptId.equals(state.getAttemptId())) return;
            // This releases a canceled checkout attempt only; it never changes store ownership, access or credits.
            if (state.getExpectedTransactionId() != null || state.getProviderTransactionId() != null || state.getAllocationReference() != null
                    || java.util.List.of("VERIFIED", "RECONCILED", "SCHEDULED").contains(state.getStatus())) return;
            state.setStatus(Status.CANCELED.name());
            state.setReasonCode("CHECKOUT_CANCELED_BEFORE_CONFIRMATION");
            state.setLeaseUntil(null);
            state.setNextAttemptAt(null);
            state.setUpdatedAt(Instant.now());
            verifications.saveAndFlush(state);
        });
    }

    @Scheduled(fixedDelayString = "${grun.revenuecat.verification-delay-ms:5000}", initialDelay = 15000)
    public void verifyDuePurchases() {
        for (Long userId : verifications.findDue(Instant.now(), PageRequest.of(0, 2))) {
            try { verifyOne(userId); }
            catch (RuntimeException ex) {
                log.error("subscription_verification_worker_failed userId={} exception={}", userId, ex.getClass().getSimpleName());
            }
        }
    }

    public void verifyOne(Long userId) {
        Claim claim = transaction.execute(tx -> {
            var user = users.findByIdForUpdate(userId).orElse(null);
            if (user == null) return null;
            var state = verifications.findById(userId).orElse(null);
            Instant now = Instant.now();
            if (state == null || state.getNextAttemptAt() == null || state.getNextAttemptAt().isAfter(now)
                    || (state.getLeaseUntil() != null && state.getLeaseUntil().isAfter(now))) return null;
            if (Status.SCHEDULED.name().equals(state.getStatus())) {
                state.setRecoveryStartedAt(now);
                state.setAttempts(0);
            }
            Instant recoveryStart = state.getRecoveryStartedAt() == null ? state.getCreatedAt() : state.getRecoveryStartedAt();
            if (state.getAttempts() >= 32 || (recoveryStart != null
                    && recoveryStart.isBefore(now.minusSeconds(86400)))) {
                state.setStatus(Status.REQUIRES_REVIEW.name());
                state.setNextAttemptAt(null);
                state.setLeaseUntil(null);
                state.setUpdatedAt(now);
                state.setReasonCode("RETRY_WINDOW_EXHAUSTED");
                verifications.saveAndFlush(state);
                return null;
            }
            state.setAttempts(state.getAttempts() + 1);
            state.setLeaseUntil(now.plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
            verifications.saveAndFlush(state);
            return new Claim(userId, user.getEmail(), state.getProductId(), state.getAttempts(), state.getLeaseUntil(),
                    state.getCreatedAt(), state.getExpectedTransactionId());
        });
        if (claim == null) return;
        Outcome outcome;
        try { outcome = verify(claim); }
        catch (RuntimeException ex) {
            String reason = providerFailureReason(ex);
            log.warn("subscription_verification_provider_unavailable userId={} product={} reason={} exception={}",
                    userId, claim.product(), reason, ex.getClass().getSimpleName());
            outcome = new Outcome(Status.PROVIDER_UNAVAILABLE, null, reason, null, null);
        }
        Outcome result = outcome;
        transaction.executeWithoutResult(tx -> {
            if (users.findByIdForUpdate(userId).isEmpty()) return;
            var state = verifications.findById(userId).orElse(null);
            if (state == null || !claim.lease().equals(state.getLeaseUntil()) || !claim.product().equals(state.getProductId())) return;
            // A receipt arriving during provider lookup must be checked by a fresh claim.
            if (!java.util.Objects.equals(claim.expectedTransactionId(), state.getExpectedTransactionId())) {
                state.setLeaseUntil(null);
                state.setNextAttemptAt(Instant.now());
                state.setUpdatedAt(Instant.now());
                verifications.saveAndFlush(state);
                return;
            }
            boolean success = result.status() == Status.VERIFIED || result.status() == Status.RECONCILED || result.status() == Status.SCHEDULED;
            boolean terminal = success || result.status() == Status.REQUIRES_REVIEW || claim.attempt() >= 32;
            state.setStatus((claim.attempt() >= 8 && !success
                    ? Status.REQUIRES_REVIEW : result.status()).name());
            state.setAllocationReference(result.allocationReference());
            state.setReasonCode(result.reasonCode());
            state.setResolvedProductId(result.productId());
            state.setProviderTransactionId(result.transactionId());
            state.setUpdatedAt(Instant.now());
            state.setLeaseUntil(null);
            state.setNextAttemptAt(result.status() == Status.SCHEDULED
                    ? subscriptions.getCurrentSubscription(claim.email()).getScheduledChangeAt()
                    : terminal ? null : Instant.now().plusSeconds(claim.attempt() >= 8
                    ? 900 : Math.min(300, 5L << claim.attempt())));
            verifications.saveAndFlush(state);
            if (Status.REQUIRES_REVIEW.name().equals(state.getStatus())) {
                log.error("subscription_verification_requires_review userId={} attempts={}", userId, claim.attempt());
            }
        });
    }

    private Outcome verify(Claim claim) {
        if (isAddon(claim.product())) return verifyAddon(claim);
        SubscriptionDto current = subscriptions.getCurrentSubscription(claim.email());
        if (claim.product().equals(current.getScheduledProductId()) && current.getScheduledChangeAt() != null
                && current.getScheduledChangeAt().isAfter(Instant.now()) && current.getProviderProductId() != null) {
            var active = provider.activePurchases(claim.userId(), current.getProviderProductId());
            if (active.size() == 1 && !active.get(0).ownershipConflict()) {
                var purchase = active.get(0);
                String reference = SubscriptionAllocationIdentity.of(purchase.environment(), purchase.store(),
                        plan(purchase.productId()), purchase.productId(), purchase.transactionId(), purchase.purchasedAt().toEpochMilli());
                if (matches(current, plan(purchase.productId()), purchase.productId(), reference)) {
                    return new Outcome(Status.SCHEDULED, reference);
                }
            }
            return new Outcome(Status.PENDING, null);
        }
        var evidence = provider.activePurchases(claim.userId(), claim.product());
        boolean reconciled = false;
        if (evidence.isEmpty()) {
            // Reconcile an interrupted plan selection against server-fetched active purchases.
            var active = provider.activePurchases(claim.userId()).stream()
                    .filter(item -> properties.getProducts().getPro().contains(item.productId())
                            || properties.getProducts().getPlus().contains(item.productId())).toList();
            if (!active.isEmpty()) {
                evidence = active;
                reconciled = true;
            }
        }
        if (evidence.isEmpty()) return new Outcome(Status.PENDING, null);
        if (evidence.size() != 1) return new Outcome(Status.REQUIRES_REVIEW, null, "AMBIGUOUS_ACTIVE_PURCHASES", null, null);
        if (evidence.get(0).ownershipConflict()) return new Outcome(Status.REQUIRES_REVIEW, null, "OWNERSHIP_CONFLICT", null, null);
        var purchase = evidence.get(0);
        if (STORE_OFFER_PRODUCT.equals(claim.product()) && (claim.createdAt() == null
                || purchase.purchasedAt().isBefore(claim.createdAt())))
            return new Outcome(Status.PENDING, null, "AWAITING_OFFER_TRANSACTION", null, null);
        SubscriptionPlan plan = plan(purchase.productId());
        String reference = SubscriptionAllocationIdentity.of(purchase.environment(), purchase.store(), plan,
                purchase.productId(), purchase.transactionId(), purchase.purchasedAt().toEpochMilli());
        if (reconciled || !matches(current, plan, purchase.productId(), reference)) {
            var payloads = provider.recoverablePurchaseEvents(claim.userId(), purchase);
            if (payloads.isEmpty()) return new Outcome(Status.PENDING, null, "PURCHASE_EVENT_NOT_RECOVERABLE", purchase.productId(), purchase.transactionId());
            // Server-fetched event only: reuse ownership, transaction rollback and idempotent credit allocation.
            for (var payload : payloads) {
                var response = webhooks.processWebhook(properties.getWebhookAuthorization(), payload);
                if (response != null && "REQUIRES_REVIEW".equals(response.getStatus())) {
                    return new Outcome(Status.REQUIRES_REVIEW, null, "OWNERSHIP_CONFLICT", purchase.productId(), purchase.transactionId());
                }
                if (response != null && "FAILED".equals(response.getStatus())) {
                    return new Outcome(Status.PENDING, null, "WEBHOOK_PROCESSING_PENDING", purchase.productId(), purchase.transactionId());
                }
            }
            current = subscriptions.getCurrentSubscription(claim.email());
        }
        boolean verified = matches(current, plan, purchase.productId(), reference);
        if (verified && claim.product().equals(current.getScheduledProductId())
                && current.getScheduledChangeAt() != null && current.getScheduledChangeAt().isAfter(Instant.now())) {
            return new Outcome(Status.SCHEDULED, reference, "PLAN_CHANGE_SCHEDULED", purchase.productId(), purchase.transactionId());
        }
        return new Outcome(verified ? (reconciled ? Status.RECONCILED : Status.VERIFIED) : Status.PENDING, reference,
                verified ? (reconciled ? "ACTIVE_PRODUCT_RECONCILED" : "PURCHASE_VERIFIED") : "ALLOCATION_NOT_MATCHED",
                purchase.productId(), purchase.transactionId());
    }

    private String providerFailureReason(RuntimeException exception) {
        if (exception instanceof org.springframework.web.client.RestClientResponseException http) {
            return "PROVIDER_HTTP_" + http.getStatusCode().value();
        }
        if (exception instanceof org.springframework.web.client.ResourceAccessException) return "PROVIDER_NETWORK_ERROR";
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("pagination")) return "PROVIDER_PAGINATION_ERROR";
        if (message.contains("not configured") || message.contains("verification environment")) return "PROVIDER_CONFIGURATION_ERROR";
        if (message.contains("Latest provider transaction")) return "PROVIDER_TRANSACTION_NOT_AVAILABLE";
        return "VERIFICATION_PROCESSING_ERROR";
    }

    private boolean matches(SubscriptionDto current, SubscriptionPlan plan, String product, String reference) {
        return Boolean.TRUE.equals(current.getActiveEntitlement()) && current.getProvider() == PaymentProvider.REVENUECAT
                && current.getPlanType() == plan && product.equals(current.getProviderProductId())
                && reference != null && reference.equals(current.getAiCreditAllocationReference());
    }

    private SubscriptionPlan plan(String product) {
        if (product != null && properties.getProducts().getPro().contains(product)) return SubscriptionPlan.PRO;
        if (product != null && properties.getProducts().getPlus().contains(product)) return SubscriptionPlan.PLUS;
        throw new IllegalArgumentException("Unknown subscription product");
    }

    private boolean isAddon(String product) {
        return product != null && properties.getProducts().getAiAddonQuotas().getOrDefault(product, 0) > 0;
    }

    private Outcome verifyAddon(Claim claim) {
        var payload = provider.recoverableAddonPurchase(claim.userId(), claim.product(), claim.createdAt(), claim.expectedTransactionId());
        if (payload == null) return new Outcome(Status.PENDING, null, "AWAITING_ADDON_TRANSACTION", null, null);
        var body = payload.path("event");
        String transactionId = body.path("transaction_id").asText();
        var response = webhooks.processWebhook(properties.getWebhookAuthorization(), payload);
        if (response != null && "REQUIRES_REVIEW".equals(response.getStatus()))
            return new Outcome(Status.REQUIRES_REVIEW, null, "ADDON_OWNERSHIP_CONFLICT", claim.product(), transactionId);
        boolean processed = events.existsByUser_IdAndProviderAndProductIdAndTransactionIdAndEnvironmentAndEventTypeAndStatus(
                claim.userId(), PaymentProvider.REVENUECAT, claim.product(), transactionId,
                body.path("environment").asText(), "NON_RENEWING_PURCHASE",
                com.grun.calorietracker.enums.SubscriptionProviderEventStatus.PROCESSED);
        boolean refunded = events.existsByUser_IdAndProviderAndTransactionIdAndCancelReasonAndStatus(
                claim.userId(), PaymentProvider.REVENUECAT, transactionId, "CUSTOMER_SUPPORT",
                com.grun.calorietracker.enums.SubscriptionProviderEventStatus.PROCESSED);
        return new Outcome(refunded ? Status.REQUIRES_REVIEW : processed ? Status.VERIFIED : Status.PENDING,
                null, refunded ? "ADDON_REFUNDED" : processed ? "ADDON_TRANSACTION_VERIFIED" : "ADDON_PROCESSING_PENDING",
                claim.product(), transactionId);
    }
}

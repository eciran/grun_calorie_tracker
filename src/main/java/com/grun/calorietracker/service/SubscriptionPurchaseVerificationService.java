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
    public enum Status { PENDING, VERIFIED, SCHEDULED, REQUIRES_REVIEW, PROVIDER_UNAVAILABLE }
    public record Result(Status status, SubscriptionDto subscription, int retryAfterSeconds) { }
    private record Claim(Long userId, String email, String product, int attempt, Instant lease) { }
    private record Outcome(Status status, String allocationReference) { }

    private final UserRepository users;
    private final SubscriptionVerificationRepository verifications;
    private final RevenueCatPurchaseEvidenceClient provider;
    private final RevenueCatProperties properties;
    private final SubscriptionService subscriptions;
    private final RevenueCatWebhookService webhooks;
    private final TransactionTemplate transaction;

    public SubscriptionPurchaseVerificationService(UserRepository users, SubscriptionVerificationRepository verifications,
            RevenueCatPurchaseEvidenceClient provider, RevenueCatProperties properties, SubscriptionService subscriptions,
            RevenueCatWebhookService webhooks, PlatformTransactionManager manager) {
        this.users = users;
        this.verifications = verifications;
        this.provider = provider;
        this.properties = properties;
        this.subscriptions = subscriptions;
        this.webhooks = webhooks;
        transaction = new TransactionTemplate(manager);
    }

    public Result request(String email, String productId, String attemptId) {
        plan(productId);
        var state = transaction.execute(tx -> {
            var user = users.findByEmailForUpdate(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
            var existing = verifications.findById(user.getId()).orElse(null);
            if (existing != null && !existing.getAttemptId().equals(attemptId)
                    && (existing.getNextAttemptAt() != null || existing.getUpdatedAt().isAfter(Instant.now().minusSeconds(10)))) {
                throw new com.grun.calorietracker.exception.RequestConflictException("Another purchase is being verified.");
            }
            if (existing != null && existing.getAttemptId().equals(attemptId)) {
                if (!existing.getProductId().equals(productId)) throw new IllegalArgumentException("Purchase attempt product mismatch");
                if (Status.REQUIRES_REVIEW.name().equals(existing.getStatus())
                        && existing.getUpdatedAt().isBefore(Instant.now().minusSeconds(60))) {
                    existing.setStatus(Status.PENDING.name());
                    existing.setAttempts(0);
                    existing.setNextAttemptAt(Instant.now());
                    existing.setUpdatedAt(Instant.now());
                    return verifications.saveAndFlush(existing);
                }
                return existing;
            }
            var created = new SubscriptionVerificationEntity();
            created.setUserId(user.getId());
            created.setProductId(productId);
            created.setAttemptId(attemptId);
            created.setStatus(Status.PENDING.name());
            created.setUpdatedAt(Instant.now());
            created.setNextAttemptAt(Instant.now());
            return verifications.saveAndFlush(created);
        });
        SubscriptionDto current = subscriptions.getCurrentSubscription(email);
        Status status = Status.valueOf(state.getStatus());
        if (status == Status.VERIFIED && !matches(current, plan(productId), productId, state.getAllocationReference())) {
            status = Status.REQUIRES_REVIEW;
        }
        if (status == Status.SCHEDULED && (!productId.equals(current.getScheduledProductId())
                || current.getScheduledChangeAt() == null || !current.getScheduledChangeAt().isAfter(Instant.now()))) {
            status = Status.REQUIRES_REVIEW;
        }
        return new Result(status, current, 5);
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
            if (state.getAttempts() >= 8) {
                state.setStatus(Status.REQUIRES_REVIEW.name());
                state.setNextAttemptAt(null);
                state.setLeaseUntil(null);
                state.setUpdatedAt(now);
                verifications.saveAndFlush(state);
                return null;
            }
            state.setAttempts(state.getAttempts() + 1);
            state.setLeaseUntil(now.plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
            verifications.saveAndFlush(state);
            return new Claim(userId, user.getEmail(), state.getProductId(), state.getAttempts(), state.getLeaseUntil());
        });
        if (claim == null) return;
        Outcome outcome;
        try { outcome = verify(claim); }
        catch (RuntimeException ex) {
            log.warn("subscription_verification_provider_unavailable userId={} exception={}", userId, ex.getClass().getSimpleName());
            outcome = new Outcome(Status.PROVIDER_UNAVAILABLE, null);
        }
        Outcome result = outcome;
        transaction.executeWithoutResult(tx -> {
            if (users.findByIdForUpdate(userId).isEmpty()) return;
            var state = verifications.findById(userId).orElse(null);
            if (state == null || !claim.lease().equals(state.getLeaseUntil()) || !claim.product().equals(state.getProductId())) return;
            boolean success = result.status() == Status.VERIFIED || result.status() == Status.SCHEDULED;
            boolean terminal = success || result.status() == Status.REQUIRES_REVIEW || claim.attempt() >= 8;
            state.setStatus((claim.attempt() >= 8 && !success
                    ? Status.REQUIRES_REVIEW : result.status()).name());
            state.setAllocationReference(result.allocationReference());
            state.setUpdatedAt(Instant.now());
            state.setLeaseUntil(null);
            state.setNextAttemptAt(terminal ? null : Instant.now().plusSeconds(Math.min(300, 5L << claim.attempt())));
            verifications.saveAndFlush(state);
            if (Status.REQUIRES_REVIEW.name().equals(state.getStatus())) {
                log.error("subscription_verification_requires_review userId={} attempts={}", userId, claim.attempt());
            }
        });
    }

    private Outcome verify(Claim claim) {
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
        if (evidence.isEmpty()) return new Outcome(Status.PENDING, null);
        if (evidence.size() != 1 || evidence.get(0).ownershipConflict()) return new Outcome(Status.REQUIRES_REVIEW, null);
        var purchase = evidence.get(0);
        SubscriptionPlan plan = plan(claim.product());
        String reference = SubscriptionAllocationIdentity.of(purchase.environment(), purchase.store(), plan,
                purchase.productId(), purchase.transactionId(), purchase.purchasedAt().toEpochMilli());
        if (!matches(current, plan, claim.product(), reference)) {
            var payload = provider.recoverablePurchaseEvent(claim.userId(), purchase);
            if (payload == null) return new Outcome(Status.PENDING, null);
            // Server-fetched event only: reuse ownership, transaction rollback and idempotent credit allocation.
            webhooks.processWebhook(properties.getWebhookAuthorization(), payload);
            current = subscriptions.getCurrentSubscription(claim.email());
        }
        return new Outcome(matches(current, plan, claim.product(), reference) ? Status.VERIFIED : Status.PENDING, reference);
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
}

package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.PromoProviderRedemptionCommand;
import com.grun.calorietracker.entity.AppliedPromoEntity;
import com.grun.calorietracker.entity.PromoCodeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.AppliedPromoRepository;
import com.grun.calorietracker.repository.PromoCodeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.PromoProviderRedemptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PromoProviderRedemptionServiceImpl implements PromoProviderRedemptionService {
    private final PromoCodeRepository promoRepository;
    private final AppliedPromoRepository redemptionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void recordVerifiedPurchase(PromoProviderRedemptionCommand command) {
        requireCommand(command);
        LocalDateTime now = LocalDateTime.now();
        var duplicate = redemptionRepository.findByProviderEventId(command.providerEventId());
        if (duplicate.isPresent()) {
            AppliedPromoEntity existing = duplicate.get();
            existing.setDuplicateHits(existing.getDuplicateHits() + 1);
            existing.setLastDuplicateAt(now);
            redemptionRepository.save(existing);
            return;
        }

        List<PromoCodeEntity> matches = promoRepository.findActiveProviderCandidates(command.productId(), now).stream()
                .filter(promo -> storeMatches(promo.getTargetStore(), command.store()))
                .filter(promo -> blank(promo.getProviderOfferId()) || promo.getProviderOfferId().equals(command.offeringId()))
                .filter(promo -> blank(promo.getTargetProductId()) || promo.getTargetProductId().equals(command.productId()))
                .toList();
        if (matches.isEmpty()) return;
        if (matches.size() > 1) {
            throw new IllegalStateException("Ambiguous active promotion provider mapping for product " + command.productId());
        }

        PromoCodeEntity promo = promoRepository.findByIdForUpdate(matches.get(0).getId())
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
        UserEntity user = userRepository.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String rejection = rejectionReason(promo, user, command, now);

        AppliedPromoEntity redemption = new AppliedPromoEntity();
        redemption.setPromoCode(promo);
        redemption.setUser(user);
        redemption.setAppliedAt(now);
        redemption.setIdempotencyKey("rc:" + sha256(command.providerEventId()));
        redemption.setProviderEventId(command.providerEventId());
        redemption.setStatus(rejection == null ? PromoRedemptionStatus.CONVERTED : PromoRedemptionStatus.REJECTED);
        redemption.setAmountMinor(command.amountMinor());
        redemption.setCurrency(normalizeCurrency(command.currency(), promo.getCurrency()));
        redemption.setRejectionReason(rejection);
        redemption.setConvertedAt(rejection == null ? now : null);
        redemption.setDuplicateHits(0);
        redemptionRepository.save(redemption);

        if (rejection == null) {
            promo.setUsedCount(promo.getUsedCount() + 1);
            promo.setUpdatedAt(now);
            promoRepository.save(promo);
        }
    }

    private String rejectionReason(PromoCodeEntity promo, UserEntity user,
                                   PromoProviderRedemptionCommand command, LocalDateTime now) {
        if (!promo.isActive() || promo.getStatus() != PromoStatus.ACTIVE
                || (promo.getStartAt() != null && promo.getStartAt().isAfter(now))
                || (promo.getEndAt() != null && !promo.getEndAt().isAfter(now))) {
            return "ELIGIBILITY: Promotion is outside its active window.";
        }
        if (promo.getTargetRegion() != null && promo.getTargetRegion() != user.getMarketRegion()) {
            return "ELIGIBILITY: User region does not match.";
        }
        SubscriptionPlan previousPlan = command.previousPlan() == null ? SubscriptionPlan.FREE : command.previousPlan();
        if (promo.getTargetPlan() != null && promo.getTargetPlan() != previousPlan) {
            return "ELIGIBILITY: Previous plan does not match.";
        }
        long allPrior = redemptionRepository.countConvertedForUser(user.getId());
        PromoEligibilityRule rule = promo.getEligibilityRule() == null ? PromoEligibilityRule.ALL_USERS : promo.getEligibilityRule();
        if (rule == PromoEligibilityRule.NO_PRIOR_PROMO_REDEMPTION && allPrior > 0) {
            return "ELIGIBILITY: User has a prior promotion redemption.";
        }
        if (rule == PromoEligibilityRule.FIRST_PAID_PURCHASE
                && (previousPlan != SubscriptionPlan.FREE || allPrior > 0)) {
            return "ELIGIBILITY: Not the user's first paid purchase.";
        }
        if (rule == PromoEligibilityRule.LAPSED_SUBSCRIBER && !isLapsed(command.previousStatus())) {
            return "ELIGIBILITY: User is not a lapsed subscriber.";
        }
        if (rule == PromoEligibilityRule.ADMIN_SUPPORT_ONLY) {
            return "ELIGIBILITY: Promotion requires an admin support grant.";
        }
        if (redemptionRepository.countConvertedForUser(user.getId(), promo.getId()) >= promo.getPerUserLimit()) {
            return "LIMIT: Per-user promotion limit exhausted.";
        }
        if (promo.getGlobalLimit() != null && promo.getUsedCount() >= promo.getGlobalLimit()) {
            return "LIMIT: Global promotion limit exhausted.";
        }
        return null;
    }

    private boolean storeMatches(PromoStore configured, String providerStore) {
        if (configured == null || configured == PromoStore.ALL || configured == PromoStore.REVENUECAT) return true;
        if (providerStore == null) return false;
        return configured == PromoStore.APPLE_APP_STORE && "APP_STORE".equalsIgnoreCase(providerStore)
                || configured == PromoStore.GOOGLE_PLAY && "PLAY_STORE".equalsIgnoreCase(providerStore);
    }

    private boolean isLapsed(SubscriptionStatus status) {
        return status == SubscriptionStatus.CANCELED || status == SubscriptionStatus.EXPIRED
                || status == SubscriptionStatus.REFUNDED;
    }

    private void requireCommand(PromoProviderRedemptionCommand command) {
        if (command == null || command.userId() == null || command.userId() <= 0
                || blank(command.providerEventId()) || blank(command.productId())) {
            throw new IllegalArgumentException("Verified provider promotion attribution requires user, event and product ids.");
        }
    }

    private String normalizeCurrency(String value, String fallback) {
        String currency = blank(value) ? fallback : value;
        return blank(currency) ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create provider attribution key.", ex);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

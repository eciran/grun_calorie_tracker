package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.AdminAiQuotaPromotionPolicyDto;
import com.grun.calorietracker.dto.AdminAiQuotaPromotionPolicyRequestDto;
import com.grun.calorietracker.dto.AiQuotaPromotionDecisionDto;
import com.grun.calorietracker.dto.AiQuotaPromotionDecisionRequestDto;
import com.grun.calorietracker.dto.AiQuotaPromotionEventRequestDto;
import com.grun.calorietracker.dto.AiQuotaPromotionOfferDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiQuotaPromotionPolicyEntity;
import com.grun.calorietracker.entity.AiQuotaPromotionReservationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.AiQuotaPromotionPolicyRepository;
import com.grun.calorietracker.repository.AiQuotaPromotionReservationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AiQuotaPromotionService;
import com.grun.calorietracker.service.SubscriptionService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiQuotaPromotionServiceImpl implements AiQuotaPromotionService {
    private static final long POLICY_ID = 1L;
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(5);

    private final AiQuotaPromotionPolicyRepository policies;
    private final AiQuotaPromotionReservationRepository reservations;
    private final UserRepository users;
    private final SubscriptionService subscriptionService;
    private final RevenueCatProperties revenueCatProperties;
    private final AdminAuditService auditService;
    private final Clock analyticsClock;

    @Override
    @Transactional(readOnly = true)
    public AdminAiQuotaPromotionPolicyDto getPolicy() {
        return dto(policy());
    }

    @Override
    @Transactional
    public AdminAiQuotaPromotionPolicyDto updatePolicy(AdminAiQuotaPromotionPolicyRequestDto request,
                                                        String admin,
                                                        String correlationId) {
        AiQuotaPromotionPolicyEntity entity = policies.findByIdForUpdate(POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("AI quota promotion policy is missing."));
        if (!Objects.equals(entity.getVersion(), request.version())) {
            throw new OptimisticLockException("AI quota promotion policy version conflict.");
        }
        Map<String, Object> before = audit(entity);
        entity.setEnabled(request.enabled());
        entity.setMinimumIntervalHours(request.minimumIntervalHours());
        entity.setMaxImpressions24h(request.maxImpressions24h());
        entity.setDismissCooldownHours(request.dismissCooldownHours());
        entity.setRolloutPercentage(request.rolloutPercentage());
        entity.setCampaignVersion(entity.getCampaignVersion() + 1);
        entity.setChangeReason(request.changeReason().trim());
        entity.setUpdatedBy(admin);
        entity.setUpdatedAt(analyticsClock.instant());
        entity = policies.save(entity);
        auditService.record(admin, AdminAuditActionType.FREE_PROMOTION_POLICY_UPDATE,
                AdminAuditTargetType.FREE_PROMOTION_POLICY, "ai-quota-exhausted", before, audit(entity), correlationId);
        return dto(entity);
    }

    @Override
    @Transactional
    public AiQuotaPromotionDecisionDto decide(String email, AiQuotaPromotionDecisionRequestDto request) {
        UserEntity user = lockedUser(email);
        AiQuotaPromotionPolicyEntity policy = policy();
        Instant now = analyticsClock.instant();
        long version = policy.getCampaignVersion();

        if (!Boolean.TRUE.equals(policy.getEnabled())) return AiQuotaPromotionDecisionDto.denied("POLICY_DISABLED", version);
        if (request.pendingPurchase()) return AiQuotaPromotionDecisionDto.denied("PURCHASE_PENDING", version);

        SubscriptionDto subscription = subscriptionService.getCurrentSubscription(email);
        String eligibilityFailure = eligibilityFailure(subscription);
        if (eligibilityFailure != null) return AiQuotaPromotionDecisionDto.denied(eligibilityFailure, version);

        List<AiQuotaPromotionOfferDto> offers = offers();
        if (offers.isEmpty()) return AiQuotaPromotionDecisionDto.denied("NO_CREDIT_PRODUCTS", version);
        if (!inRollout(user.getId(), version, policy.getRolloutPercentage())) {
            return AiQuotaPromotionDecisionDto.denied("OUTSIDE_ROLLOUT", version);
        }
        if (reservations.existsByUserAndSessionIdAndImpressionAtIsNotNull(user, request.sessionId())) {
            return AiQuotaPromotionDecisionDto.denied("SESSION_LIMIT_REACHED", version);
        }
        if (reservations.existsByUserAndSessionIdAndImpressionAtIsNullAndExpiresAtAfter(user, request.sessionId(), now)) {
            return AiQuotaPromotionDecisionDto.denied("RESERVATION_EXISTS", version);
        }
        if (reservations.existsByUserAndImpressionAtAfter(user,
                now.minus(Duration.ofHours(policy.getMinimumIntervalHours())))) {
            return AiQuotaPromotionDecisionDto.denied("MINIMUM_INTERVAL", version);
        }
        if (reservations.existsByUserAndDismissedAtAfter(user,
                now.minus(Duration.ofHours(policy.getDismissCooldownHours())))) {
            return AiQuotaPromotionDecisionDto.denied("DISMISS_COOLDOWN", version);
        }
        if (reservations.countByUserAndImpressionAtAfter(user, now.minus(Duration.ofHours(24)))
                >= policy.getMaxImpressions24h()) {
            return AiQuotaPromotionDecisionDto.denied("ROLLING_LIMIT", version);
        }

        AiQuotaPromotionReservationEntity reservation = new AiQuotaPromotionReservationEntity();
        reservation.setUser(user);
        reservation.setReservationToken(token(user.getId(), request.sessionId(), now));
        reservation.setSessionId(request.sessionId());
        reservation.setCampaignVersion(version);
        reservation.setReservedAt(now);
        reservation.setExpiresAt(now.plus(RESERVATION_TTL));
        reservations.save(reservation);

        SubscriptionPlan upgradePlan = subscription.getPlanType() == SubscriptionPlan.PLUS
                ? SubscriptionPlan.PRO : null;
        return new AiQuotaPromotionDecisionDto(true, "ELIGIBLE", reservation.getReservationToken(),
                reservation.getExpiresAt(), version, subscription.getPlanType(), upgradePlan,
                subscription.getAiRemainingThisPeriod(), offers);
    }

    @Override
    @Transactional
    public void recordImpression(String email, AiQuotaPromotionEventRequestDto request) {
        mutate(email, request, Event.IMPRESSION);
    }

    @Override
    @Transactional
    public void recordDismissal(String email, AiQuotaPromotionEventRequestDto request) {
        mutate(email, request, Event.DISMISSAL);
    }

    @Override
    @Transactional
    public void recordCta(String email, AiQuotaPromotionEventRequestDto request) {
        mutate(email, request, Event.CTA);
    }

    private void mutate(String email, AiQuotaPromotionEventRequestDto request, Event event) {
        UserEntity user = lockedUser(email);
        AiQuotaPromotionReservationEntity reservation = reservations
                .findByReservationTokenAndUser(request.reservationToken(), user)
                .orElseThrow(() -> new IllegalArgumentException("AI quota promotion reservation not found."));
        Instant now = analyticsClock.instant();
        if (event == Event.IMPRESSION) {
            AiQuotaPromotionPolicyEntity policy = policy();
            if (reservation.getImpressionAt() != null) return;
            SubscriptionDto subscription = subscriptionService.getCurrentSubscription(email);
            if (!Boolean.TRUE.equals(policy.getEnabled())
                    || !Objects.equals(policy.getCampaignVersion(), reservation.getCampaignVersion())
                    || !reservation.getExpiresAt().isAfter(now)
                    || eligibilityFailure(subscription) != null) {
                throw new IllegalArgumentException("AI quota promotion reservation expired or is no longer eligible.");
            }
            reservation.setImpressionAt(now);
        } else {
            if (reservation.getImpressionAt() == null) {
                throw new IllegalArgumentException("AI quota promotion impression was not recorded.");
            }
            if (event == Event.DISMISSAL && reservation.getDismissedAt() == null) reservation.setDismissedAt(now);
            if (event == Event.CTA && reservation.getCtaAt() == null) reservation.setCtaAt(now);
        }
        reservations.save(reservation);
    }

    private String eligibilityFailure(SubscriptionDto subscription) {
        if (subscription.getPlanType() != SubscriptionPlan.PLUS && subscription.getPlanType() != SubscriptionPlan.PRO) {
            return "PAID_PLAN_REQUIRED";
        }
        if (!Boolean.TRUE.equals(subscription.getActiveEntitlement())) return "ACTIVE_ENTITLEMENT_REQUIRED";
        if (subscription.getAiRemainingThisPeriod() == null || subscription.getAiRemainingThisPeriod() != 0) {
            return "QUOTA_NOT_EXHAUSTED";
        }
        return null;
    }

    private List<AiQuotaPromotionOfferDto> offers() {
        Map<String, Integer> quotas = revenueCatProperties.getProducts().getAiAddonQuotas();
        Map<String, Integer> validity = revenueCatProperties.getProducts().getAiAddonValidityDays();
        int defaultValidity = revenueCatProperties.getProducts().getDefaultAiAddonValidityDays();
        return quotas.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank()
                        && entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> new AiQuotaPromotionOfferDto(entry.getKey(), entry.getValue(),
                        Math.max(1, validity.getOrDefault(entry.getKey(), defaultValidity))))
                .sorted(Comparator.comparingInt(AiQuotaPromotionOfferDto::credits)
                        .thenComparing(AiQuotaPromotionOfferDto::productId))
                .toList();
    }

    private UserEntity lockedUser(String email) {
        UserEntity found = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        return users.findByIdForUpdate(found.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    private AiQuotaPromotionPolicyEntity policy() {
        return policies.findById(POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("AI quota promotion policy is missing."));
    }

    private boolean inRollout(Long userId, long campaignVersion, int percentage) {
        if (percentage <= 0) return false;
        if (percentage >= 100) return true;
        return Math.floorMod(Objects.hash(userId, campaignVersion), 100) < percentage;
    }

    private String token(Long userId, String sessionId, Instant now) {
        try {
            String raw = userId + ":" + sessionId + ":" + now + ":" + UUID.randomUUID();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create AI quota promotion reservation.", exception);
        }
    }

    private AdminAiQuotaPromotionPolicyDto dto(AiQuotaPromotionPolicyEntity entity) {
        return new AdminAiQuotaPromotionPolicyDto(entity.getId(), entity.getVersion(), entity.getEnabled(),
                entity.getMinimumIntervalHours(), entity.getMaxImpressions24h(), entity.getDismissCooldownHours(),
                entity.getRolloutPercentage(), entity.getCampaignVersion(), entity.getChangeReason(),
                entity.getUpdatedBy(), entity.getUpdatedAt());
    }

    private Map<String, Object> audit(AiQuotaPromotionPolicyEntity entity) {
        return Map.of("enabled", entity.getEnabled(),
                "minimumIntervalHours", entity.getMinimumIntervalHours(),
                "maxImpressions24h", entity.getMaxImpressions24h(),
                "dismissCooldownHours", entity.getDismissCooldownHours(),
                "rolloutPercentage", entity.getRolloutPercentage(),
                "campaignVersion", entity.getCampaignVersion());
    }

    private enum Event { IMPRESSION, DISMISSAL, CTA }
}

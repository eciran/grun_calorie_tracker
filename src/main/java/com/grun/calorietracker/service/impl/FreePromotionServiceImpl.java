package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FreePromotionServiceImpl implements FreePromotionService {
    private static final long POLICY_ID = 1L;
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(5);

    private final FreePromotionPolicyRepository policies;
    private final FreePromotionUserStateRepository userStates;
    private final FreePromotionReservationRepository reservations;
    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final FoodLogsRepository foodLogs;
    private final AdminAuditService auditService;
    private final Clock analyticsClock;

    @Override
    @Transactional(readOnly = true)
    public AdminFreePromotionPolicyDto getPolicy() {
        return dto(policy());
    }

    @Override
    @Transactional
    public AdminFreePromotionPolicyDto updatePolicy(AdminFreePromotionPolicyRequestDto request, String admin,
                                                     String correlationId) {
        FreePromotionPolicyEntity entity = policies.findByIdForUpdate(POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("Free promotion policy is missing."));
        if (!Objects.equals(entity.getVersion(), request.version())) {
            throw new OptimisticLockException("Free promotion policy version conflict.");
        }
        Map<String, Object> before = audit(entity);
        entity.setEnabled(request.enabled());
        entity.setMinimumIntervalHours(request.minimumIntervalHours());
        entity.setMaxImpressions24h(request.maxImpressions24h());
        entity.setDismissCooldownHours(request.dismissCooldownHours());
        entity.setMinimumSessionNumber(request.minimumSessionNumber());
        entity.setRolloutPercentage(request.rolloutPercentage());
        entity.setCampaignVersion(entity.getCampaignVersion() + 1);
        entity.setChangeReason(request.changeReason().trim());
        entity.setUpdatedBy(admin);
        entity.setUpdatedAt(analyticsClock.instant());
        entity = policies.save(entity);
        auditService.record(admin, AdminAuditActionType.FREE_PROMOTION_POLICY_UPDATE,
                AdminAuditTargetType.FREE_PROMOTION_POLICY, entity.getId().toString(), before, audit(entity), correlationId);
        return dto(entity);
    }

    @Override
    @Transactional
    public FreePromotionDecisionDto decide(String email, FreePromotionDecisionRequestDto request) {
        UserEntity foundUser = users.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("User not found."));
        UserEntity user = users.findByIdForUpdate(foundUser.getId()).orElseThrow(() -> new IllegalArgumentException("User not found."));
        Instant now = analyticsClock.instant();
        FreePromotionPolicyEntity policy = policy();
        FreePromotionUserStateEntity state = userStates.findByUser(user).orElseGet(() -> newState(user, now));
        if (!request.sessionId().equals(state.getLastSessionId())) {
            state.setSessionNumber(state.getSessionNumber() + 1);
            state.setLastSessionId(request.sessionId());
            state.setLastSessionAt(now);
        }
        state.setUpdatedAt(now);
        userStates.save(state);

        long version = policy.getCampaignVersion();
        if (!Boolean.TRUE.equals(policy.getEnabled())) return FreePromotionDecisionDto.denied("POLICY_DISABLED", version);
        if (request.pendingPurchase()) return FreePromotionDecisionDto.denied("PURCHASE_PENDING", version);
        if (request.placement() != FreePromotionPlacement.HOME_RETURN) return FreePromotionDecisionDto.denied("PLACEMENT_NOT_ALLOWED", version);
        Optional<SubscriptionEntity> subscription = subscriptions.findByUser(user);
        if (subscription.isPresent() && subscription.get().getPlanType() != SubscriptionPlan.FREE) {
            return FreePromotionDecisionDto.denied("NOT_VERIFIED_FREE", version);
        }
        if (state.getSessionNumber() < policy.getMinimumSessionNumber()) {
            return FreePromotionDecisionDto.denied("MINIMUM_SESSION_NOT_REACHED", version);
        }
        if (foodLogs.countByUser(user) < 1) return FreePromotionDecisionDto.denied("BASIC_ACTIVITY_REQUIRED", version);
        if (!inRollout(user.getId(), version, policy.getRolloutPercentage())) {
            return FreePromotionDecisionDto.denied("OUTSIDE_ROLLOUT", version);
        }
        if (reservations.existsByUserAndSessionIdAndImpressionAtIsNotNull(user, request.sessionId())) {
            return FreePromotionDecisionDto.denied("SESSION_LIMIT_REACHED", version);
        }
        if (reservations.existsByUserAndSessionIdAndImpressionAtIsNullAndExpiresAtAfter(user, request.sessionId(), now)) {
            return FreePromotionDecisionDto.denied("RESERVATION_EXISTS", version);
        }
        if (reservations.existsByUserAndImpressionAtAfter(user, now.minus(Duration.ofHours(policy.getMinimumIntervalHours())))) {
            return FreePromotionDecisionDto.denied("MINIMUM_INTERVAL", version);
        }
        if (reservations.existsByUserAndDismissedAtAfter(user, now.minus(Duration.ofHours(policy.getDismissCooldownHours())))) {
            return FreePromotionDecisionDto.denied("DISMISS_COOLDOWN", version);
        }
        if (reservations.countByUserAndImpressionAtAfter(user, now.minus(Duration.ofHours(24))) >= policy.getMaxImpressions24h()) {
            return FreePromotionDecisionDto.denied("ROLLING_LIMIT", version);
        }

        FreePromotionReservationEntity reservation = new FreePromotionReservationEntity();
        reservation.setUser(user);
        reservation.setReservationToken(token(user.getId(), request.sessionId(), now));
        reservation.setSessionId(request.sessionId());
        reservation.setPlacement(request.placement());
        reservation.setCampaignVersion(version);
        reservation.setReservedAt(now);
        reservation.setExpiresAt(now.plus(RESERVATION_TTL));
        reservations.save(reservation);
        return new FreePromotionDecisionDto(true, "ELIGIBLE", reservation.getReservationToken(),
                reservation.getExpiresAt(), version);
    }

    @Override @Transactional public void recordImpression(String email, FreePromotionEventRequestDto request) {
        mutate(email, request, Event.IMPRESSION);
    }
    @Override @Transactional public void recordDismissal(String email, FreePromotionEventRequestDto request) {
        mutate(email, request, Event.DISMISSAL);
    }
    @Override @Transactional public void recordCta(String email, FreePromotionEventRequestDto request) {
        mutate(email, request, Event.CTA);
    }

    private void mutate(String email, FreePromotionEventRequestDto request, Event event) {
        UserEntity foundUser = users.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("User not found."));
        UserEntity user = users.findByIdForUpdate(foundUser.getId()).orElseThrow(() -> new IllegalArgumentException("User not found."));
        FreePromotionReservationEntity reservation = reservations.findByReservationTokenAndUser(request.reservationToken(), user)
                .orElseThrow(() -> new IllegalArgumentException("Promotion reservation not found."));
        Instant now = analyticsClock.instant();
        if (event == Event.IMPRESSION) {
            FreePromotionPolicyEntity policy = policy();
            if (reservation.getImpressionAt() != null) return;
            if (!Boolean.TRUE.equals(policy.getEnabled()) || !Objects.equals(policy.getCampaignVersion(), reservation.getCampaignVersion())
                    || !reservation.getExpiresAt().isAfter(now)) {
                throw new IllegalArgumentException("Promotion reservation expired or disabled.");
            }
            reservation.setImpressionAt(now);
        } else {
            if (reservation.getImpressionAt() == null) throw new IllegalArgumentException("Promotion impression was not recorded.");
            if (event == Event.DISMISSAL && reservation.getDismissedAt() == null) reservation.setDismissedAt(now);
            if (event == Event.CTA && reservation.getCtaAt() == null) reservation.setCtaAt(now);
        }
        reservations.save(reservation);
    }

    private FreePromotionPolicyEntity policy() {
        return policies.findById(POLICY_ID).orElseThrow(() -> new IllegalStateException("Free promotion policy is missing."));
    }
    private FreePromotionUserStateEntity newState(UserEntity user, Instant now) {
        FreePromotionUserStateEntity state = new FreePromotionUserStateEntity();
        state.setUser(user); state.setSessionNumber(0); state.setUpdatedAt(now); return state;
    }
    private boolean inRollout(Long userId, long campaignVersion, int percentage) {
        if (percentage <= 0) return false;
        if (percentage >= 100) return true;
        return Math.floorMod(Objects.hash(userId, campaignVersion), 100) < percentage;
    }
    private String token(Long userId, String sessionId, Instant now) {
        try {
            String raw = userId + ":" + sessionId + ":" + now + ":" + UUID.randomUUID();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Could not create promotion reservation.", ex); }
    }
    private AdminFreePromotionPolicyDto dto(FreePromotionPolicyEntity e) {
        return new AdminFreePromotionPolicyDto(e.getId(), e.getVersion(), e.getEnabled(), e.getMinimumIntervalHours(),
                e.getMaxImpressions24h(), e.getDismissCooldownHours(), e.getMinimumSessionNumber(),
                e.getRolloutPercentage(), e.getCampaignVersion(), e.getChangeReason(), e.getUpdatedBy(), e.getUpdatedAt());
    }
    private Map<String, Object> audit(FreePromotionPolicyEntity e) {
        return Map.of("enabled", e.getEnabled(), "minimumIntervalHours", e.getMinimumIntervalHours(),
                "maxImpressions24h", e.getMaxImpressions24h(), "dismissCooldownHours", e.getDismissCooldownHours(),
                "minimumSessionNumber", e.getMinimumSessionNumber(), "rolloutPercentage", e.getRolloutPercentage(),
                "campaignVersion", e.getCampaignVersion());
    }
    private enum Event { IMPRESSION, DISMISSAL, CTA }
}

package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminPromoServiceImpl implements AdminPromoService {
    private static final String ENTITLEMENT_GUARDRAIL =
            "Promotion records never grant paid entitlement; access requires a provider-verified event or controlled admin grant.";

    private final PromoCodeRepository promoRepository;
    private final AppliedPromoRepository redemptionRepository;
    private final UserRepository userRepository;
    private final AdminAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public AdminPromoPageDto list(String search, PromoStatus status, PromoType type, PromoStore store, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PromoCodeEntity> result = promoRepository.findAll(specification(search, status, type, store), pageable);
        return new AdminPromoPageDto(result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPromoDto get(Long id) {
        return toDto(requirePromo(id));
    }

    @Override
    @Transactional
    public AdminPromoDto create(AdminPromoRequestDto request, String adminEmail, String correlationId) {
        validateRequest(request, null);
        PromoCodeEntity entity = new PromoCodeEntity();
        entity.setStatus(PromoStatus.DRAFT);
        entity.setActive(false);
        entity.setUsedCount(0);
        entity.setCreatedBy(adminEmail);
        entity.setCreatedAt(LocalDateTime.now());
        applyRequest(entity, request, adminEmail);
        entity = promoRepository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.PROMO_CREATE, AdminAuditTargetType.PROMOTION,
                entity.getId().toString(), null, auditValue(entity), correlationId);
        return toDto(entity);
    }

    @Override
    @Transactional
    public AdminPromoDto update(Long id, AdminPromoRequestDto request, String adminEmail, String correlationId) {
        PromoCodeEntity entity = promoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
        requireDraft(entity);
        validateRequest(request, id);
        Map<String, Object> before = auditValue(entity);
        applyRequest(entity, request, adminEmail);
        entity = promoRepository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.PROMO_UPDATE, AdminAuditTargetType.PROMOTION,
                id.toString(), before, auditValue(entity), correlationId);
        return toDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPromoPreviewDto preview(Long id) {
        PromoCodeEntity entity = requirePromo(id);
        List<String> issues = validationIssues(entity);
        return new AdminPromoPreviewDto(id, userRepository.count(audienceSpecification(entity)),
                providerMappingReady(entity), issues.isEmpty(), issues);
    }

    @Override
    @Transactional
    public AdminPromoDto activate(Long id, String adminEmail, String correlationId) {
        PromoCodeEntity entity = promoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
        if (entity.getStatus() != PromoStatus.DRAFT && entity.getStatus() != PromoStatus.DEACTIVATED) {
            throw new IllegalArgumentException("Only draft or deactivated promotions can be activated.");
        }
        List<String> issues = validationIssues(entity);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException("Promotion is not activation-ready: " + String.join("; ", issues));
        }
        PromoStatus oldStatus = entity.getStatus();
        entity.setStatus(PromoStatus.ACTIVE);
        entity.setActive(true);
        entity.setDeactivatedReason(null);
        entity.setUpdatedBy(adminEmail);
        entity.setUpdatedAt(LocalDateTime.now());
        entity = promoRepository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.PROMO_ACTIVATE, AdminAuditTargetType.PROMOTION,
                id.toString(), Map.of("status", oldStatus), auditValue(entity), correlationId);
        return toDto(entity);
    }

    @Override
    @Transactional
    public AdminPromoDto deactivate(Long id, String reason, String adminEmail, String correlationId) {
        PromoCodeEntity entity = promoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
        if (entity.getStatus() != PromoStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active promotions can be deactivated.");
        }
        Map<String, Object> before = auditValue(entity);
        entity.setStatus(PromoStatus.DEACTIVATED);
        entity.setActive(false);
        entity.setDeactivatedReason(reason.trim());
        entity.setUpdatedBy(adminEmail);
        entity.setUpdatedAt(LocalDateTime.now());
        entity = promoRepository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.PROMO_DEACTIVATE, AdminAuditTargetType.PROMOTION,
                id.toString(), before, auditValue(entity), correlationId);
        return toDto(entity);
    }

    @Override
    @Transactional
    public AdminPromoReconciliationDto reconcile(Long id, String adminEmail, String correlationId) {
        PromoCodeEntity entity = requirePromo(id);
        List<String> issues = new ArrayList<>();
        if (!providerMappingReady(entity)) {
            issues.add("Store-targeted promotions require both provider offer id and provider product id.");
        }
        AdminPromoReconciliationDto result = new AdminPromoReconciliationDto(entity.getId(), entity.getTargetStore(),
                issues.isEmpty(), entity.getProviderOfferId(), entity.getProviderProductId(), issues, ENTITLEMENT_GUARDRAIL);
        auditService.record(adminEmail, AdminAuditActionType.PROMO_RECONCILE, AdminAuditTargetType.PROMOTION,
                id.toString(), null, Map.of("mappingReady", result.mappingReady(), "store", entity.getTargetStore()), correlationId);
        return result;
    }

    @Override
    @Transactional
    public AdminPromoRedemptionDto recordRedemption(Long id, AdminPromoRedemptionRequestDto request,
                                                    String adminEmail, String correlationId) {
        Optional<AppliedPromoEntity> existing = redemptionRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
        if (existing.isPresent()) return toRedemptionDto(existing.get());

        PromoCodeEntity promo = promoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if ((request.getStatus() == PromoRedemptionStatus.PROVIDER_VERIFIED
                || request.getStatus() == PromoRedemptionStatus.CONVERTED) && !hasText(request.getProviderEventId())) {
            throw new IllegalArgumentException("Provider event id is required for verified or converted redemption.");
        }
        if (request.getStatus() == PromoRedemptionStatus.REJECTED && !hasText(request.getRejectionReason())) {
            throw new IllegalArgumentException("Rejection reason is required for rejected redemption.");
        }
        if (request.getStatus() == PromoRedemptionStatus.CONVERTED) {
            long userUsage = redemptionRepository.countConvertedForUser(user.getId(), promo.getId());
            if (userUsage >= promo.getPerUserLimit()) throw new IllegalArgumentException("Per-user promotion limit is exhausted.");
            if (promo.getGlobalLimit() != null && promo.getUsedCount() >= promo.getGlobalLimit()) {
                throw new IllegalArgumentException("Global promotion limit is exhausted.");
            }
            promo.setUsedCount(promo.getUsedCount() + 1);
            promo.setUpdatedAt(LocalDateTime.now());
            promoRepository.save(promo);
        }

        AppliedPromoEntity entity = new AppliedPromoEntity();
        entity.setPromoCode(promo);
        entity.setUser(user);
        entity.setAppliedAt(LocalDateTime.now());
        entity.setIdempotencyKey(request.getIdempotencyKey().trim());
        entity.setProviderEventId(trimToNull(request.getProviderEventId()));
        entity.setStatus(request.getStatus());
        entity.setAmountMinor(request.getAmountMinor());
        entity.setCurrency(hasText(request.getCurrency()) ? request.getCurrency().trim().toUpperCase(Locale.ROOT) : promo.getCurrency());
        entity.setRejectionReason(trimToNull(request.getRejectionReason()));
        entity.setConvertedAt(request.getStatus() == PromoRedemptionStatus.CONVERTED ? LocalDateTime.now() : null);
        entity = redemptionRepository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.PROMO_REDEMPTION_RECORD, AdminAuditTargetType.PROMOTION,
                id.toString(), null, Map.of("redemptionId", entity.getId(), "status", entity.getStatus(),
                        "idempotencyKey", entity.getIdempotencyKey()), correlationId);
        return toRedemptionDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPromoMetricsDto metrics(Long promoId) {
        PromoCodeEntity promo = promoId == null ? null : requirePromo(promoId);
        long total = redemptionRepository.countForPromo(promoId);
        long converted = redemptionRepository.countForPromoAndStatus(promoId, PromoRedemptionStatus.CONVERTED);
        long rejected = redemptionRepository.countForPromoAndStatus(promoId, PromoRedemptionStatus.REJECTED);
        return new AdminPromoMetricsDto(promoRepository.countCurrentlyActive(LocalDateTime.now()), total, converted,
                rejected, redemptionRepository.countUniqueUsers(promoId),
                redemptionRepository.sumConvertedRevenueByCurrency(promoId).stream()
                        .map(row -> new AdminPromoMetricsDto.CurrencyRevenue(row.getCurrency(), row.getAmountMinor()))
                        .toList(), rate(converted, total), rate(rejected, total));
    }

    private Specification<PromoCodeEntity> specification(String search, PromoStatus status, PromoType type, PromoStore store) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), value), cb.like(cb.lower(root.get("name")), value),
                        cb.like(cb.lower(root.get("campaignKey")), value)));
            }
            if (status != null) {
                LocalDateTime now = LocalDateTime.now();
                if (status == PromoStatus.EXPIRED) {
                    predicates.add(cb.equal(root.get("status"), PromoStatus.ACTIVE));
                    predicates.add(cb.isNotNull(root.get("endAt")));
                    predicates.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("endAt"), now));
                } else if (status == PromoStatus.ACTIVE) {
                    predicates.add(cb.equal(root.get("status"), PromoStatus.ACTIVE));
                    predicates.add(cb.isTrue(root.get("active")));
                    predicates.add(cb.or(cb.isNull(root.get("startAt")),
                            cb.lessThanOrEqualTo(root.<LocalDateTime>get("startAt"), now)));
                    predicates.add(cb.or(cb.isNull(root.get("endAt")),
                            cb.greaterThan(root.<LocalDateTime>get("endAt"), now)));
                } else {
                    predicates.add(cb.equal(root.get("status"), status));
                }
            }
            if (type != null) predicates.add(cb.equal(root.get("promoType"), type));
            if (store != null) predicates.add(cb.equal(root.get("targetStore"), store));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<UserEntity> audienceSpecification(PromoCodeEntity promo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("accountEnabled")));
            predicates.add(cb.isFalse(root.get("accountLocked")));
            predicates.add(cb.notEqual(root.get("role"), UserRole.ADMIN));
            if (promo.getTargetRegion() != null) predicates.add(cb.equal(root.get("marketRegion"), promo.getTargetRegion()));
            if (promo.getTargetPlan() != null && query != null) {
                Subquery<Long> matching = query.subquery(Long.class);
                Root<SubscriptionEntity> subscription = matching.from(SubscriptionEntity.class);
                matching.select(subscription.get("user").get("id"));
                matching.where(cb.equal(subscription.get("user").get("id"), root.get("id")),
                        cb.equal(subscription.get("planType"), promo.getTargetPlan()));
                Predicate planMatch = cb.exists(matching);
                if (promo.getTargetPlan() == SubscriptionPlan.FREE) {
                    Subquery<Long> any = query.subquery(Long.class);
                    Root<SubscriptionEntity> anySubscription = any.from(SubscriptionEntity.class);
                    any.select(anySubscription.get("user").get("id"));
                    any.where(cb.equal(anySubscription.get("user").get("id"), root.get("id")));
                    planMatch = cb.or(planMatch, cb.not(cb.exists(any)));
                }
                predicates.add(planMatch);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validateRequest(AdminPromoRequestDto request, Long currentId) {
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{3,80}")) throw new IllegalArgumentException("Promo code must use A-Z, 0-9, underscore or hyphen.");
        if (currentId == null ? promoRepository.findByCodeIgnoreCase(code).isPresent()
                : promoRepository.existsByCodeIgnoreCaseAndIdNot(code, currentId)) {
            throw new IllegalArgumentException("Promo code already exists.");
        }
        if (request.getStartAt() != null && request.getEndAt() != null && !request.getEndAt().isAfter(request.getStartAt())) {
            throw new IllegalArgumentException("Promotion end must be after start.");
        }
        if (request.getGlobalLimit() != null && request.getGlobalLimit() < request.getPerUserLimit()) {
            throw new IllegalArgumentException("Global limit must be greater than or equal to per-user limit.");
        }
    }

    private List<String> validationIssues(PromoCodeEntity entity) {
        List<String> issues = new ArrayList<>();
        if (entity.getEndAt() != null && !entity.getEndAt().isAfter(LocalDateTime.now())) issues.add("End time is in the past.");
        if (entity.getStartAt() != null && entity.getEndAt() != null && !entity.getEndAt().isAfter(entity.getStartAt())) issues.add("End time must be after start time.");
        if (!providerMappingReady(entity)) issues.add("Provider offer and product mapping are incomplete.");
        if (entity.getGlobalLimit() != null && entity.getUsedCount() >= entity.getGlobalLimit()) issues.add("Global redemption limit is exhausted.");
        return issues;
    }

    private boolean providerMappingReady(PromoCodeEntity entity) {
        return entity.getTargetStore() == PromoStore.ALL
                || (hasText(entity.getProviderOfferId()) && hasText(entity.getProviderProductId()));
    }

    private void applyRequest(PromoCodeEntity entity, AdminPromoRequestDto request, String adminEmail) {
        entity.setCode(request.getCode().trim().toUpperCase(Locale.ROOT));
        entity.setName(request.getName().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setDiscountPercent(request.getDiscountPercent());
        entity.setPromoType(request.getPromoType());
        entity.setTargetStore(request.getTargetStore());
        entity.setTargetPlan(request.getTargetPlan());
        entity.setTargetRegion(request.getTargetRegion());
        entity.setTargetProductId(trimToNull(request.getTargetProductId()));
        entity.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        entity.setEligibilityRule(trimToNull(request.getEligibilityRule()));
        entity.setPerUserLimit(request.getPerUserLimit());
        entity.setGlobalLimit(request.getGlobalLimit());
        entity.setMaxUsageCount(request.getGlobalLimit());
        entity.setCampaignKey(trimToNull(request.getCampaignKey()));
        entity.setProviderOfferId(trimToNull(request.getProviderOfferId()));
        entity.setProviderProductId(trimToNull(request.getProviderProductId()));
        entity.setStartAt(request.getStartAt());
        entity.setEndAt(request.getEndAt());
        entity.setExpirationDate(request.getEndAt() == null ? null : request.getEndAt().toLocalDate());
        entity.setUpdatedBy(adminEmail);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void requireDraft(PromoCodeEntity entity) {
        if (entity.getStatus() != PromoStatus.DRAFT) throw new IllegalArgumentException("Only draft promotions can be edited.");
    }

    private PromoCodeEntity requirePromo(Long id) {
        return promoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
    }

    private Map<String, Object> auditValue(PromoCodeEntity entity) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", entity.getCode());
        value.put("status", entity.getStatus());
        value.put("type", entity.getPromoType());
        value.put("store", entity.getTargetStore());
        value.put("targetPlan", entity.getTargetPlan());
        value.put("discountPercent", entity.getDiscountPercent());
        value.put("providerMappingReady", providerMappingReady(entity));
        return value;
    }

    private AdminPromoDto toDto(PromoCodeEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        boolean expired = entity.getStatus() == PromoStatus.ACTIVE && entity.getEndAt() != null && !entity.getEndAt().isAfter(now);
        boolean effectiveActive = entity.isActive() && entity.getStatus() == PromoStatus.ACTIVE && !expired
                && (entity.getStartAt() == null || !entity.getStartAt().isAfter(now));
        PromoStatus effectiveStatus = expired ? PromoStatus.EXPIRED : entity.getStatus();
        return new AdminPromoDto(entity.getId(), entity.getVersion(), entity.getCode(), entity.getName(), entity.getDescription(),
                entity.getDiscountPercent(), effectiveStatus, entity.getPromoType(), effectiveActive, entity.getStartAt(),
                entity.getEndAt(), entity.getTargetPlan(), entity.getTargetProductId(), entity.getTargetStore(), entity.getTargetRegion(),
                entity.getCurrency(), entity.getEligibilityRule(), entity.getPerUserLimit(), entity.getGlobalLimit(), entity.getUsedCount(),
                entity.getCampaignKey(), entity.getProviderOfferId(), entity.getProviderProductId(), providerMappingReady(entity),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedAt(), entity.getDeactivatedReason());
    }

    private AdminPromoRedemptionDto toRedemptionDto(AppliedPromoEntity entity) {
        return new AdminPromoRedemptionDto(entity.getId(), entity.getPromoCode().getId(), entity.getUser().getId(),
                entity.getStatus(), entity.getIdempotencyKey(), entity.getProviderEventId(), entity.getAmountMinor(),
                entity.getCurrency(), entity.getRejectionReason(), entity.getAppliedAt(), entity.getConvertedAt(),
                ENTITLEMENT_GUARDRAIL);
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trimToNull(String value) { return hasText(value) ? value.trim() : null; }
}

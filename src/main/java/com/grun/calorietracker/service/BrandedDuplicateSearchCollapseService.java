package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseDto;
import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseRequestDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateGroupDto;
import com.grun.calorietracker.entity.FoodBrandedDuplicateDecisionEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseAuditEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseMemberEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodBrandedDuplicateDecisionRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseAuditRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseMemberRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class BrandedDuplicateSearchCollapseService {
    private final BrandedProductDuplicateAnalysisService analysisService;
    private final FoodBrandedDuplicateDecisionRepository decisionRepository;
    private final FoodBrandedDuplicateSearchCollapseRepository collapseRepository;
    private final FoodBrandedDuplicateSearchCollapseMemberRepository memberRepository;
    private final FoodBrandedDuplicateSearchCollapseAuditRepository auditRepository;
    private final FoodItemRepository foodItemRepository;

    public BrandedDuplicateSearchCollapseService(
            BrandedProductDuplicateAnalysisService analysisService,
            FoodBrandedDuplicateDecisionRepository decisionRepository,
            FoodBrandedDuplicateSearchCollapseRepository collapseRepository,
            FoodBrandedDuplicateSearchCollapseMemberRepository memberRepository,
            FoodBrandedDuplicateSearchCollapseAuditRepository auditRepository,
            FoodItemRepository foodItemRepository
    ) {
        this.analysisService = analysisService;
        this.decisionRepository = decisionRepository;
        this.collapseRepository = collapseRepository;
        this.memberRepository = memberRepository;
        this.auditRepository = auditRepository;
        this.foodItemRepository = foodItemRepository;
    }

    @Transactional(readOnly = true)
    public BrandedDuplicateSearchCollapseDto get(String brandKey, String nameKey) {
        FoodBrandedDuplicateSearchCollapseEntity collapse = collapseRepository
                .findByBrandKeyAndNameKey(normalizeKey(brandKey), normalizeKey(nameKey))
                .orElseThrow(() -> new ResourceNotFoundException("Branded duplicate search collapse was not found."));
        return toDto(collapse);
    }

    @Transactional
    @CacheEvict(cacheNames = "foodProductSearch", allEntries = true)
    public BrandedDuplicateSearchCollapseDto apply(
            BrandedDuplicateSearchCollapseRequestDto request,
            String performedBy
    ) {
        String brandKey = normalizeKey(request.brandKey());
        String nameKey = normalizeKey(request.nameKey());
        FoodBrandedDuplicateDecisionEntity decision = decisionRepository
                .findByBrandKeyAndNameKey(brandKey, nameKey)
                .orElseThrow(() -> new ResourceNotFoundException("Branded duplicate decision was not found."));
        if (decision.getDecision() != BrandedDuplicateDecision.SURVIVOR_SELECTED) {
            throw new IllegalArgumentException("Only a SURVIVOR_SELECTED decision can be applied to search.");
        }

        BrandedProductDuplicateGroupDto group = analysisService.getExactGroup(brandKey, nameKey);
        if (!Objects.equals(group.candidateFingerprint(), request.candidateFingerprint())
                || !Objects.equals(decision.getCandidateFingerprint(), request.candidateFingerprint())) {
            throw new IllegalArgumentException("Candidate group or reviewed decision is stale. Refresh before applying.");
        }

        Long survivorId = decision.getSurvivorFoodItem().getId();
        List<Long> suppressedIds = group.candidates().stream()
                .map(candidate -> candidate.productId())
                .filter(id -> !Objects.equals(id, survivorId))
                .sorted()
                .toList();
        if (suppressedIds.isEmpty()) {
            throw new IllegalArgumentException("The reviewed group has no non-survivor products to suppress.");
        }

        FoodBrandedDuplicateSearchCollapseEntity collapse = collapseRepository.findByDecisionId(decision.getId())
                .orElseGet(FoodBrandedDuplicateSearchCollapseEntity::new);
        if (collapse.getId() != null && collapse.isActive()) {
            throw new IllegalStateException("This reviewed decision is already active in search.");
        }
        collapse.setDecision(decision);
        collapse.setBrandKey(brandKey);
        collapse.setNameKey(nameKey);
        collapse.setSurvivorFoodItem(decision.getSurvivorFoodItem());
        collapse.setCandidateFingerprint(group.candidateFingerprint());
        collapse.setReason(request.reason().trim());
        collapse.setAppliedBy(actor(performedBy));
        collapse.setAppliedAt(LocalDateTime.now());
        collapse.setActive(true);
        collapse.setRevertedBy(null);
        collapse.setRevertedAt(null);
        collapse.setRevertReason(null);
        FoodBrandedDuplicateSearchCollapseEntity saved = collapseRepository.saveAndFlush(collapse);

        memberRepository.deleteByCollapseId(saved.getId());
        List<FoodItemEntity> suppressedProducts = foodItemRepository.findAllById(suppressedIds);
        if (suppressedProducts.size() != suppressedIds.size()) {
            throw new IllegalStateException("One or more reviewed products no longer exist.");
        }
        memberRepository.saveAll(suppressedProducts.stream().map(product -> {
            FoodBrandedDuplicateSearchCollapseMemberEntity member =
                    new FoodBrandedDuplicateSearchCollapseMemberEntity();
            member.setCollapse(saved);
            member.setSuppressedFoodItem(product);
            return member;
        }).toList());
        auditRepository.save(audit(saved, "APPLY", suppressedIds, request.reason(), performedBy));
        return toDto(saved, suppressedIds);
    }

    @Transactional
    @CacheEvict(cacheNames = "foodProductSearch", allEntries = true)
    public BrandedDuplicateSearchCollapseDto revert(
            String brandKey,
            String nameKey,
            String reason,
            String performedBy
    ) {
        requireReason(reason);
        FoodBrandedDuplicateSearchCollapseEntity collapse = collapseRepository
                .findByBrandKeyAndNameKey(normalizeKey(brandKey), normalizeKey(nameKey))
                .orElseThrow(() -> new ResourceNotFoundException("Branded duplicate search collapse was not found."));
        if (!collapse.isActive()) {
            throw new IllegalStateException("Branded duplicate search collapse is already inactive.");
        }
        List<Long> suppressedIds = memberIds(collapse.getId());
        collapse.setActive(false);
        collapse.setRevertedBy(actor(performedBy));
        collapse.setRevertedAt(LocalDateTime.now());
        collapse.setRevertReason(reason.trim());
        FoodBrandedDuplicateSearchCollapseEntity saved = collapseRepository.saveAndFlush(collapse);
        auditRepository.save(audit(saved, "REVERT", suppressedIds, reason, performedBy));
        return toDto(saved, suppressedIds);
    }

    private BrandedDuplicateSearchCollapseDto toDto(FoodBrandedDuplicateSearchCollapseEntity collapse) {
        return toDto(collapse, memberIds(collapse.getId()));
    }

    private BrandedDuplicateSearchCollapseDto toDto(
            FoodBrandedDuplicateSearchCollapseEntity collapse,
            List<Long> suppressedIds
    ) {
        return new BrandedDuplicateSearchCollapseDto(
                collapse.getId(), collapse.getDecision().getId(), collapse.getBrandKey(), collapse.getNameKey(),
                collapse.getSurvivorFoodItem().getId(), suppressedIds, collapse.getCandidateFingerprint(),
                collapse.getReason(), collapse.getAppliedBy(), collapse.getAppliedAt().toString(), collapse.isActive(),
                collapse.getRevertedBy(), collapse.getRevertedAt() == null ? null : collapse.getRevertedAt().toString(),
                collapse.getRevertReason(), collapse.getVersion()
        );
    }

    private List<Long> memberIds(Long collapseId) {
        return memberRepository.findByCollapseIdOrderBySuppressedFoodItemId(collapseId).stream()
                .map(member -> member.getSuppressedFoodItem().getId())
                .toList();
    }

    private FoodBrandedDuplicateSearchCollapseAuditEntity audit(
            FoodBrandedDuplicateSearchCollapseEntity collapse,
            String action,
            List<Long> suppressedIds,
            String reason,
            String performedBy
    ) {
        FoodBrandedDuplicateSearchCollapseAuditEntity audit =
                new FoodBrandedDuplicateSearchCollapseAuditEntity();
        audit.setDecisionId(collapse.getDecision().getId());
        audit.setBrandKey(collapse.getBrandKey());
        audit.setNameKey(collapse.getNameKey());
        audit.setAction(action);
        audit.setSurvivorFoodItemId(collapse.getSurvivorFoodItem().getId());
        audit.setCandidateFingerprint(collapse.getCandidateFingerprint());
        audit.setSuppressedFoodItemIds(suppressedIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
        audit.setReason(reason.trim());
        audit.setPerformedBy(actor(performedBy));
        return audit;
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Brand and name keys are required.");
        }
        return value.trim();
    }

    private void requireReason(String reason) {
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("A reason of at least 10 characters is required.");
        }
    }

    private String actor(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}

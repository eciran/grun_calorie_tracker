package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FoodCategoryImportResolutionService {
    private static final String REVIEW_REASON =
            "Source category evidence could not be mapped safely to one canonical category.";

    private final FoodCategorySourceMappingRepository mappingRepository;
    private final FoodCategoryResolutionRuleRepository ruleRepository;
    private final FoodItemCategoryRepository itemCategoryRepository;
    private final FoodProductQualityIssueRepository qualityIssueRepository;

    @Transactional
    public ResolutionSummary resolveAfterImport(List<FoodItemEntity> products, String actor) {
        List<FoodItemEntity> eligibleProducts = products == null ? List.of() : products.stream()
                .filter(Objects::nonNull)
                .filter(product -> product.getId() != null)
                .filter(product -> !Boolean.TRUE.equals(product.getIsCustom()))
                .toList();
        if (eligibleProducts.isEmpty()) {
            return new ResolutionSummary(0, 0, 0);
        }

        List<Long> productIds = eligibleProducts.stream().map(FoodItemEntity::getId).distinct().toList();
        List<FoodItemCategoryEntity> existingAssignments = BatchQuerySupport.loadInChunks(
                productIds, itemCategoryRepository::findByFoodItemIdIn);
        Set<Long> productsWithPrimary = new HashSet<>();
        existingAssignments.stream().filter(FoodItemCategoryEntity::isPrimaryCategory)
                .forEach(assignment -> productsWithPrimary.add(assignment.getFoodItem().getId()));

        List<FoodCategorySourceMappingEntity> mappings = mappingRepository.findAllByOrderByPrimaryPriorityAscIdAsc();
        List<FoodCategoryResolutionRuleEntity> rules = ruleRepository.findAllByStatus(FoodCategoryMappingStatus.ACTIVE);
        List<FoodProductQualityIssueEntity> activeIssues = BatchQuerySupport.loadInChunks(
                productIds, qualityIssueRepository::findByFoodItemIdInAndResolvedFalse).stream()
                .filter(issue -> issue.getIssueType() == FoodProductQualityIssue.MISSING_CANONICAL_CATEGORY)
                .toList();
        Map<Long, FoodProductQualityIssueEntity> issueByProduct = new HashMap<>();
        activeIssues.forEach(issue -> issueByProduct.put(issue.getFoodItem().getId(), issue));

        String resolvedActor = actor == null || actor.isBlank() ? "import-category-resolver" : actor.trim();
        LocalDateTime now = LocalDateTime.now();
        List<FoodItemCategoryEntity> assignmentsToSave = new ArrayList<>();
        List<FoodProductQualityIssueEntity> issuesToSave = new ArrayList<>();
        int assignedProducts = 0;
        int preservedProducts = 0;
        int reviewProducts = 0;

        for (FoodItemEntity product : eligibleProducts) {
            if (productsWithPrimary.contains(product.getId())) {
                preservedProducts++;
                resolveIssue(issueByProduct.remove(product.getId()), resolvedActor, now, issuesToSave);
                continue;
            }
            Set<String> tags = normalizeTags(product.getSourceCategoryTags());
            List<CategoryCandidate> candidates = candidates(product, tags, mappings);
            List<CategoryAssignment> resolved = resolveAssignments(product, tags, candidates, rules);
            if (!resolved.isEmpty()) {
                resolved.forEach(assignment -> assignmentsToSave.add(toEntity(
                        product, assignment, resolvedActor, now)));
                assignedProducts++;
                resolveIssue(issueByProduct.remove(product.getId()), resolvedActor, now, issuesToSave);
            } else if (!tags.isEmpty()) {
                reviewProducts++;
                issuesToSave.add(openIssue(product, issueByProduct.remove(product.getId()), now));
            }
        }

        if (!assignmentsToSave.isEmpty()) itemCategoryRepository.saveAll(assignmentsToSave);
        if (!issuesToSave.isEmpty()) qualityIssueRepository.saveAll(issuesToSave);
        return new ResolutionSummary(assignedProducts, preservedProducts, reviewProducts);
    }

    private List<CategoryCandidate> candidates(
            FoodItemEntity product,
            Set<String> tags,
            List<FoodCategorySourceMappingEntity> mappings
    ) {
        Map<Long, CategoryCandidate> byCategory = new LinkedHashMap<>();
        for (FoodCategorySourceMappingEntity mapping : mappings) {
            if (mapping.getDataSource() != product.getDataSource()
                    || !tags.contains(normalizeTag(mapping.getNormalizedSourceTag()))
                    || !matchesMarket(mapping.getMarketRegion(), product.getMarketRegion())
                    || mapping.getCategory() == null || !mapping.getCategory().isActive()) {
                continue;
            }
            CategoryCandidate candidate = new CategoryCandidate(
                    mapping.getCategory(), mapping.getStatus(), mapping.getPrimaryPriority(),
                    mapping.getConfidenceScore());
            byCategory.merge(mapping.getCategory().getId(), candidate, this::mergeCandidate);
        }
        return byCategory.values().stream()
                .sorted(Comparator.comparingInt(CategoryCandidate::priority)
                        .thenComparing(candidate -> candidate.category().getSlug()))
                .toList();
    }

    private CategoryCandidate mergeCandidate(CategoryCandidate left, CategoryCandidate right) {
        FoodCategoryMappingStatus status = left.status() == FoodCategoryMappingStatus.ACTIVE
                || right.status() == FoodCategoryMappingStatus.ACTIVE
                ? FoodCategoryMappingStatus.ACTIVE : FoodCategoryMappingStatus.REVIEW_REQUIRED;
        return new CategoryCandidate(left.category(), status,
                Math.min(left.priority(), right.priority()),
                Math.max(value(left.confidence()), value(right.confidence())));
    }

    private List<CategoryAssignment> resolveAssignments(
            FoodItemEntity product,
            Set<String> tags,
            List<CategoryCandidate> candidates,
            List<FoodCategoryResolutionRuleEntity> rules
    ) {
        if (candidates.isEmpty()) return List.of();
        CategoryCandidate primary = candidates.get(0);
        if (primary.status() == FoodCategoryMappingStatus.ACTIVE) {
            return candidates.stream().filter(candidate -> candidate.status() == FoodCategoryMappingStatus.ACTIVE)
                    .map(candidate -> new CategoryAssignment(candidate.category(), candidate == primary,
                            value(candidate.confidence(), 95))).toList();
        }

        List<FoodCategoryResolutionRuleEntity> matchedRules = rules.stream()
                .filter(rule -> rule.getDataSource() == product.getDataSource())
                .filter(rule -> matchesMarket(rule.getMarketRegion(), product.getMarketRegion()))
                .filter(rule -> primary.category().getSlug().equals(rule.getSourceReviewCategory()))
                .filter(rule -> tags.stream().anyMatch(normalizeTags(rule.getRequiredTags())::contains))
                .filter(rule -> Collections.disjoint(tags, normalizeTags(rule.getExcludedTags())))
                .sorted(Comparator.comparingInt(FoodCategoryResolutionRuleEntity::getPrimaryPriority)
                        .thenComparing(FoodCategoryResolutionRuleEntity::getRuleKey))
                .toList();
        Map<Long, FoodCategoryResolutionRuleEntity> byTarget = new LinkedHashMap<>();
        matchedRules.forEach(rule -> byTarget.putIfAbsent(rule.getTargetCategory().getId(), rule));
        if (byTarget.size() != 1) return List.of();
        FoodCategoryResolutionRuleEntity rule = byTarget.values().iterator().next();
        return List.of(new CategoryAssignment(rule.getTargetCategory(), true,
                value(rule.getConfidenceScore(), 97)));
    }

    private FoodItemCategoryEntity toEntity(
            FoodItemEntity product, CategoryAssignment assignment, String actor, LocalDateTime now) {
        FoodItemCategoryEntity entity = new FoodItemCategoryEntity();
        entity.setFoodItem(product);
        entity.setCategory(assignment.category());
        entity.setPrimaryCategory(assignment.primary());
        entity.setAssignmentSource(FoodCategoryAssignmentSource.IMPORT);
        entity.setConfidenceScore(assignment.confidence());
        entity.setReviewed(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        return entity;
    }

    private FoodProductQualityIssueEntity openIssue(
            FoodItemEntity product, FoodProductQualityIssueEntity existing, LocalDateTime now) {
        FoodProductQualityIssueEntity issue = existing == null ? new FoodProductQualityIssueEntity() : existing;
        issue.setFoodItem(product);
        issue.setIssueType(FoodProductQualityIssue.MISSING_CANONICAL_CATEGORY);
        issue.setIdentifier(product.getSourceKey());
        issue.setReason(REVIEW_REASON);
        if (issue.getFirstDetectedAt() == null) issue.setFirstDetectedAt(now);
        issue.setLastDetectedAt(now);
        issue.setResolved(false);
        issue.setResolvedAt(null);
        issue.setResolvedBy(null);
        return issue;
    }

    private void resolveIssue(FoodProductQualityIssueEntity issue, String actor, LocalDateTime now,
                              List<FoodProductQualityIssueEntity> changes) {
        if (issue == null) return;
        issue.setResolved(true);
        issue.setResolvedAt(now);
        issue.setResolvedBy(actor);
        changes.add(issue);
    }

    private boolean matchesMarket(MarketRegion mappingMarket, MarketRegion productMarket) {
        return mappingMarket == null || mappingMarket == productMarket;
    }

    private Set<String> normalizeTags(Collection<String> tags) {
        if (tags == null) return Set.of();
        Set<String> normalized = new LinkedHashSet<>();
        tags.stream().map(this::normalizeTag).filter(Objects::nonNull).forEach(normalized::add);
        return normalized;
    }

    private String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) return null;
        return tag.trim().toLowerCase(Locale.ROOT);
    }

    private int value(Integer value) { return value(value, 0); }
    private int value(Integer value, int fallback) { return value == null ? fallback : value; }

    private record CategoryCandidate(FoodCategoryEntity category, FoodCategoryMappingStatus status,
                                     int priority, Integer confidence) {}
    private record CategoryAssignment(FoodCategoryEntity category, boolean primary, int confidence) {}
    public record ResolutionSummary(int assignedProducts, int preservedProducts, int reviewProducts) {}
}

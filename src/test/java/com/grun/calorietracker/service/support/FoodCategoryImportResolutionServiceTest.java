package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodCategoryImportResolutionServiceTest {
    @Mock private FoodCategorySourceMappingRepository mappingRepository;
    @Mock private FoodCategoryResolutionRuleRepository ruleRepository;
    @Mock private FoodItemCategoryRepository itemCategoryRepository;
    @Mock private FoodProductQualityIssueRepository qualityIssueRepository;

    private FoodCategoryImportResolutionService service;

    @BeforeEach
    void setUp() {
        service = new FoodCategoryImportResolutionService(mappingRepository, ruleRepository,
                itemCategoryRepository, qualityIssueRepository);
        when(itemCategoryRepository.findByFoodItemIdIn(anyList())).thenReturn(List.of());
        when(qualityIssueRepository.findByFoodItemIdInAndResolvedFalse(anyList())).thenReturn(List.of());
        when(ruleRepository.findAllByStatus(FoodCategoryMappingStatus.ACTIVE)).thenReturn(List.of());
    }

    @Test
    void assignsActiveMappingAndUsesLowestPriorityAsPrimary() {
        FoodItemEntity product = product(1L, "en:fruits", "en:snacks");
        FoodCategoryEntity fruit = category(11L, "fruit");
        FoodCategoryEntity snacks = category(12L, "savoury-snacks");
        when(mappingRepository.findAllByOrderByPrimaryPriorityAscIdAsc()).thenReturn(List.of(
                mapping(fruit, "en:fruits", FoodCategoryMappingStatus.ACTIVE, 510),
                mapping(snacks, "en:snacks", FoodCategoryMappingStatus.ACTIVE, 200)
        ));

        FoodCategoryImportResolutionService.ResolutionSummary summary =
                service.resolveAfterImport(List.of(product), "test-import");

        ArgumentCaptor<List<FoodItemCategoryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemCategoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).filteredOn(FoodItemCategoryEntity::isPrimaryCategory)
                .singleElement().extracting(item -> item.getCategory().getSlug())
                .isEqualTo("savoury-snacks");
        assertThat(summary.assignedProducts()).isEqualTo(1);
        verify(qualityIssueRepository, never()).saveAll(anyList());
    }

    @Test
    void resolvesReviewMappingOnlyWhenApprovedConditionalEvidenceMatches() {
        FoodItemEntity product = product(2L, "en:milks", "en:whole-milks");
        FoodCategoryEntity milk = category(21L, "milk");
        when(mappingRepository.findAllByOrderByPrimaryPriorityAscIdAsc()).thenReturn(List.of(
                mapping(milk, "en:milks", FoodCategoryMappingStatus.REVIEW_REQUIRED, 400)
        ));
        when(ruleRepository.findAllByStatus(FoodCategoryMappingStatus.ACTIVE)).thenReturn(List.of(
                rule(milk, "milk-explicit-subtypes", "milk", Set.of("en:whole-milks"), Set.of())
        ));

        FoodCategoryImportResolutionService.ResolutionSummary summary =
                service.resolveAfterImport(List.of(product), "test-import");

        ArgumentCaptor<List<FoodItemCategoryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemCategoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(assignment -> {
            assertThat(assignment.isPrimaryCategory()).isTrue();
            assertThat(assignment.getAssignmentSource()).isEqualTo(FoodCategoryAssignmentSource.IMPORT);
            assertThat(assignment.getCategory()).isSameAs(milk);
        });
        assertThat(summary.reviewProducts()).isZero();
    }

    @Test
    void excludedConditionalEvidenceKeepsProductInReviewQueue() {
        FoodItemEntity product = product(3L, "en:hot-beverages", "en:teas", "en:iced-teas");
        FoodCategoryEntity hotDrinks = category(31L, "hot-drinks");
        when(mappingRepository.findAllByOrderByPrimaryPriorityAscIdAsc()).thenReturn(List.of(
                mapping(hotDrinks, "en:hot-beverages", FoodCategoryMappingStatus.REVIEW_REQUIRED, 320)
        ));
        when(ruleRepository.findAllByStatus(FoodCategoryMappingStatus.ACTIVE)).thenReturn(List.of(
                rule(hotDrinks, "tea-coffee-explicit-subtypes", "hot-drinks",
                        Set.of("en:teas"), Set.of("en:iced-teas"))
        ));

        FoodCategoryImportResolutionService.ResolutionSummary summary =
                service.resolveAfterImport(List.of(product), "test-import");

        verify(itemCategoryRepository, never()).saveAll(anyList());
        ArgumentCaptor<List<FoodProductQualityIssueEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(qualityIssueRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(issue -> {
            assertThat(issue.getIssueType()).isEqualTo(FoodProductQualityIssue.MISSING_CANONICAL_CATEGORY);
            assertThat(issue.getResolved()).isFalse();
        });
        assertThat(summary.reviewProducts()).isEqualTo(1);
    }

    @Test
    void preservesExistingPrimaryAndResolvesOpenCategoryIssue() {
        FoodItemEntity product = product(4L, "en:fruits");
        FoodItemCategoryEntity existingAssignment = new FoodItemCategoryEntity();
        existingAssignment.setFoodItem(product);
        existingAssignment.setCategory(category(41L, "fruit"));
        existingAssignment.setPrimaryCategory(true);
        FoodProductQualityIssueEntity existingIssue = new FoodProductQualityIssueEntity();
        existingIssue.setFoodItem(product);
        existingIssue.setIssueType(FoodProductQualityIssue.MISSING_CANONICAL_CATEGORY);
        existingIssue.setResolved(false);
        when(itemCategoryRepository.findByFoodItemIdIn(anyList())).thenReturn(List.of(existingAssignment));
        when(qualityIssueRepository.findByFoodItemIdInAndResolvedFalse(anyList())).thenReturn(List.of(existingIssue));
        when(mappingRepository.findAllByOrderByPrimaryPriorityAscIdAsc()).thenReturn(List.of());

        FoodCategoryImportResolutionService.ResolutionSummary summary =
                service.resolveAfterImport(List.of(product), "test-import");

        verify(itemCategoryRepository, never()).saveAll(anyList());
        ArgumentCaptor<List<FoodProductQualityIssueEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(qualityIssueRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(issue -> {
            assertThat(issue.getResolved()).isTrue();
            assertThat(issue.getResolvedBy()).isEqualTo("test-import");
        });
        assertThat(summary.preservedProducts()).isEqualTo(1);
    }

    private FoodItemEntity product(Long id, String... tags) {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(id);
        product.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        product.setMarketRegion(MarketRegion.UK_IE);
        product.setSourceKey("off:" + id);
        product.setSourceCategoryTags(Set.of(tags));
        product.setIsCustom(false);
        return product;
    }

    private FoodCategoryEntity category(Long id, String slug) {
        FoodCategoryEntity category = new FoodCategoryEntity();
        category.setId(id);
        category.setSlug(slug);
        category.setActive(true);
        return category;
    }

    private FoodCategorySourceMappingEntity mapping(
            FoodCategoryEntity category, String tag, FoodCategoryMappingStatus status, int priority) {
        FoodCategorySourceMappingEntity mapping = new FoodCategorySourceMappingEntity();
        mapping.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        mapping.setNormalizedSourceTag(tag);
        mapping.setCategory(category);
        mapping.setStatus(status);
        mapping.setPrimaryPriority(priority);
        mapping.setConfidenceScore(95);
        return mapping;
    }

    private FoodCategoryResolutionRuleEntity rule(
            FoodCategoryEntity target, String key, String reviewCategory,
            Set<String> required, Set<String> excluded) {
        FoodCategoryResolutionRuleEntity rule = new FoodCategoryResolutionRuleEntity();
        rule.setRuleKey(key);
        rule.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        rule.setSourceReviewCategory(reviewCategory);
        rule.setTargetCategory(target);
        rule.setStatus(FoodCategoryMappingStatus.ACTIVE);
        rule.setPrimaryPriority(1000);
        rule.setConfidenceScore(97);
        rule.setRequiredTags(required);
        rule.setExcludedTags(excluded);
        return rule;
    }
}

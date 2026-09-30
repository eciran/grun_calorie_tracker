package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodCategoryEntity;
import com.grun.calorietracker.entity.FoodItemCategoryEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodCategorySourceMappingEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodCategoryAssignmentSource;
import com.grun.calorietracker.enums.FoodCategoryMappingStatus;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FoodCategoryBackfillRepositoryTest {
    @Autowired private FoodItemRepository foodItemRepository;
    @Autowired private FoodCategoryRepository foodCategoryRepository;
    @Autowired private FoodItemCategoryRepository foodItemCategoryRepository;
    @Autowired private FoodCategorySourceMappingRepository sourceMappingRepository;
    @Autowired private FoodProductQualityIssueRepository qualityIssueRepository;

    @Test
    void candidateQueryReturnsOnlyTaggedNonCustomProductsWithoutPrimaryCategory() {
        FoodItemEntity candidate = product("Candidate", false, Set.of("en:milks"));
        FoodItemEntity alreadyAssigned = product("Assigned", false, Set.of("en:fruits"));
        FoodItemEntity custom = product("Custom", true, Set.of("en:milks"));
        FoodItemEntity noEvidence = product("No evidence", false, Set.of());
        FoodItemEntity unmappedEvidence = product("Unmapped", false, Set.of("en:unknown"));
        foodItemRepository.saveAllAndFlush(List.of(
                candidate, alreadyAssigned, custom, noEvidence, unmappedEvidence));

        FoodCategoryEntity category = new FoodCategoryEntity();
        category.setSlug("fruit");
        category.setNameEn("Fruit");
        category.setNameTr("Meyve");
        category.setActive(true);
        category = foodCategoryRepository.saveAndFlush(category);

        FoodCategorySourceMappingEntity mapping = new FoodCategorySourceMappingEntity();
        mapping.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        mapping.setSourceTag("en:milks");
        mapping.setNormalizedSourceTag("en:milks");
        mapping.setCategory(category);
        mapping.setStatus(FoodCategoryMappingStatus.REVIEW_REQUIRED);
        mapping.setPrimaryPriority(400);
        sourceMappingRepository.saveAndFlush(mapping);

        FoodItemCategoryEntity assignment = new FoodItemCategoryEntity();
        assignment.setFoodItem(alreadyAssigned);
        assignment.setCategory(category);
        assignment.setPrimaryCategory(true);
        assignment.setAssignmentSource(FoodCategoryAssignmentSource.IMPORT);
        assignment.setReviewed(false);
        foodItemCategoryRepository.saveAndFlush(assignment);

        List<Long> ids = foodItemRepository.findCategoryResolutionBackfillIds(
                0L, PageRequest.of(0, 100));

        assertThat(ids).containsExactly(candidate.getId());
        assertThat(foodItemRepository.countCategoryResolutionBackfillCandidates()).isEqualTo(1);
        assertThat(foodItemRepository.findLastCategoryResolutionBackfillCandidateId())
                .isEqualTo(candidate.getId());
        assertThat(foodItemRepository.findByIdIn(ids, org.springframework.data.domain.Sort.by("id")))
                .singleElement()
                .satisfies(item -> assertThat(item.getSourceCategoryTags()).containsExactly("en:milks"));

        FoodProductQualityIssueEntity candidateIssue = issue(candidate);
        FoodProductQualityIssueEntity unmappedIssue = issue(unmappedEvidence);
        qualityIssueRepository.saveAllAndFlush(List.of(candidateIssue, unmappedIssue));

        assertThat(qualityIssueRepository.resolveOutOfScopeCanonicalCategoryIssues("scope-test"))
                .isEqualTo(1);
        assertThat(qualityIssueRepository.findById(candidateIssue.getId()).orElseThrow().getResolved())
                .isFalse();
        assertThat(qualityIssueRepository.findById(unmappedIssue.getId()).orElseThrow().getResolved())
                .isTrue();
    }

    private FoodItemEntity product(String name, boolean custom, Set<String> tags) {
        FoodItemEntity product = new FoodItemEntity();
        product.setName(name);
        product.setIsCustom(custom);
        product.setDataSource(FoodDataSource.OPEN_FOOD_FACTS);
        product.setSourceCategoryTags(tags);
        return product;
    }

    private FoodProductQualityIssueEntity issue(FoodItemEntity product) {
        FoodProductQualityIssueEntity issue = new FoodProductQualityIssueEntity();
        issue.setFoodItem(product);
        issue.setIssueType(FoodProductQualityIssue.MISSING_CANONICAL_CATEGORY);
        issue.setIdentifier(product.getName());
        issue.setReason("test");
        issue.setResolved(false);
        return issue;
    }
}

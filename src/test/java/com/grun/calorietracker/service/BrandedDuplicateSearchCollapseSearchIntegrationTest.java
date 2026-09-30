package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodBrandedDuplicateDecisionEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseMemberEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodBrandedDuplicateDecisionRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseMemberRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class BrandedDuplicateSearchCollapseSearchIntegrationTest {

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private FoodItemLocalizationRepository foodItemLocalizationRepository;

    @Autowired
    private FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Autowired
    private FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;

    @Autowired
    private FoodBrandedDuplicateDecisionRepository decisionRepository;

    @Autowired
    private FoodBrandedDuplicateSearchCollapseRepository collapseRepository;

    @Autowired
    private FoodBrandedDuplicateSearchCollapseMemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    private FoodItemServiceImpl foodItemService;

    @BeforeEach
    void setUp() {
        foodItemService = new FoodItemServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                Mockito.mock(OpenFoodFactsService.class),
                Mockito.mock(FoodProductQualityIssueTracker.class),
                Mockito.mock(FoodProductEvidenceService.class),
                Mockito.mock(CatalogPublicationService.class)
        );
    }

    @Test
    void activeCollapseHidesOnlyReviewedMembersAndRevertRestoresSearchResults() {
        FoodItemEntity survivor = product("Wispa Chocolate Bar", "7622210102221");
        FoodItemEntity duplicateOne = product("Wispa Chocolate Bar", "7622210102238");
        FoodItemEntity duplicateTwo = product("Wispa Chocolate Bar", "7622210102245");
        foodItemRepository.saveAllAndFlush(List.of(survivor, duplicateOne, duplicateTwo));

        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("wispa");
        criteria.setMarketRegion(MarketRegion.UK_IE);

        assertEquals(3, foodItemService.searchFoodItems(criteria, 0, 25).getContent().size());

        FoodBrandedDuplicateDecisionEntity decision = new FoodBrandedDuplicateDecisionEntity();
        decision.setBrandKey("cadbury");
        decision.setNameKey("wispa chocolate bar");
        decision.setDecision(BrandedDuplicateDecision.SURVIVOR_SELECTED);
        decision.setSurvivorFoodItem(survivor);
        decision.setCandidateFingerprint("integration-test-fingerprint");
        decision.setReason("Verified integration-test cohort");
        decision.setReviewedBy("integration-test@grun.local");
        decision = decisionRepository.saveAndFlush(decision);

        FoodBrandedDuplicateSearchCollapseEntity collapse = new FoodBrandedDuplicateSearchCollapseEntity();
        collapse.setDecision(decision);
        collapse.setBrandKey(decision.getBrandKey());
        collapse.setNameKey(decision.getNameKey());
        collapse.setSurvivorFoodItem(survivor);
        collapse.setCandidateFingerprint(decision.getCandidateFingerprint());
        collapse.setReason("Search-only duplicate collapse integration proof");
        collapse.setAppliedBy("integration-test@grun.local");
        collapse.setActive(true);
        collapse = collapseRepository.saveAndFlush(collapse);

        memberRepository.saveAllAndFlush(List.of(
                member(collapse, duplicateOne),
                member(collapse, duplicateTwo)
        ));
        entityManager.clear();

        var collapsedResults = foodItemService.searchFoodItems(criteria, 0, 25).getContent();
        assertEquals(1, collapsedResults.size());
        assertEquals(survivor.getId(), collapsedResults.get(0).getId());
        assertNotNull(foodItemService.getFoodItemById(duplicateOne.getId(), null));

        FoodBrandedDuplicateSearchCollapseEntity activeCollapse = collapseRepository.findById(collapse.getId()).orElseThrow();
        activeCollapse.setActive(false);
        activeCollapse.setRevertedBy("integration-test@grun.local");
        activeCollapse.setRevertReason("Rollback proof");
        collapseRepository.saveAndFlush(activeCollapse);
        entityManager.clear();

        assertEquals(3, foodItemService.searchFoodItems(criteria, 0, 25).getContent().size());
    }

    private FoodBrandedDuplicateSearchCollapseMemberEntity member(
            FoodBrandedDuplicateSearchCollapseEntity collapse,
            FoodItemEntity suppressedProduct
    ) {
        FoodBrandedDuplicateSearchCollapseMemberEntity member = new FoodBrandedDuplicateSearchCollapseMemberEntity();
        member.setCollapse(collapse);
        member.setSuppressedFoodItem(suppressedProduct);
        return member;
    }

    private FoodItemEntity product(String name, String barcode) {
        FoodItemEntity product = new FoodItemEntity();
        product.setName(name);
        product.setDisplayName(name);
        product.setBrand("Cadbury");
        product.setBarcode(barcode);
        product.setNormalizedBarcode(barcode);
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setMarketRegion(MarketRegion.UK_IE);
        product.setCalories(215.0);
        product.setProtein(3.2);
        product.setCarbs(58.0);
        product.setFat(17.0);
        product.setQualityScore(90);
        product.setUsageCount(0L);
        product.setIsCustom(false);
        return product;
    }
}

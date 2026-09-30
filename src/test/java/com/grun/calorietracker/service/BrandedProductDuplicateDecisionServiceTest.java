package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateDecisionRequestDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateGroupDto;
import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import com.grun.calorietracker.repository.FoodBrandedDuplicateDecisionAuditRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateDecisionRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandedProductDuplicateDecisionServiceTest {
    private static final String FINGERPRINT = "a".repeat(64);

    @Mock private BrandedProductDuplicateAnalysisService analysisService;
    @Mock private FoodBrandedDuplicateDecisionRepository decisionRepository;
    @Mock private FoodBrandedDuplicateDecisionAuditRepository auditRepository;
    @Mock private FoodItemRepository foodItemRepository;
    @Mock private FoodBrandedDuplicateSearchCollapseRepository collapseRepository;

    private BrandedProductDuplicateDecisionService service;

    @BeforeEach
    void setUp() {
        service = new BrandedProductDuplicateDecisionService(
                analysisService, decisionRepository, auditRepository, foodItemRepository, collapseRepository
        );
        when(analysisService.getExactGroup("brand:1", "wispa")).thenReturn(group());
    }

    @Test
    void rejectsStaleCandidateFingerprintBeforeWriting() {
        var request = new BrandedProductDuplicateDecisionRequestDto(
                "brand:1", "wispa", BrandedDuplicateDecision.KEEP_SEPARATE,
                null, "Confirmed product variants.", "b".repeat(64)
        );

        assertThatThrownBy(() -> service.decide(request, "admin@grun.app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Candidate group changed");
        verifyNoInteractions(decisionRepository, auditRepository, foodItemRepository);
    }

    @Test
    void rejectsSurvivorForKeepSeparateDecision() {
        var request = new BrandedProductDuplicateDecisionRequestDto(
                "brand:1", "wispa", BrandedDuplicateDecision.KEEP_SEPARATE,
                11L, "Confirmed product variants.", FINGERPRINT
        );

        assertThatThrownBy(() -> service.decide(request, "admin@grun.app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only valid for SURVIVOR_SELECTED");
        verifyNoInteractions(decisionRepository, auditRepository, foodItemRepository);
    }

    @Test
    void rejectsSurvivorOutsideCurrentCandidateGroup() {
        var request = new BrandedProductDuplicateDecisionRequestDto(
                "brand:1", "wispa", BrandedDuplicateDecision.SURVIVOR_SELECTED,
                99L, "Verified duplicate identity evidence.", FINGERPRINT
        );

        assertThatThrownBy(() -> service.decide(request, "admin@grun.app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must belong to the current candidate group");
        verifyNoInteractions(decisionRepository, auditRepository, foodItemRepository);
    }

    private BrandedProductDuplicateGroupDto group() {
        var first = new BrandedProductDuplicateCandidateDto(
                11L, "Wispa", "7622210100300", "off:7622210100300", "UK_IE", null,
                36.0, "g", 550.0, 7.0, 58.0, 31.0, 90, "VERIFIED", "OPEN_FOOD_FACTS"
        );
        var second = new BrandedProductDuplicateCandidateDto(
                12L, "Wispa", "7622201809700", "off:7622201809700", "UK_IE", null,
                27.9, "g", 545.0, 7.1, 57.0, 30.0, 88, "VERIFIED", "OPEN_FOOD_FACTS"
        );
        return new BrandedProductDuplicateGroupDto(
                "brand:1", "wispa", "Cadbury", "Wispa", 2, 2, 0,
                1, 0, 2, 0, 2, 0, "REVIEW_DIFFERENT_GTIN", true,
                FINGERPRINT, null, false, null, List.of(first, second)
        );
    }
}

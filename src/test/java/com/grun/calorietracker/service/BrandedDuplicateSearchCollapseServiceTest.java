package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseRequestDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateGroupDto;
import com.grun.calorietracker.entity.FoodBrandedDuplicateDecisionEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseMemberEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import com.grun.calorietracker.repository.FoodBrandedDuplicateDecisionRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseAuditRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseMemberRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandedDuplicateSearchCollapseServiceTest {
    private static final String FINGERPRINT = "a".repeat(64);

    @Mock private BrandedProductDuplicateAnalysisService analysisService;
    @Mock private FoodBrandedDuplicateDecisionRepository decisionRepository;
    @Mock private FoodBrandedDuplicateSearchCollapseRepository collapseRepository;
    @Mock private FoodBrandedDuplicateSearchCollapseMemberRepository memberRepository;
    @Mock private FoodBrandedDuplicateSearchCollapseAuditRepository auditRepository;
    @Mock private FoodItemRepository foodItemRepository;

    private BrandedDuplicateSearchCollapseService service;
    private FoodBrandedDuplicateDecisionEntity decision;

    @BeforeEach
    void setUp() {
        service = new BrandedDuplicateSearchCollapseService(
                analysisService, decisionRepository, collapseRepository, memberRepository,
                auditRepository, foodItemRepository
        );
        FoodItemEntity survivor = product(11L);
        decision = new FoodBrandedDuplicateDecisionEntity();
        decision.setId(41L);
        decision.setBrandKey("cadbury");
        decision.setNameKey("wispa");
        decision.setDecision(BrandedDuplicateDecision.SURVIVOR_SELECTED);
        decision.setSurvivorFoodItem(survivor);
        decision.setCandidateFingerprint(FINGERPRINT);
    }

    @Test
    void appliesOnlyNonSurvivorsAndKeepsCatalogRows() {
        when(decisionRepository.findByBrandKeyAndNameKey("cadbury", "wispa"))
                .thenReturn(Optional.of(decision));
        when(analysisService.getExactGroup("cadbury", "wispa")).thenReturn(group(FINGERPRINT));
        when(collapseRepository.findByDecisionId(41L)).thenReturn(Optional.empty());
        when(collapseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            FoodBrandedDuplicateSearchCollapseEntity entity = invocation.getArgument(0);
            entity.setId(71L);
            entity.setVersion(0L);
            return entity;
        });
        when(foodItemRepository.findAllById(List.of(12L, 13L)))
                .thenReturn(List.of(product(12L), product(13L)));

        var result = service.apply(new BrandedDuplicateSearchCollapseRequestDto(
                "cadbury", "wispa", FINGERPRINT, "Confirmed duplicate catalog entries."
        ), "admin@grun.app");

        assertThat(result.survivorProductId()).isEqualTo(11L);
        assertThat(result.suppressedProductIds()).containsExactly(12L, 13L);
        assertThat(result.active()).isTrue();
        ArgumentCaptor<List<FoodBrandedDuplicateSearchCollapseMemberEntity>> members =
                ArgumentCaptor.forClass(List.class);
        verify(memberRepository).saveAll(members.capture());
        assertThat(members.getValue()).extracting(member -> member.getSuppressedFoodItem().getId())
                .containsExactly(12L, 13L);
        verify(foodItemRepository, never()).deleteById(anyLong());
        verify(auditRepository).save(any());
    }

    @Test
    void rejectsStaleDecisionBeforeCreatingCollapse() {
        when(decisionRepository.findByBrandKeyAndNameKey("cadbury", "wispa"))
                .thenReturn(Optional.of(decision));
        when(analysisService.getExactGroup("cadbury", "wispa")).thenReturn(group("b".repeat(64)));

        assertThatThrownBy(() -> service.apply(new BrandedDuplicateSearchCollapseRequestDto(
                "cadbury", "wispa", FINGERPRINT, "Confirmed duplicate catalog entries."
        ), "admin@grun.app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stale");

        verify(collapseRepository, never()).saveAndFlush(any());
        verify(memberRepository, never()).saveAll(any());
    }

    @Test
    void revertKeepsMembersForAuditAndRestoresSearchVisibility() {
        FoodBrandedDuplicateSearchCollapseEntity collapse = collapse();
        when(collapseRepository.findByBrandKeyAndNameKey("cadbury", "wispa"))
                .thenReturn(Optional.of(collapse));
        FoodBrandedDuplicateSearchCollapseMemberEntity member =
                new FoodBrandedDuplicateSearchCollapseMemberEntity();
        member.setCollapse(collapse);
        member.setSuppressedFoodItem(product(12L));
        when(memberRepository.findByCollapseIdOrderBySuppressedFoodItemId(71L))
                .thenReturn(List.of(member));
        when(collapseRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.revert(
                "cadbury", "wispa", "Source evidence changed after review.", "admin@grun.app"
        );

        assertThat(result.active()).isFalse();
        assertThat(result.suppressedProductIds()).containsExactly(12L);
        assertThat(result.revertedBy()).isEqualTo("admin@grun.app");
        verify(memberRepository, never()).deleteByCollapseId(any());
        verify(auditRepository).save(any());
    }

    private FoodBrandedDuplicateSearchCollapseEntity collapse() {
        FoodBrandedDuplicateSearchCollapseEntity collapse = new FoodBrandedDuplicateSearchCollapseEntity();
        collapse.setId(71L);
        collapse.setDecision(decision);
        collapse.setBrandKey("cadbury");
        collapse.setNameKey("wispa");
        collapse.setSurvivorFoodItem(decision.getSurvivorFoodItem());
        collapse.setCandidateFingerprint(FINGERPRINT);
        collapse.setReason("Confirmed duplicate catalog entries.");
        collapse.setAppliedBy("admin@grun.app");
        collapse.setAppliedAt(LocalDateTime.now());
        collapse.setActive(true);
        collapse.setVersion(0L);
        return collapse;
    }

    private BrandedProductDuplicateGroupDto group(String fingerprint) {
        return new BrandedProductDuplicateGroupDto(
                "cadbury", "wispa", "Cadbury", "Wispa", 3, 3, 0, 1, 0,
                3, 0, 3, 0, "REVIEW_DIFFERENT_GTIN", false, fingerprint, null, false, null,
                List.of(candidate(11L), candidate(12L), candidate(13L))
        );
    }

    private BrandedProductDuplicateCandidateDto candidate(Long id) {
        return new BrandedProductDuplicateCandidateDto(
                id, "Wispa", "barcode-" + id, "off:" + id, "UK_IE", null,
                36.0, "g", 550.0, 7.0, 58.0, 31.0, 90, "VERIFIED", "OPEN_FOOD_FACTS"
        );
    }

    private FoodItemEntity product(Long id) {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(id);
        return product;
    }
}

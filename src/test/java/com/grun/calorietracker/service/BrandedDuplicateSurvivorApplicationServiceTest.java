package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseDto;
import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseRequestDto;
import com.grun.calorietracker.dto.BrandedDuplicateSurvivorApplyRequestDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateDecisionRequestDto;
import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandedDuplicateSurvivorApplicationServiceTest {
    private static final String FINGERPRINT = "a".repeat(64);

    @Mock private BrandedProductDuplicateDecisionService decisionService;
    @Mock private BrandedDuplicateSearchCollapseService collapseService;

    @Test
    void recordsSurvivorBeforeApplyingTheSameFingerprintToSearch() {
        BrandedDuplicateSurvivorApplicationService service =
                new BrandedDuplicateSurvivorApplicationService(decisionService, collapseService);
        BrandedDuplicateSearchCollapseDto expected = new BrandedDuplicateSearchCollapseDto(
                1L, 2L, "brand:4", "chickenkorma", 11L, List.of(12L),
                FINGERPRINT, "reason", "admin", "2026-11-02T00:00:00", true,
                null, null, null, 0L
        );
        when(collapseService.apply(any(), any())).thenReturn(expected);

        var result = service.selectAndApply(new BrandedDuplicateSurvivorApplyRequestDto(
                "brand:4", "chickenkorma", 11L, FINGERPRINT
        ), "admin@grun.app");

        ArgumentCaptor<BrandedProductDuplicateDecisionRequestDto> decision =
                ArgumentCaptor.forClass(BrandedProductDuplicateDecisionRequestDto.class);
        ArgumentCaptor<BrandedDuplicateSearchCollapseRequestDto> collapse =
                ArgumentCaptor.forClass(BrandedDuplicateSearchCollapseRequestDto.class);
        var ordered = inOrder(decisionService, collapseService);
        ordered.verify(decisionService).decide(decision.capture(), eq("admin@grun.app"));
        ordered.verify(collapseService).apply(collapse.capture(), eq("admin@grun.app"));

        assertThat(result).isSameAs(expected);
        assertThat(decision.getValue().decision()).isEqualTo(BrandedDuplicateDecision.SURVIVOR_SELECTED);
        assertThat(decision.getValue().survivorProductId()).isEqualTo(11L);
        assertThat(decision.getValue().reason()).contains("#11", "applied it to search");
        assertThat(collapse.getValue().candidateFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(collapse.getValue().reason()).isEqualTo(decision.getValue().reason());
    }
}

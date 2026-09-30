package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrandedDuplicateFingerprintTest {
    @Test
    void isStableAcrossCandidateOrderingAndChangesWhenIdentityEvidenceChanges() {
        var first = candidate(2L, "222", "source:2");
        var second = candidate(1L, "111", "source:1");

        String forward = BrandedDuplicateFingerprint.fromCandidates(List.of(first, second));
        String reversed = BrandedDuplicateFingerprint.fromCandidates(List.of(second, first));
        String changed = BrandedDuplicateFingerprint.fromCandidates(
                List.of(candidate(1L, "999", "source:1"), first)
        );

        assertThat(forward).hasSize(64).isEqualTo(reversed).isNotEqualTo(changed);
    }

    private BrandedProductDuplicateCandidateDto candidate(Long id, String barcode, String sourceKey) {
        return new BrandedProductDuplicateCandidateDto(
                id, "Product", barcode, sourceKey, "UK_IE", null,
                100.0, "g", 100.0, 1.0, 2.0, 3.0,
                80, "VERIFIED", "OPEN_FOOD_FACTS"
        );
    }
}

package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void changesWhenProfileChangesWithoutAnyIdentityChange() {
        var before = candidate(1L, "111", "source:1");
        var after = new BrandedProductDuplicateCandidateDto(
                1L, "Product", "111", "source:1", "UK_IE", null,
                100.0, "g", 100.0, 1.0, 2.0, 3.04,
                80, "VERIFIED", "OPEN_FOOD_FACTS"
        );
        assertThat(BrandedDuplicateFingerprint.fromCandidates(List.of(before)))
                .isNotEqualTo(BrandedDuplicateFingerprint.fromCandidates(List.of(after)));
    }

    @Test
    void bindsFullProfileEvidenceAndPreservesOrderingIndependence() {
        var first = candidate(1L, "111", "source:1");
        var second = candidate(2L, "222", "source:2");
        Map<Long, String> profiles = Map.of(1L, "a".repeat(64), 2L, "b".repeat(64));
        String before = BrandedDuplicateFingerprint.fromCandidates(List.of(first, second), profiles);
        assertThat(before).isEqualTo(BrandedDuplicateFingerprint.fromCandidates(List.of(second, first), profiles));
        assertThat(before).isNotEqualTo(BrandedDuplicateFingerprint.fromCandidates(List.of(first, second),
                Map.of(1L, "c".repeat(64), 2L, "b".repeat(64))));
        assertThat(before).isNotEqualTo(BrandedDuplicateFingerprint.fromCandidates(List.of(first, second)));
    }

    @Test
    void refusesIncompleteMalformedOrUnrelatedProfileEvidence() {
        var candidate = candidate(1L, "111", "source:1");
        for (Map<Long, String> profiles : List.of(Map.<Long, String>of(), Map.of(2L, "a".repeat(64)),
                Map.of(1L, "bad"), Map.of(1L, "A".repeat(64)), Map.of(1L, "a".repeat(64), 2L, "b".repeat(64)))) {
            assertThatThrownBy(() -> BrandedDuplicateFingerprint.fromCandidates(List.of(candidate), profiles))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsDuplicateIdentitiesAndKeepsNullDistinctFromEmpty() {
        var candidate = candidate(1L, "111", "source:1");
        assertThatThrownBy(() -> BrandedDuplicateFingerprint.fromCandidates(List.of(candidate, candidate)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(BrandedDuplicateFingerprint.fromCandidates(List.of(candidate(1L, null, "source:1"))))
                .isNotEqualTo(BrandedDuplicateFingerprint.fromCandidates(List.of(candidate(1L, "", "source:1"))));
    }
}

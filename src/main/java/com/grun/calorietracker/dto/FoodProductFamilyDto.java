package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;

import java.time.LocalDateTime;
import java.util.List;

public record FoodProductFamilyDto(
        Long id,
        String familyKey,
        String displayName,
        Long representativeProductId,
        boolean active,
        String evidenceReason,
        String appliedBy,
        LocalDateTime appliedAt,
        String previewFingerprint,
        List<String> acknowledgedWarnings,
        String revertedBy,
        LocalDateTime revertedAt,
        String revertReason,
        List<Member> members
) {
    public record Member(
            Long productId,
            String barcode,
            String displayName,
            Double packageQuantity,
            FoodPortionUnit packageUnit,
            String packageLabel,
            boolean defaultVariant,
            boolean active
    ) {
    }
}

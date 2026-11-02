package com.grun.calorietracker.dto;

public record FoodProductFamilyMembershipDto(
        Long productId,
        Long familyId,
        String familyKey,
        String displayName
) {
}

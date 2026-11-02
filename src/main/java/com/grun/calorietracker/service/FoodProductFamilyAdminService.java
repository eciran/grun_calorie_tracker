package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductFamilyDto;
import com.grun.calorietracker.dto.FoodProductFamilyPreviewDto;
import com.grun.calorietracker.dto.FoodProductFamilyRequestDto;
import com.grun.calorietracker.dto.FoodProductFamilyPageDto;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import com.grun.calorietracker.dto.FoodProductFamilyMembershipDto;

public interface FoodProductFamilyAdminService {
    FoodProductFamilyPreviewDto preview(FoodProductFamilyRequestDto request);

    FoodProductFamilyDto apply(FoodProductFamilyRequestDto request, String actor);

    FoodProductFamilyDto apply(FoodProductFamilyRequestDto request, String previewFingerprint,
                               java.util.List<String> acknowledgedWarnings, String actor);

    FoodProductFamilyPageDto list(String query, Boolean active, Pageable pageable);

    FoodProductFamilyDto get(Long familyId);

    FoodProductFamilyPreviewDto previewAddition(Long familyId,
                                                com.grun.calorietracker.dto.FoodProductFamilyMemberRequestDto member,
                                                String reason);

    FoodProductFamilyDto addMember(Long familyId,
                                   com.grun.calorietracker.dto.FoodProductFamilyMemberRequestDto member,
                                   String reason, String previewFingerprint,
                                   java.util.List<String> acknowledgedWarnings, String actor);

    java.util.List<FoodProductFamilyMembershipDto> memberships(Collection<Long> productIds);

    FoodProductFamilyDto deactivate(Long familyId, String reason, String actor);
}

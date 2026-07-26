package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated user's profile contract without admin-only account fields.")
public class MyProfileDto {
    private Long id;
    private String email;
    private String name;
    private String avatarUrl;
    private ProfileBodyDto body;
    private ProfilePreferencesDto preferences;
    private ProfileSecurityDto security;
    private Boolean goalRecalculationRecommended;
    private String goalRecalculationReason;
}

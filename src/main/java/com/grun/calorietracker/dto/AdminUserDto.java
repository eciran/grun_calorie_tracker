package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.CountryCode;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin-only user management view.")
public class AdminUserDto {
    private Long id;
    private String email;
    private String name;
    private UserRole role;
    private Boolean emailVerified;
    private Boolean passwordSet;
    private Boolean accountEnabled;
    private Boolean accountLocked;
    private MarketRegion marketRegion;
    private CountryCode countryCode;
    private PreferredLanguage preferredLanguage;
    private String timeZone;
    private Instant createdAt;
    private Instant emailVerifiedAt;
    private Instant lastLoginAt;
    private Instant lastActiveAt;
}

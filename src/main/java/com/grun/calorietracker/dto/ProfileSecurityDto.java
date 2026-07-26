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
@Schema(description = "User-visible account security state. Admin enforcement fields are intentionally excluded.")
public class ProfileSecurityDto {
    private Boolean emailVerified;
    private Boolean passwordSet;
}

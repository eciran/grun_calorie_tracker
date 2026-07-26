package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Body metrics owned by the authenticated user's profile.")
public class ProfileBodyDto {
    private Integer age;
    private LocalDate birthDate;
    private String gender;
    private Double height;
    private Double weight;
    private Double bmi;
    private Double bodyFat;
}

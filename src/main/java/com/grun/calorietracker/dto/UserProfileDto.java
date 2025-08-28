package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String email;
    private String name;
    private Integer age;
    private String gender;
    private Double height;
    private Double weight;
    private Double bmi;
    private Double bodyFat;

}

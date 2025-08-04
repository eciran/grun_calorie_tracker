package com.grun.calorietracker.dto;

import lombok.Data;


@Data
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

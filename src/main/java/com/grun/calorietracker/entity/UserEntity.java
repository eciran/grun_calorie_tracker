package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    private String password;

    private Integer age;

    private String gender;

    private Double height;

    private Double weight;

    private Double bodyFatPercentage;

    private Double bmi;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    private MarketRegion marketRegion;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = true;

    @Column(name = "password_set", nullable = false)
    private Boolean passwordSet = true;

}

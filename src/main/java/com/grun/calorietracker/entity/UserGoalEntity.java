package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.GoalCalculationMode;
import com.grun.calorietracker.enums.GoalControlledStrategy;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "goals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private Double targetWeight;
    private Integer dailyCalorieGoal;
    private Double dailyProteinGoal;
    private Double dailyFatGoal;
    private Double dailyCarbGoal;

    private Double weeklyWeightChangeTargetKg;

    @Enumerated(EnumType.STRING)
    private GoalType goalType;

    @Enumerated(EnumType.STRING)
    private ActivityLevel activityLevel;

    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private GoalCalculationMode calculationMode;

    @Enumerated(EnumType.STRING)
    private GoalControlledStrategy controlledStrategy;

    private String lockedMacros;
    private Integer macroCalculatedCalories;
    private Integer automaticReferenceCalories;
    private Double automaticReferenceProtein;
    private Double automaticReferenceCarbs;
    private Double automaticReferenceFat;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private LocalDate effectiveLocalDate;
    private String effectiveTimeZone;

    @Version
    private Long version;


}

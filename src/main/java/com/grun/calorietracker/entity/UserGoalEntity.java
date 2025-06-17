package com.grun.calorietracker.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

    @OneToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private Double targetWeight;

    private Integer dailyCalorieGoal;
    private Double dailyProteinGoal;
    private Double dailyFatGoal;
    private Double dailyCarbGoal;

    private LocalDateTime createdAt;
}

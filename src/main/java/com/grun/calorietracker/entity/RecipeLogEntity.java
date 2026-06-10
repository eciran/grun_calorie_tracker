package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recipe_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private RecipeEntity recipe;

    private Double servingGrams;
    private Double servingCount;
    private String mealType;
    private LocalDateTime logDate;

    private Double snapshotCalories;
    private Double snapshotProtein;
    private Double snapshotCarbs;
    private Double snapshotFat;
    private Double snapshotFiber;
    private Double snapshotSugar;
    private Double snapshotSaturatedFat;
    private Double snapshotSodium;
    private Double snapshotPotassium;
    private Double snapshotCholesterol;
    private Double snapshotCalcium;
    private Double snapshotIron;
    private Double snapshotMagnesium;
    private Double snapshotZinc;

    @Column(name = "snapshot_vitamin_a")
    private Double snapshotVitaminA;

    @Column(name = "snapshot_vitamin_c")
    private Double snapshotVitaminC;

    @Column(name = "snapshot_vitamin_d")
    private Double snapshotVitaminD;

    @Column(name = "snapshot_vitamin_e")
    private Double snapshotVitaminE;

    @Column(name = "snapshot_vitamin_b12")
    private Double snapshotVitaminB12;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

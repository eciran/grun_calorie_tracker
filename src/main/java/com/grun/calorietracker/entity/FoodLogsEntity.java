package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodLogSource;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "food_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodLogsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "food_id")
    private FoodItemEntity foodItem;

    private String displayName;

    private Boolean estimated = false;

    private Long aiRequestId;

    private Double aiConfidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serving_option_id")
    private FoodItemServingOptionEntity servingOption;

    private Double portionSize;

    @Enumerated(EnumType.STRING)
    private FoodPortionUnit portionUnit;

    private Double normalizedPortionGrams;

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

    @Enumerated(EnumType.STRING)
    private FoodLogSource source;

    private String mealType;

    private LocalDateTime logDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

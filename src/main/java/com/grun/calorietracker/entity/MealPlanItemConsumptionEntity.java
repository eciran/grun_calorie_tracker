package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanItemConsumptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "meal_plan_item_consumptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealPlanItemConsumptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_plan_item_id", nullable = false)
    private MealPlanItemEntity mealPlanItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_log_id")
    private FoodLogsEntity foodLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_log_id")
    private RecipeLogEntity recipeLog;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MealPlanItemConsumptionStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "planned_quantity", nullable = false)
    private Double plannedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "planned_unit", nullable = false, length = 30)
    private FoodPortionUnit plannedUnit;

    @Column(name = "consumed_quantity")
    private Double consumedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "consumed_unit", length = 30)
    private FoodPortionUnit consumedUnit;

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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

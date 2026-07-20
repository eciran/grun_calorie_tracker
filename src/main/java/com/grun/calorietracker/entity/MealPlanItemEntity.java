package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanItemType;
import com.grun.calorietracker.enums.MealPlanItemLinkState;
import com.grun.calorietracker.enums.MealPlanWorkoutRelation;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "meal_plan_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealPlanItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_plan_id", nullable = false)
    private MealPlanEntity mealPlan;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "meal_type", nullable = false, length = 30)
    private String mealType;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private MealPlanItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id")
    private FoodItemEntity foodItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private RecipeEntity recipe;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_state", nullable = false, length = 30)
    private MealPlanItemLinkState linkState = MealPlanItemLinkState.NONE;

    @Column(name = "portion_size")
    private Double portionSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "portion_unit", length = 30)
    private FoodPortionUnit portionUnit;

    @Column(name = "serving_count")
    private Double servingCount;

    @Column(name = "item_order")
    private Integer itemOrder;

    @Column(name = "snapshot_name", length = 255)
    private String snapshotName;

    @Column(name = "snapshot_description", columnDefinition = "TEXT")
    private String snapshotDescription;

    @Column(name = "short_preparation_state", length = 120)
    private String shortPreparationState;

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

    @Column(name = "allergens_payload", columnDefinition = "TEXT")
    private String allergensPayload;

    @Column(name = "warnings_payload", columnDefinition = "TEXT")
    private String warningsPayload;

    @Column(name = "assumptions_payload", columnDefinition = "TEXT")
    private String assumptionsPayload;

    @Column(name = "snapshot_payload", columnDefinition = "TEXT")
    private String snapshotPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "workout_relation", nullable = false, length = 30)
    private MealPlanWorkoutRelation workoutRelation = MealPlanWorkoutRelation.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_ai_request_id")
    private AiRequestHistoryEntity sourceAiRequest;

    @Column(name = "schema_version", length = 50)
    private String schemaVersion;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;
}

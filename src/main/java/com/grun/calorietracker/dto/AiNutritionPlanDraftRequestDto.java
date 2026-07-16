package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grun.calorietracker.enums.NutritionPlanGenerationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Controlled AI nutrition-plan request. The result remains a draft until the user confirms it.")
public class AiNutritionPlanDraftRequestDto {

    @NotNull
    private NutritionPlanGenerationMode generationMode;

    @Schema(description = "Required only for WORKOUT_ALIGNED mode. The referenced plan must have a user-confirmed schedule.")
    private Long workoutPlanId;

    @NotNull
    @FutureOrPresent
    private LocalDate startDate;

    @NotNull
    @Min(1)
    @Max(7)
    private Integer dayCount;

    @NotNull
    @Min(2)
    @Max(6)
    private Integer mealsPerDay;

    @Size(max = 6)
    private List<LocalTime> preferredMealTimes = new ArrayList<>();

    @Size(max = 30)
    private List<@Size(max = 80) String> excludedFoods = new ArrayList<>();

    @Size(max = 20)
    private List<@Size(max = 80) String> dietaryPreferences = new ArrayList<>();

    @Pattern(regexp = "(?i)LOW|MODERATE|FLEXIBLE")
    private String budgetPreference;

    @Pattern(regexp = "(?i)QUICK|MEDIUM|FLEXIBLE")
    private String preparationTimePreference;

    private Boolean includeRecipeSuggestions = false;

    @Size(max = 12)
    private String language;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Object trustedUserContext;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Backend-owned persistent allergen codes. Client values are ignored.")
    private List<String> trustedAllergens = new ArrayList<>();

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private MealPlanNutritionSnapshotDto trustedDailyTarget;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Backend-built context from the owned active workout plan schedule. Client values are ignored.")
    private WorkoutNutritionContextDto trustedWorkoutContext;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Backend-only feedback used for one controlled provider correction retry.")
    private String trustedValidationFeedback;
}
package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.CountryCode;
import com.grun.calorietracker.enums.DietaryPreference;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.OnboardingStatus;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UnitPreference;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.WeeklyWorkoutFrequency;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "onboarding_drafts",
        uniqueConstraints = @UniqueConstraint(name = "uk_onboarding_drafts_user", columnNames = "user_id")
)
@Data
public class OnboardingDraftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_onboarding_drafts_user")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnboardingStatus status = OnboardingStatus.IN_PROGRESS;

    @Column(length = 120)
    private String name;
    private Integer age;
    @Column(name = "birth_date")
    private LocalDate birthDate;
    @Column(length = 30)
    private String gender;
    private Double height;
    private Double weight;
    @Column(name = "body_fat_percentage")
    private Double bodyFatPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_region", length = 20)
    private MarketRegion marketRegion;

    @Enumerated(EnumType.STRING)
    @Column(name = "country_code", length = 2)
    private CountryCode countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", length = 10)
    private PreferredLanguage preferredLanguage;

    @Column(name = "time_zone", length = 80)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_preference", length = 20)
    private UnitPreference unitPreference;

    @Column(name = "target_weight")
    private Double targetWeight;

    @Column(name = "weekly_weight_change_target_kg")
    private Double weeklyWeightChangeTargetKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 30)
    private GoalType goalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 30)
    private ActivityLevel activityLevel;
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_dietary_preference", length = 30)
    private DietaryPreference primaryDietaryPreference;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onboarding_draft_allergens",
            joinColumns = @JoinColumn(name = "draft_id"),
            foreignKey = @ForeignKey(name = "fk_onboarding_draft_allergens_draft")
    )
    @Column(name = "allergen", nullable = false, length = 60)
    @Enumerated(EnumType.STRING)
    private Set<RecipeAllergen> allergens = new LinkedHashSet<>();

    @Column(name = "allergen_selection_confirmed", nullable = false)
    private boolean allergenSelectionConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekly_workout_frequency", length = 30)
    private WeeklyWorkoutFrequency weeklyWorkoutFrequency;

    @Column(name = "fitness_preference_completed", nullable = false)
    private boolean fitnessPreferenceCompleted;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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

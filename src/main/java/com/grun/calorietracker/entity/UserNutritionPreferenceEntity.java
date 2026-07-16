package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.RecipeAllergen;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "user_nutrition_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_nutrition_preferences_user",
                columnNames = "user_id"))
@Data
public class UserNutritionPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_nutrition_preferences_user"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UserEntity user;

    @ElementCollection
    @CollectionTable(name = "user_nutrition_allergens",
            joinColumns = @JoinColumn(name = "preference_id"),
            foreignKey = @ForeignKey(name = "fk_user_nutrition_allergens_preference"))
    @Column(name = "allergen", nullable = false, length = 60)
    @Enumerated(EnumType.STRING)
    private Set<RecipeAllergen> allergens = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "user_excluded_foods",
            joinColumns = @JoinColumn(name = "preference_id"),
            foreignKey = @ForeignKey(name = "fk_user_excluded_foods_preference"))
    @OrderColumn(name = "sort_order")
    @Column(name = "food_name", nullable = false, length = 80)
    private List<String> excludedFoods = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_dietary_preferences",
            joinColumns = @JoinColumn(name = "preference_id"),
            foreignKey = @ForeignKey(name = "fk_user_dietary_preferences_preference"))
    @OrderColumn(name = "sort_order")
    @Column(name = "preference", nullable = false, length = 80)
    private List<String> dietaryPreferences = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version = 0L;

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

package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserEntity ownerUser;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 40)
    private String mealType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipeVisibility visibility = RecipeVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    private MarketRegion marketRegion;

    @Column(length = 12)
    private String language;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private ImageSource imageSource;

    @Enumerated(EnumType.STRING)
    private ImageStatus imageStatus;

    @Column(columnDefinition = "TEXT")
    private String imageReviewNote;

    private String imageReviewedBy;
    private LocalDateTime imageReviewedAt;

    private Double totalYieldGrams;
    private Double defaultServingGrams;
    private Integer servingCount;

    private Double snapshotCalories;
    private Double snapshotProtein;
    private Double snapshotCarbs;
    private Double snapshotFat;
    private Double snapshotFiber;
    private Double snapshotSugar;
    private Double snapshotSodium;

    private Boolean archived = false;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemOrder ASC, id ASC")
    private List<RecipeIngredientEntity> ingredients = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.visibility == null) {
            this.visibility = RecipeVisibility.PRIVATE;
        }
        if (this.archived == null) {
            this.archived = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

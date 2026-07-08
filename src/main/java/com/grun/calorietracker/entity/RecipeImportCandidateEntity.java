package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeImportCandidateStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recipe_import_candidates", uniqueConstraints = {
        @UniqueConstraint(name = "uq_recipe_import_candidate_source", columnNames = {"batch_id", "source_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeImportCandidateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, length = 160)
    private String batchId;

    @Column(name = "source_key", nullable = false, length = 220)
    private String sourceKey;

    @Column(length = 255)
    private String sourceTitle;

    @Column(length = 1000)
    private String sourceUrl;

    @Column(length = 1000)
    private String sourceRevisionUrl;

    @Column(length = 120)
    private String license;

    @Column(length = 80)
    private String recommendedImportStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RecipeImportCandidateStatus status = RecipeImportCandidateStatus.PENDING;

    @Column(nullable = false, length = 160)
    private String recipeName;

    @Column(length = 40)
    private String mealType;

    @Enumerated(EnumType.STRING)
    private MarketRegion marketRegion;

    @Column(length = 12)
    private String language;

    private Integer ingredientCount;
    private Integer unresolvedIngredientCount;

    @Column(columnDefinition = "TEXT")
    private String validationIssues;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String rawPayload;

    private Long createdRecipeId;
    private String reviewedBy;
    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String reviewNote;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = RecipeImportCandidateStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

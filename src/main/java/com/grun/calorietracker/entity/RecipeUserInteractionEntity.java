package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recipe_user_interactions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_recipe_user_interaction", columnNames = {"user_id", "recipe_id"})
})
@Data
@NoArgsConstructor
public class RecipeUserInteractionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private RecipeEntity recipe;

    @Column(nullable = false)
    private Boolean saved = false;

    @Column(nullable = false)
    private Boolean favorite = false;

    private Integer rating;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.saved == null) {
            this.saved = false;
        }
        if (this.favorite == null) {
            this.favorite = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

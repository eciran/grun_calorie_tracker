package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.PreferredLanguage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipe_translations", uniqueConstraints =
        @UniqueConstraint(name = "uk_recipe_translations_recipe_language", columnNames = {"recipe_id", "language"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeTranslationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private RecipeEntity recipe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PreferredLanguage language;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @OneToMany(mappedBy = "translation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC, id ASC")
    private List<RecipeTranslationStepEntity> cookingSteps = new ArrayList<>();
}

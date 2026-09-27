package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipe_translation_steps", uniqueConstraints =
        @UniqueConstraint(name = "uk_recipe_translation_steps_order", columnNames = {"translation_id", "step_order"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeTranslationStepEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "translation_id", nullable = false)
    private RecipeTranslationEntity translation;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(nullable = false, length = 1000)
    private String instruction;
}

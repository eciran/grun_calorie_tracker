package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "exercise_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String metCode;
    private Double caloriesPerMinute;
    private String description;
    private String iconUrl;

    private String primaryMuscleGroup;
    private String secondaryMuscleGroups;
    private String equipment;

    @Enumerated(EnumType.STRING)
    private ExerciseDifficulty difficulty;

    @Column(length = 2000)
    private String instructions;

    @Column(length = 1000)
    private String safetyNotes;

    private String thumbnailUrl;
    private String videoUrl;
    private String animationUrl;

    @Enumerated(EnumType.STRING)
    private ExerciseLogMeasurementType defaultMeasurementType;

    @Column(length = 255)
    private String allowedMeasurementTypes;
    private Boolean aiEligible = true;
    private Boolean active = true;
}

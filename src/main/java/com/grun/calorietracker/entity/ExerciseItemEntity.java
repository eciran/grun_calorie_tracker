package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExerciseTechniqueReviewStatus techniqueReviewStatus = ExerciseTechniqueReviewStatus.PENDING;

    @Column(length = 1000)
    private String techniqueReviewNote;
    private String techniqueReviewedBy;
    private java.time.LocalDateTime techniqueReviewedAt;

    @Column(length = 160)
    private String sourceName;
    @Column(length = 1000)
    private String sourceUrl;
    @Column(length = 120)
    private String licenseName;
    @Column(length = 1000)
    private String licenseUrl;
    private java.time.LocalDateTime sourceLastRefreshedAt;

    private String reviewAssignee;
    private java.time.LocalDateTime reviewDueAt;
    private java.time.LocalDateTime reviewClaimedAt;
}

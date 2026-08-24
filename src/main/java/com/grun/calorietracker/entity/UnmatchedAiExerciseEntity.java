package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "unmatched_ai_exercises")
@Data
public class UnmatchedAiExerciseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "normalized_name", nullable = false, length = 160)
    private String normalizedName;
    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;
    @Column(length = 12)
    private String language;
    @Column(length = 120)
    private String equipment;
    @Column(name = "target_muscle_group", length = 120)
    private String targetMuscleGroup;
    @Column(name = "occurrence_count", nullable = false)
    private Long occurrenceCount = 1L;
    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
    @Column(nullable = false, length = 24)
    private String status = "OPEN";
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_exercise_item_id")
    private ExerciseItemEntity resolvedExerciseItem;
}

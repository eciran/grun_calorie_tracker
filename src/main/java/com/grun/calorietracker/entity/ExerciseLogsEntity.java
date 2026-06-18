package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "exercise_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_exercise_logs_user_source_external",
                        columnNames = {"user_id", "source", "external_id"}
                )
        },
        indexes = {
                @Index(name = "idx_exercise_logs_user_source", columnList = "user_id, source")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseLogsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "exercise_item_id")
    private ExerciseItemEntity exerciseItem;

    private Integer durationMinutes;
    @Enumerated(EnumType.STRING)
    private ExerciseLogMeasurementType measurementType;
    private Integer setCount;
    private Integer reps;
    private Double weightKg;
    private Double distanceKm;
    private Double caloriesBurned;
    private LocalDateTime logDate;
    private String source;     // Data source (e.g., MANUAL, GOOGLE_FIT, APPLE_HEALTH)
    private String externalId;
    private String extraData;
}

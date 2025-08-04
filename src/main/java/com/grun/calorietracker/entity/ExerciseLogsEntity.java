package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "exercise_logs")
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
    private Double caloriesBurned;
    private LocalDateTime logDate;
    private String source;     // Data source (e.g., MANUAL, GOOGLE_FIT, APPLE_HEALTH)
    private String externalId;
    private String extraData;
}

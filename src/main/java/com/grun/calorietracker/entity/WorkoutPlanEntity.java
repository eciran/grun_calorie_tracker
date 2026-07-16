package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.WorkoutPlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "workout_plans")
@Data
public class WorkoutPlanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkoutPlanStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_ai_request_id")
    private AiRequestHistoryEntity sourceAiRequest;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String planPayload;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(length = 50)
    private String scheduleVersion;

    private LocalDateTime scheduleUpdatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

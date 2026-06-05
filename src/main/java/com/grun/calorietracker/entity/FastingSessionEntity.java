package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FastingSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fasting_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FastingSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "plan_id")
    private FastingPlanEntity plan;

    @Enumerated(EnumType.STRING)
    private FastingSessionStatus status;

    private LocalDate fastingDate;

    private LocalDateTime startedAt;

    private LocalDateTime targetEndAt;

    private LocalDateTime endedAt;

    private LocalDateTime reminderSentAt;

    private Integer targetMinutes;

    private Integer actualMinutes;

    private Boolean targetReached;

    @Column(length = 500)
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

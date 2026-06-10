package com.grun.calorietracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_achievements",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_achievements_user_code",
                columnNames = {"user_id", "achievement_code"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAchievementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "achievement_code", nullable = false, length = 80)
    private String achievementCode;

    @Column(nullable = false)
    private Integer progressValue = 0;

    @Column(nullable = false)
    private Integer targetValue = 1;

    @Column(nullable = false)
    private Boolean unlocked = false;

    private LocalDateTime unlockedAt;

    @Column(nullable = false)
    private LocalDateTime lastEvaluatedAt = LocalDateTime.now();
}

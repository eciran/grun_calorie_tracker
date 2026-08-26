package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "goal_target_acknowledgements")
@Data
public class GoalTargetAcknowledgementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "goal_id")
    private UserGoalEntity goal;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private UserEntity user;
    @Column(nullable = false, length = 1000)
    private String warningCodes;
    @Column(nullable = false, length = 64)
    private String policyVersion;
    @Column(nullable = false, length = 16)
    private String locale;
    @Column(nullable = false)
    private LocalDateTime acknowledgedAt;
}

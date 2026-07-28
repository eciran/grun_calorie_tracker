package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FastingHistoryCorrectionAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fasting_history_corrections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FastingHistoryCorrectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private FastingSessionEntity session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private UserEntity actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FastingHistoryCorrectionAction action;

    @Column(nullable = false, length = 64)
    private String correctionReason;

    private LocalDateTime oldStartedAt;
    private LocalDateTime oldEndedAt;
    private LocalDateTime newStartedAt;
    private LocalDateTime newEndedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "advanced_fasting_reminder_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedFastingReminderSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(nullable = false) private Boolean enabled = true;
    @Column(nullable = false) private Boolean preStartEnabled = true;
    @Column(nullable = false) private Boolean startEnabled = true;
    @Column(nullable = false) private Boolean nearingCompletionEnabled = true;
    @Column(nullable = false) private Boolean completionEnabled = true;
    @Column(nullable = false) private Boolean missedPlanEnabled = false;
    @Column(nullable = false) private Integer preStartMinutes = 30;
    @Column(nullable = false) private Integer nearingCompletionMinutes = 15;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    @PrePersist void create() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.enums.SleepQualityConfidence;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sleep_sessions", indexes = {
        @Index(name = "idx_sleep_sessions_user_date", columnList = "user_id, sleep_date"),
        @Index(name = "idx_sleep_sessions_user_start", columnList = "user_id, started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class SleepSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "sleep_date", nullable = false)
    private LocalDate sleepDate;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HealthProvider provider;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "quality_score", nullable = false)
    private Integer qualityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_confidence", nullable = false, length = 32)
    private SleepQualityConfidence qualityConfidence;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startedAt ASC, id ASC")
    private List<SleepStageEntity> stages = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

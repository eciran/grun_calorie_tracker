package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.SleepStageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sleep_stages", indexes = {
        @Index(name = "idx_sleep_stages_session_start", columnList = "session_id, started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class SleepStageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SleepSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 24)
    private SleepStageType stageType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;
}

package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.NotificationClassification;
import com.grun.calorietracker.enums.NotificationEventType;
import com.grun.calorietracker.enums.NotificationOccurrenceStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "notification_occurrences",
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_occurrence_source_event",
                columnNames = {"source", "source_event_id", "definition_key", "user_id"}))
@Data
public class NotificationOccurrenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private NotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationClassification classification;

    @Column(name = "definition_key", nullable = false, length = 80)
    private String definitionKey;

    @Column(name = "definition_version")
    private Long definitionVersion;

    @Column(nullable = false, length = 80)
    private String source;

    @Column(name = "source_event_id", nullable = false, length = 255)
    private String sourceEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationOccurrenceStatus status;

    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT")
    private String parametersJson = "{}";

    @Column(name = "reason_code", length = 80)
    private String reasonCode;

    @Column(name = "eligible_at", nullable = false)
    private Instant eligibleAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id")
    private NotificationEntity notification;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

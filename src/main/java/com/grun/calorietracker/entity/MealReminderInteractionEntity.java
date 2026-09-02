package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MealReminderInteractionType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "meal_reminder_interactions",
        uniqueConstraints = @UniqueConstraint(name = "uq_meal_reminder_interaction_event_key", columnNames = "event_key"))
@Data
public class MealReminderInteractionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "occurrence_id")
    private MealReminderOccurrenceEntity occurrence;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "notification_id")
    private NotificationEntity notification;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 30)
    private MealReminderInteractionType eventType;
    @Column(name = "event_key", nullable = false, length = 180)
    private String eventKey;
    @Column(name = "client_event_id", length = 100)
    private String clientEventId;
    @Column(length = 30)
    private String source;
    @Column(name = "related_date")
    private LocalDate relatedDate;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}

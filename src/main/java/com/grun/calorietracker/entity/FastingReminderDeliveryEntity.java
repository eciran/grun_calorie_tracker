package com.grun.calorietracker.entity;
import com.grun.calorietracker.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="fasting_reminder_deliveries",uniqueConstraints=@UniqueConstraint(name="uk_fasting_reminder_occurrence_key",columnNames="occurrence_key"))
@Data @NoArgsConstructor @AllArgsConstructor
public class FastingReminderDeliveryEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="occurrence_id",nullable=false) private FastingProgramOccurrenceEntity occurrence;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="notification_id") private NotificationEntity notification;
 @Column(name="occurrence_key",nullable=false,updatable=false,length=180) private String occurrenceKey;
 @Enumerated(EnumType.STRING) @Column(name="reminder_type",nullable=false,length=32) private AdvancedFastingReminderType reminderType;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private FastingReminderDeliveryStatus status;
 @Column(name="scheduled_for",nullable=false) private LocalDateTime scheduledFor;
 @Column(name="next_attempt_at") private LocalDateTime nextAttemptAt;
 @Column(name="attempt_count",nullable=false) private Integer attemptCount=0;
 @Column(name="last_error",length=500) private String lastError;
 @Column(name="delivered_at") private LocalDateTime deliveredAt;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @PrePersist void create(){var now=LocalDateTime.now();createdAt=now;updatedAt=now;}
 @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}

package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "owner_operational_alerts")
@Data
public class OwnerOperationalAlertEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "dedupe_key", nullable = false, unique = true, length = 240) private String dedupeKey;
    @Column(nullable = false, length = 60) private String category;
    @Column(nullable = false, length = 20) private String severity;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "title_en", nullable = false, length = 240) private String titleEn;
    @Column(name = "title_tr", nullable = false, length = 240) private String titleTr;
    @Column(name = "message_en", nullable = false, length = 2000) private String messageEn;
    @Column(name = "message_tr", nullable = false, length = 2000) private String messageTr;
    @Column(name = "target_path", nullable = false, length = 500) private String targetPath;
    @Column(name = "occurrence_count", nullable = false) private Long occurrenceCount;
    @Column(name = "first_occurred_at", nullable = false) private Instant firstOccurredAt;
    @Column(name = "last_occurred_at", nullable = false) private Instant lastOccurredAt;
    @Column(name = "attempt_count", nullable = false) private Integer attemptCount;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "last_error_type", length = 240) private String lastErrorType;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;
}

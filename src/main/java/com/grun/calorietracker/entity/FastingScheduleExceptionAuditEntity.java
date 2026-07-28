package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name = "fasting_schedule_exception_audits")
@Getter @Setter @NoArgsConstructor
public class FastingScheduleExceptionAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserEntity user;
    @Column(name = "exception_id") private Long exceptionId;
    @Column(name = "source_date", nullable = false) private LocalDate sourceDate;
    @Column(name = "action", nullable = false, length = 16) private String action;
    @Column(name = "old_value", length = 500) private String oldValue;
    @Column(name = "new_value", length = 500) private String newValue;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FastingScheduleExceptionType;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name = "fasting_schedule_exceptions", uniqueConstraints = @UniqueConstraint(name = "uk_fasting_exception_user_source_date", columnNames = {"user_id", "source_date"}))
@Getter @Setter @NoArgsConstructor
public class FastingScheduleExceptionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserEntity user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "program_id", nullable = false) private FastingProgramEntity program;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "program_version_id", nullable = false) private FastingProgramVersionEntity programVersion;
    @Column(name = "source_date", nullable = false) private LocalDate sourceDate;
    @Column(name = "target_date") private LocalDate targetDate;
    @Enumerated(EnumType.STRING) @Column(name = "exception_type", nullable = false, length = 32) private FastingScheduleExceptionType exceptionType;
    @Column(name = "moved_start_time") private LocalTime movedStartTime;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
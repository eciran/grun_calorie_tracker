package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "advanced_fasting_operations_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedFastingOperationsConfigEntity {
    @Id private Long id;
    @Column(nullable = false) private Boolean reminderEnabled;
    @Column(nullable = false) private Integer preStartMinutes;
    @Column(nullable = false) private Integer nearingCompletionMinutes;
    @Column(nullable = false) private Integer missedPlanMinutes;
    @Column(nullable = false) private Integer maxRetryAttempts;
    @Version @Column(nullable = false) private Long version;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create(){ LocalDateTime now=LocalDateTime.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void update(){ updatedAt=LocalDateTime.now(); }
}
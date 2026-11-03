package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_branded_duplicate_batch_jobs")
public class FoodBrandedDuplicateBatchJobEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32) private String status;
    @Column(length = 300) private String queryFilter;
    @Column(nullable = false) private boolean safeOnly = true;
    @Column(nullable = false) private int pageSize = 100;
    @Column(nullable = false) private int nextPage;
    @Column(nullable = false) private int nextItemIndex;
    @Column(nullable = false) private long totalGroups;
    @Column(nullable = false) private long eligibleGroups;
    @Column(nullable = false) private long processedGroups;
    @Column(nullable = false) private long appliedGroups;
    @Column(nullable = false) private long skippedGroups;
    @Column(nullable = false) private long failedGroups;
    @Column(nullable = false) private boolean pauseRequested;
    @Column(nullable = false, length = 255) private String requestedBy;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    @Column(length = 2000) private String lastError;
    @Version private Long version;

    @PrePersist void initialize() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}

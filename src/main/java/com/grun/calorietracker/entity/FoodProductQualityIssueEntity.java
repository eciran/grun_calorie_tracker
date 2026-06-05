package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodProductQualityIssue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "food_product_quality_issues")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodProductQualityIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    @Enumerated(EnumType.STRING)
    private FoodProductQualityIssue issueType;

    private String identifier;
    private String reason;
    private Boolean resolved;
    private LocalDateTime firstDetectedAt;
    private LocalDateTime lastDetectedAt;
    private LocalDateTime resolvedAt;
    private String resolvedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (firstDetectedAt == null) {
            firstDetectedAt = now;
        }
        if (lastDetectedAt == null) {
            lastDetectedAt = now;
        }
        if (resolved == null) {
            resolved = false;
        }
    }

    @PreUpdate
    void onUpdate() {
        if (resolved == null) {
            resolved = false;
        }
    }
}

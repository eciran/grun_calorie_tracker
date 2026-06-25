package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import jakarta.persistence.Column;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_quality_suggestions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQualitySuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductQualitySuggestionType suggestionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductQualitySuggestionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductQualitySuggestionStatus status;

    private Integer confidenceScore;

    @Column(length = 1000)
    private String currentValue;

    @Column(length = 1000)
    private String suggestedValue;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;
    private String reviewedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ProductQualitySuggestionStatus.OPEN;
        }
        if (source == null) {
            source = ProductQualitySuggestionSource.RULE_BASED;
        }
    }
}

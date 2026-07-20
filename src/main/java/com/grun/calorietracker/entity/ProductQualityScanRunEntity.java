package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ProductQualityScanStatus;
import com.grun.calorietracker.enums.ProductQualityScanTriggerType;
import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_quality_scan_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQualityScanRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductQualitySuggestionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductQualityScanTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductQualityScanStatus status;

    @Enumerated(EnumType.STRING)
    private MarketRegion marketRegion;

    private int requestedLimit;
    private int effectiveLimit;
    private boolean forceRescan;
    private int scannedProducts;
    private int createdSuggestions;
    private int skippedExistingSuggestions;
    private int skippedPreviouslyValidatedProducts;
    private int validatedProducts;
    private String triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Column(length = 1000)
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ProductQualityScanStatus.RUNNING;
        }
        if (source == null) {
            source = ProductQualitySuggestionSource.RULE_BASED;
        }
        if (triggerType == null) {
            triggerType = ProductQualityScanTriggerType.MANUAL;
        }
    }
}

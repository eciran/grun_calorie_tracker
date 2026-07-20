package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ProductQualityScanItemStatus;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_quality_scan_run_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQualityScanRunItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_run_id", nullable = false)
    private ProductQualityScanRunEntity scanRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProductQualityScanItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 80)
    private ProductQualitySuggestionType suggestionType;

    @Column(length = 100)
    private String fieldName;

    @Column(length = 1000)
    private String suggestedValue;

    @Column(length = 1000)
    private String reason;

    private Integer confidenceScore;

    @Column(length = 255)
    private String productNameSnapshot;

    @Column(length = 255)
    private String brandSnapshot;

    @Column(length = 1000)
    private String note;
}
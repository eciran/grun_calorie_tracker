package com.grun.calorietracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_quality_ai_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQualityAiSettingsEntity {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int maxProductsPerRun = 25;

    @Column(nullable = false)
    private int dailyProductLimit = 250;

    @Column(nullable = false)
    private int monthlyProductLimit = 2000;

    @Column(nullable = false)
    private boolean forceRescanAllowed = true;

    @Column(length = 1000)
    private String adminNote;

    private LocalDateTime updatedAt;
    private String updatedBy;

    @PrePersist
    @PreUpdate
    void touch() {
        if (id == null) {
            id = SINGLETON_ID;
        }
        updatedAt = LocalDateTime.now();
    }
}
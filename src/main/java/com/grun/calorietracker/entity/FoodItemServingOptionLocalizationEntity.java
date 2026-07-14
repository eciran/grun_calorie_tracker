package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.PreferredLanguage;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "food_item_serving_option_localizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemServingOptionLocalizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serving_option_id", nullable = false)
    private FoodItemServingOptionEntity servingOption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PreferredLanguage language;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 60)
    private String source;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
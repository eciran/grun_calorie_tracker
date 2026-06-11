package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ProductCorrectionStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_correction_suggestions")
@Data
public class ProductCorrectionSuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    private Double suggestedCalories;
    private Double suggestedProtein;
    private Double suggestedCarbs;
    private Double suggestedFat;

    @Column(length = 500)
    private String note;

    @Column(length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductCorrectionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

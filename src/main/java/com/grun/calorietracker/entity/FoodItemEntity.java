package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "food_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String barcode;
    private String normalizedBarcode;
    private String sourceKey;
    private String brand;
    private String imageUrl;
    private String externalImageUrl;
    private String displayImageUrl;
    private String allergens;
    private String nutriScore;

    @Enumerated(EnumType.STRING)
    private FoodDataSource dataSource;

    @Enumerated(EnumType.STRING)
    private FoodCatalogType catalogType;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    private ImageSource imageSource;

    @Enumerated(EnumType.STRING)
    private ImageStatus imageStatus;

    @Enumerated(EnumType.STRING)
    private MarketRegion marketRegion;

    @Enumerated(EnumType.STRING)
    private FoodPreparationState preparationState;

    private Long usageCount;
    private Integer qualityScore;
    private Integer confidenceScore;
    private Boolean autoApprovedForCatalog;
    private Integer reviewPriority;
    private LocalDateTime lastExternalSyncAt;
    private LocalDateTime lastReviewedAt;
    private String reviewedBy;

    private Double calories;
    private Double protein;
    private Double fat;
    private Double carbs;
    private Double fiber;
    private Double sugar;

    private Double sodium;
    private Double potassium;
    private Double cholesterol;
    private Double calcium;
    private Double iron;
    private Double magnesium;
    private Double zinc;

    @Column(name = "vitamin_a")
    private Double vitaminA;

    @Column(name = "vitamin_c")
    private Double vitaminC;

    @Column(name = "vitamin_d")
    private Double vitaminD;

    @Column(name = "vitamin_e")
    private Double vitaminE;

    @Column(name = "vitamin_b12")
    private Double vitaminB12;

    private Double saturatedFat;
    private Double transFat;
    private Double sugarAlcohol;

    private Double servingSizeGrams;
    private String servingUnit;

    private Boolean isCustom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserEntity createdByUser;

    @OneToMany(mappedBy = "foodItem", fetch = FetchType.LAZY)
    private Set<FoodItemSearchAliasEntity> searchAliases = new HashSet<>();
}

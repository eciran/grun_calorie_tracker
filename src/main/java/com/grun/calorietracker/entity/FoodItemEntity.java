package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
@Entity
@Table(name = "food_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "allergens")
    private String allergens;

    @Column(name = "nutri_score")
    private String nutriScore;

    @Column(name = "calories")
    private Double calories;

    @Column(name = "protein")
    private Double protein;

    @Column(name = "fat")
    private Double fat;

    @Column(name = "carbs")
    private Double carbs;

    @Column(name = "fiber")
    private Double fiber;

    @Column(name = "sugar")
    private Double sugar;

    @Column(name = "sodium")
    private Double sodium;

    @Column(name = "potassium")
    private Double potassium;

    @Column(name = "cholesterol")
    private Double cholesterol;

    @Column(name = "calcium")
    private Double calcium;

    @Column(name = "iron")
    private Double iron;

    @Column(name = "magnesium")
    private Double magnesium;

    @Column(name = "zinc")
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

    @Column(name = "saturated_fat")
    private Double saturatedFat;

    @Column(name = "trans_fat")
    private Double transFat;

    @Column(name = "sugar_alcohol")
    private Double sugarAlcohol;

    @Column(name = "is_custom")
    private Boolean isCustom;
}

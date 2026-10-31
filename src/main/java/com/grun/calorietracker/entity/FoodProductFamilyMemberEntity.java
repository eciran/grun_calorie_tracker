package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodPortionUnit;
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "food_product_family_members")
public class FoodProductFamilyMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private FoodProductFamilyEntity family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_item_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private FoodItemEntity foodItem;

    @Column(nullable = false)
    private BigDecimal packageQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FoodPortionUnit packageUnit;

    @Column(nullable = false, length = 120)
    private String packageLabel;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private boolean defaultVariant;

    @Column(nullable = false)
    private boolean active = true;
}

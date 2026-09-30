package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodCategoryMappingStatus;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.MarketRegion;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "food_category_resolution_rules")
public class FoodCategoryResolutionRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String ruleKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodDataSource dataSource;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MarketRegion marketRegion;

    @Column(nullable = false, length = 160)
    private String sourceReviewCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_category_id", nullable = false)
    private FoodCategoryEntity targetCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodCategoryMappingStatus status = FoodCategoryMappingStatus.REVIEW_REQUIRED;

    @Column(nullable = false)
    private Integer primaryPriority = 1000;

    private Integer confidenceScore;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "food_category_resolution_required_tags",
            joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "category_tag", nullable = false, length = 180)
    private Set<String> requiredTags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "food_category_resolution_excluded_tags",
            joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "category_tag", nullable = false, length = 180)
    private Set<String> excludedTags = new LinkedHashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @PrePersist
    void createTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}

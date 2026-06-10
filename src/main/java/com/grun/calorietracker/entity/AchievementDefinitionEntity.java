package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AchievementCategory;
import com.grun.calorietracker.enums.AchievementTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "achievement_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AchievementDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 80)
    private String metricKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AchievementCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AchievementTier tier;

    @Column(nullable = false)
    private Integer targetValue;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

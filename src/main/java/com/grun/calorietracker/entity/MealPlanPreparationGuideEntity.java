package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AiPreparationGuideStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "meal_plan_preparation_guides",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_preparation_guide_item_version",
                        columnNames = {"meal_plan_item_id", "version"}),
                @UniqueConstraint(name = "uk_preparation_guide_ai_request",
                        columnNames = "source_ai_request_id")
        })
@Data
public class MealPlanPreparationGuideEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_plan_item_id", nullable = false)
    private MealPlanItemEntity mealPlanItem;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_ai_request_id", nullable = false)
    private AiRequestHistoryEntity sourceAiRequest;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "schema_version", nullable = false, length = 50)
    private String schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiPreparationGuideStatus status;

    @Column(name = "guide_payload", nullable = false, columnDefinition = "TEXT")
    private String guidePayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

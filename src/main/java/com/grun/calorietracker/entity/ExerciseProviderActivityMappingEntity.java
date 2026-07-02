package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.HealthProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "exercise_provider_activity_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_exercise_provider_activity_mapping",
                        columnNames = {"provider", "normalized_provider_activity_type"}
                )
        },
        indexes = {
                @Index(name = "idx_exercise_provider_activity_mapping_provider", columnList = "provider, active")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseProviderActivityMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private HealthProvider provider;

    private String providerActivityType;

    private String normalizedProviderActivityType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_item_id")
    private ExerciseItemEntity exerciseItem;

    private Boolean active = true;
}

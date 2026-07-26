package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.HealthProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "body_measurements", indexes = {
        @Index(name = "idx_body_measurements_user_recorded", columnList = "user_id, recorded_at")
})
@Getter
@Setter
@NoArgsConstructor
public class BodyMeasurementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "body_fat_percentage")
    private Double bodyFatPercentage;

    @Column(name = "waist_cm")
    private Double waistCm;

    @Column(name = "chest_cm")
    private Double chestCm;

    @Column(name = "hip_cm")
    private Double hipCm;

    @Column(name = "upper_arm_cm")
    private Double upperArmCm;

    @Column(name = "thigh_cm")
    private Double thighCm;

    @Column(name = "neck_cm")
    private Double neckCm;

    @Column(name = "shoulder_cm")
    private Double shoulderCm;

    @Column(name = "forearm_cm")
    private Double forearmCm;

    @Column(name = "calf_cm")
    private Double calfCm;

    @Column(name = "left_upper_arm_cm")
    private Double leftUpperArmCm;

    @Column(name = "right_upper_arm_cm")
    private Double rightUpperArmCm;

    @Column(name = "left_thigh_cm")
    private Double leftThighCm;

    @Column(name = "right_thigh_cm")
    private Double rightThighCm;

    @Column(name = "left_calf_cm")
    private Double leftCalfCm;

    @Column(name = "right_calf_cm")
    private Double rightCalfCm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HealthProvider provider;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

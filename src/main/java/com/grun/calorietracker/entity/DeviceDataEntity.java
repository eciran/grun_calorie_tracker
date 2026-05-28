package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.HealthProvider;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private Integer steps;

    private Integer heartRate;

    private Double sleepHours;

    private Double caloriesBurned;

    private Double distanceMeters;

    private LocalDateTime recordedAt;

    @Enumerated(EnumType.STRING)
    private HealthProvider provider;

    private String externalId;

    private String source; // Apple Watch, Google Fit, vs.
}

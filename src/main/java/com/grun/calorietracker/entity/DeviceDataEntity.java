package com.grun.calorietracker.entity;

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

    private LocalDateTime recordedAt;

    private String source; // Apple Watch, Google Fit, vs.
}

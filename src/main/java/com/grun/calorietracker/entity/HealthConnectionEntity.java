package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.HealthConnectionStatus;
import com.grun.calorietracker.enums.HealthProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "health_connections",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_health_connections_user_provider", columnNames = {"user_id", "provider"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthConnectionStatus status;

    private LocalDateTime connectedAt;

    private LocalDateTime disconnectedAt;

    private LocalDateTime lastSyncAt;

    private String providerUserId;

    private String deviceModel;

    private String appVersion;
}

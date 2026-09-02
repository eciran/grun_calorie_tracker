package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "notification_platform_policy")
@Data
public class NotificationPlatformPolicyEntity {
    @Id
    private Long id;
    @Version
    @Column(nullable = false)
    private Long version = 0L;
    @Column(name = "requested_delivery_enabled", nullable = false)
    private boolean requestedDeliveryEnabled;
    @Column(name = "emergency_stopped", nullable = false)
    private boolean emergencyStopped = true;
    @Column(name = "stop_reason", length = 500)
    private String stopReason;
    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

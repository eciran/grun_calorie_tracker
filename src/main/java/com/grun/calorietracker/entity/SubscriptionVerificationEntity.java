package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Data
@Entity
@Table(name = "subscription_verifications")
public class SubscriptionVerificationEntity {
    @Id private Long userId;
    @Column(nullable = false, length = 255) private String productId;
    @Column(nullable = false, length = 100) private String attemptId;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false) private int attempts;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private String allocationReference;
    @Column(nullable = false) private Instant updatedAt;
}

package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.PushDeliveryStatus;
import com.grun.calorietracker.enums.PushProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "push_delivery_logs")
@Data
public class PushDeliveryLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id")
    private NotificationEntity notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "push_token_id")
    private UserPushTokenEntity pushToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushDeliveryStatus status;

    @Column(name = "provider_message_id", length = 512)
    private String providerMessageId;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

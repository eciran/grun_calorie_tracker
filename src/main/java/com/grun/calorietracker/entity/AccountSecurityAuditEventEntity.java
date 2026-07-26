package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AccountSecurityEventType;
import com.grun.calorietracker.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_security_audit_events")
@Data
public class AccountSecurityAuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private AccountSecurityEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AuthProvider provider;

    @Column(name = "result_code", length = 64)
    private String resultCode;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

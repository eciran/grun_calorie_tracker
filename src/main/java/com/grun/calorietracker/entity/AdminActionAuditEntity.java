package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_action_audits")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminActionAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String adminEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private AdminAuditActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private AdminAuditTargetType targetType;

    @Column(nullable = false, length = 255)
    private String targetKey;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 128)
    private String correlationId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

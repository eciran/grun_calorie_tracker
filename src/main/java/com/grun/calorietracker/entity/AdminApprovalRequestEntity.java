package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.*;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "admin_approval_requests")
@Data @NoArgsConstructor
public class AdminApprovalRequestEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name="action_type", nullable=false, length=64) private AdminApprovalActionType actionType;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private AdminApprovalStatus status;
    @Column(name="maker_email", nullable=false, length=320) private String makerEmail;
    @Column(name="checker_email", length=320) private String checkerEmail;
    @Column(name="target_key", nullable=false, length=128) private String targetKey;
    @Column(name="payload_json", nullable=false, columnDefinition="TEXT") private String payloadJson;
    @Column(name="request_reason", nullable=false, length=500) private String requestReason;
    @Column(name="decision_reason", length=500) private String decisionReason;
    @Column(name="correlation_id", length=128) private String correlationId;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="expires_at", nullable=false) private Instant expiresAt;
    @Column(name="decided_at") private Instant decidedAt;
}
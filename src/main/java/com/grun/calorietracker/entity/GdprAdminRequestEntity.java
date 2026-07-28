package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.GdprRequestStatus;
import com.grun.calorietracker.enums.GdprRequestType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "gdpr_admin_requests")
@Data
@NoArgsConstructor
public class GdprAdminRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 16)
    private GdprRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GdprRequestStatus status;

    @Column(name = "subject_reference", nullable = false, length = 64)
    private String subjectReference;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "assigned_to", length = 320)
    private String assignedTo;

    @Column(name = "result_code", length = 64)
    private String resultCode;

    @Column(name = "evidence_reference", length = 128)
    private String evidenceReference;

    @Column(name = "failure_summary", length = 300)
    private String failureSummary;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Version
    private Long version;
}
